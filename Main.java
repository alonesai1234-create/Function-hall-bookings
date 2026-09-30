package com.functionhall;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
  static final int PORT = 8080;
  static final String DB_URL = env("DB_URL", "jdbc:mysql://localhost:3306/function_hall_db?createDatabaseIfNotExist=true&serverTimezone=UTC");
  static final String DB_USER = env("DB_USER", "root");
  static final String DB_PASSWORD = env("DB_PASSWORD", "");

  static Connection getConnection() throws SQLException { return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD); }
  static String env(String k, String d) { String v = System.getenv(k); return v == null || v.isBlank() ? d : v; }

  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    initDatabase();
    HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
    server.createContext("/api/health", Main::health);
    server.createContext("/api/halls", Main::halls);
    server.createContext("/api/foods", Main::foods);
    server.createContext("/api/bookings", Main::bookings);
    server.setExecutor(null);
    server.start();
    System.out.println("Java JDBC API running at http://localhost:" + PORT);
  }

  static void initDatabase() throws SQLException {
    try (Connection c = getConnection(); Statement s = c.createStatement()) {
      s.executeUpdate("CREATE TABLE IF NOT EXISTS halls (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(120) NOT NULL, category VARCHAR(30) NOT NULL, category_label VARCHAR(60) NOT NULL, description VARCHAR(255) NOT NULL, capacity INT NOT NULL, price DECIMAL(12,2) NOT NULL, icon VARCHAR(10) NOT NULL)");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS foods (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(120) NOT NULL, food_type VARCHAR(10) NOT NULL, description VARCHAR(255) NOT NULL, price DECIMAL(10,2) NOT NULL, icon VARCHAR(10) NOT NULL)");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS bookings (id BIGINT PRIMARY KEY AUTO_INCREMENT, customer_name VARCHAR(120) NOT NULL, phone VARCHAR(20) NOT NULL, event_date DATE NOT NULL, event_type VARCHAR(60) NOT NULL, notes VARCHAR(500), payment_method VARCHAR(20) NOT NULL, hall_id INT NOT NULL, total DECIMAL(12,2) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (hall_id) REFERENCES halls(id))");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS booking_items (id BIGINT PRIMARY KEY AUTO_INCREMENT, booking_id BIGINT NOT NULL, food_id INT NOT NULL, quantity INT NOT NULL, price_each DECIMAL(10,2) NOT NULL, FOREIGN KEY (booking_id) REFERENCES bookings(id), FOREIGN KEY (food_id) REFERENCES foods(id))");
    }
    seedHalls(); seedFoods();
  }

  static void seedHalls() throws SQLException {
    try (Connection c = getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM halls")) {
      r.next(); if (r.getInt(1) > 0) return;
    }
    String[][] data = {
      {"Sunrise Mini Hall","plain","Plain","Simple hall for birthday and small family functions.","80","18000","🏛️"},
      {"Emerald Celebration Hall","chairs","With Chairs","Hall with neat guest chairs and basic stage.","150","28000","🪑"},
      {"Royal Banquet Hall","chairs","With Chairs","Spacious seating, stage area and guest welcome zone.","250","35000","👑"},
      {"Rosewood Decor Hall","decor","Chairs + Decoration","Chairs, stage, floral backdrop and warm lighting.","250","45000","🌸"},
      {"Golden Wedding Hall","decor","Chairs + Decoration","Wedding-ready decor, mandapam stage and premium lights.","400","65000","💍"},
      {"Grand Lotus Palace","decor","Chairs + Decoration","Premium full-service hall package for large events.","700","95000","✨"}
    };
    String sql = "INSERT INTO halls(name,category,category_label,description,capacity,price,icon) VALUES(?,?,?,?,?,?,?)";
    try (Connection c = getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
      for (String[] d : data) { p.setString(1,d[0]);p.setString(2,d[1]);p.setString(3,d[2]);p.setString(4,d[3]);p.setInt(5,Integer.parseInt(d[4]));p.setBigDecimal(6,new java.math.BigDecimal(d[5]));p.setString(7,d[6]);p.addBatch(); }
      p.executeBatch();
    }
  }

  static void seedFoods() throws SQLException {
    try (Connection c = getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM foods")) { r.next(); if (r.getInt(1) > 0) return; }
    String[][] veg = {
      {"Paneer Tikka","Starter","Spiced grilled paneer cubes.","180","🧀"},{"Veg Manchurian","Starter","Crispy vegetable balls in Indo-Chinese sauce.","160","🥢"},{"Gobi 65","Starter","Crispy cauliflower with South Indian spices.","150","🥦"},{"Baby Corn Pepper","Starter","Pepper-coated baby corn fry.","170","🌽"},{"Hara Bhara Kebab","Starter","Green vegetable and spinach kebab.","155","🥬"},{"Veg Spring Roll","Starter","Crunchy rolls filled with vegetables.","145","🥟"},{"Paneer Butter Masala","Main Course","Creamy tomato gravy with paneer.","220","🍛"},{"Kaju Masala","Main Course","Cashew rich curry with aromatic spices.","240","🥜"},{"Mixed Veg Curry","Main Course","Seasonal vegetables in mild gravy.","190","🥘"},{"Dal Tadka","Main Course","Yellow lentils tempered with ghee and spices.","150","🍲"},{"Veg Biryani","Rice","Aromatic basmati rice with vegetables.","190","🍚"},{"Paneer Biryani","Rice","Fragrant rice with paneer and herbs.","230","🥘"},{"Jeera Rice","Rice","Basmati rice tempered with cumin.","120","🍚"},{"Butter Naan","Bread","Soft naan with butter glaze.","55","🫓"},{"Garlic Naan","Bread","Tandoor naan with garlic and herbs.","65","🫓"},{"Rumali Roti","Bread","Thin handkerchief-style roti.","45","🫓"},{"Veg Fried Rice","Rice","Wok-tossed rice with vegetables.","170","🍜"},{"Gulab Jamun","Dessert","Soft milk dumplings in sugar syrup.","90","🍡"},{"Ice Cream","Dessert","Two-scoop assorted ice cream.","110","🍨"},{"Fruit Salad","Dessert","Fresh seasonal fruit mix.","120","🍓"},{"Gajar Halwa","Dessert","Warm carrot pudding with nuts.","130","🥕"},{"Lemon Juice","Drink","Fresh lemon cooler.","60","🍋"}
    };
    String[][] nonveg = {
      {"Chicken 65","Starter","Crispy spicy chicken bites.","220","🍗"},{"Chicken Tikka","Starter","Tandoori-style marinated chicken.","240","🍢"},{"Chicken Seekh Kebab","Starter","Minced chicken kebab with herbs.","250","🍢"},{"Mutton Shikampur","Starter","Tender mutton patties with spices.","300","🥩"},{"Fish Fry","Starter","Crispy marinated fish fillet.","260","🐟"},{"Apollo Fish","Starter","Boneless fish in spicy restaurant-style sauce.","280","🐟"},{"Chicken Curry","Main Course","Classic homestyle chicken curry.","250","🍛"},{"Butter Chicken","Main Course","Creamy tomato chicken curry.","280","🍗"},{"Mutton Curry","Main Course","Slow-cooked mutton in rich masala.","330","🥘"},{"Kadai Chicken","Main Course","Chicken tossed with capsicum and kadai spices.","270","🍲"},{"Chicken Biryani","Rice","Aromatic basmati chicken biryani.","260","🍚"},{"Mutton Biryani","Rice","Rich slow-cooked mutton biryani.","320","🍚"},{"Fish Biryani","Rice","Fragrant rice with seasoned fish pieces.","290","🍚"},{"Chicken Fried Rice","Rice","Wok-fried rice with chicken.","230","🍜"},{"Egg Fried Rice","Rice","Fried rice with fluffy egg.","190","🍳"},{"Butter Naan","Bread","Soft naan with butter glaze.","55","🫓"},{"Egg Curry","Main Course","Boiled eggs in onion-tomato gravy.","190","🥚"},{"Chicken Handi","Main Course","Slow-cooked chicken in creamy handi gravy.","290","🍲"},{"Mutton Rogan Josh","Main Course","Aromatic Kashmiri-style mutton curry.","350","🥩"},{"Prawn Masala","Main Course","Prawns cooked in spicy masala.","320","🦐"},{"Double Ka Meetha","Dessert","Hyderabadi bread pudding with nuts.","120","🍞"},{"Payasam","Dessert","Creamy milk dessert with vermicelli and nuts.","100","🥣"}
    };
    insertFoodRows(veg, "veg"); insertFoodRows(nonveg, "nonveg");
  }

  static void insertFoodRows(String[][] rows, String type) throws SQLException {
    String sql = "INSERT INTO foods(name,food_type,description,price,icon) VALUES(?,?,?,?,?)";
    try (Connection c = getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
      for(String[] r: rows){p.setString(1,r[0]);p.setString(2,type);p.setString(3,r[2]);p.setBigDecimal(4,new java.math.BigDecimal(r[3]));p.setString(5,r[4]);p.addBatch();}
      p.executeBatch();
    }
  }

  static void health(HttpExchange e) throws IOException { json(e,200,"{\"status\":\"ok\",\"service\":\"Java JDBC backend\"}"); }

  static void halls(HttpExchange e) throws IOException {
    if(!"GET".equalsIgnoreCase(e.getRequestMethod())){json(e,405,"{\"message\":\"Method not allowed\"}");return;}
    try(Connection c=getConnection(); PreparedStatement p=c.prepareStatement("SELECT id,name,category,category_label,description,capacity,price,icon FROM halls ORDER BY price"); ResultSet r=p.executeQuery()){
      List<String> out=new ArrayList<>(); while(r.next()) out.add(String.format(Locale.US,"{\"id\":%d,\"name\":\"%s\",\"category\":\"%s\",\"categoryLabel\":\"%s\",\"description\":\"%s\",\"capacity\":%d,\"price\":%s,\"icon\":\"%s\"}",r.getInt(1),esc(r.getString(2)),esc(r.getString(3)),esc(r.getString(4)),esc(r.getString(5)),r.getInt(6),r.getBigDecimal(7).toPlainString(),esc(r.getString(8))));
      json(e,200,"["+String.join(",",out)+"]");
    }catch(Exception ex){json(e,500,error(ex));}
  }

  static void foods(HttpExchange e) throws IOException {
    if(!"GET".equalsIgnoreCase(e.getRequestMethod())){json(e,405,"{\"message\":\"Method not allowed\"}");return;}
    String query=e.getRequestURI().getRawQuery(); String type="veg"; if(query!=null) for(String p:query.split("&")){String[] a=p.split("=",2);if(a.length==2 && a[0].equals("type")) type=URLDecoder.decode(a[1],StandardCharsets.UTF_8);}
    try(Connection c=getConnection(); PreparedStatement p=c.prepareStatement("SELECT id,name,food_type,description,price,icon FROM foods WHERE food_type=? ORDER BY id")){p.setString(1,type);try(ResultSet r=p.executeQuery()){List<String> out=new ArrayList<>();while(r.next())out.add(String.format(Locale.US,"{\"id\":%d,\"name\":\"%s\",\"type\":\"%s\",\"description\":\"%s\",\"price\":%s,\"icon\":\"%s\"}",r.getInt(1),esc(r.getString(2)),esc(r.getString(3)),esc(r.getString(4)),r.getBigDecimal(5).toPlainString(),esc(r.getString(6))));json(e,200,"["+String.join(",",out)+"]");}}
    catch(Exception ex){json(e,500,error(ex));}
  }

  static void bookings(HttpExchange e) throws IOException {
    if(!"POST".equalsIgnoreCase(e.getRequestMethod())){json(e,405,"{\"message\":\"POST only\"}");return;}
    Map<String,String> f=parseForm(readBody(e));
    try{
      String name=req(f,"name"), phone=req(f,"phone"), date=req(f,"eventDate"), eventType=req(f,"eventType"), payment=req(f,"paymentMethod"), hallId=req(f,"hallId"), items=f.getOrDefault("items","");
      if(!phone.matches("\\d{10}")) throw new IllegalArgumentException("Phone number must contain exactly 10 digits");
      LocalDate.parse(date); int hid=Integer.parseInt(hallId);
      try(Connection c=getConnection()){
        c.setAutoCommit(false);
        BigDecimal serverHallPrice; try(PreparedStatement hp=c.prepareStatement("SELECT price FROM halls WHERE id=?")){hp.setInt(1,hid);try(ResultSet r=hp.executeQuery()){if(!r.next())throw new IllegalArgumentException("Hall not found");serverHallPrice=r.getBigDecimal(1);}}
        List<int[]> itemRows=new ArrayList<>(); BigDecimal foodTotal=BigDecimal.ZERO;
        if(!items.isBlank()) for(String part:items.split("\\|")){String[] a=part.split(":",2);if(a.length!=2)continue;int fid=Integer.parseInt(a[0]), qty=Integer.parseInt(a[1]);if(qty<=0)continue;try(PreparedStatement fp=c.prepareStatement("SELECT price FROM foods WHERE id=?")){fp.setInt(1,fid);try(ResultSet r=fp.executeQuery()){if(!r.next())throw new IllegalArgumentException("Food item not found: "+fid);BigDecimal price=r.getBigDecimal(1);foodTotal=foodTotal.add(price.multiply(BigDecimal.valueOf(qty)));itemRows.add(new int[]{fid,qty});}}}
        BigDecimal total=serverHallPrice.add(foodTotal);
        String ins="INSERT INTO bookings(customer_name,phone,event_date,event_type,notes,payment_method,hall_id,total) VALUES(?,?,?,?,?,?,?,?)";
        long id; try(PreparedStatement p=c.prepareStatement(ins,Statement.RETURN_GENERATED_KEYS)){p.setString(1,name);p.setString(2,phone);p.setDate(3,java.sql.Date.valueOf(date));p.setString(4,eventType);p.setString(5,f.getOrDefault("notes",""));p.setString(6,payment);p.setInt(7,hid);p.setBigDecimal(8,total);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();id=k.getLong(1);}}
        String ii="INSERT INTO booking_items(booking_id,food_id,quantity,price_each) VALUES(?,?,?,?)";try(PreparedStatement p=c.prepareStatement(ii)){for(int[] row:itemRows){try(PreparedStatement fp=c.prepareStatement("SELECT price FROM foods WHERE id=?")){fp.setInt(1,row[0]);try(ResultSet r=fp.executeQuery()){r.next();p.setLong(1,id);p.setInt(2,row[0]);p.setInt(3,row[1]);p.setBigDecimal(4,r.getBigDecimal(1));p.addBatch();}}}p.executeBatch();}
        c.commit(); json(e,201,"{\"success\":true,\"bookingId\":"+id+",\"message\":\"Booking confirmed successfully\"}");
      }
    }catch(Exception ex){json(e,400,error(ex));}
  }

  static String req(Map<String,String> f,String k){String v=f.get(k);if(v==null||v.isBlank())throw new IllegalArgumentException(k+" is required");return v;}
  static String readBody(HttpExchange e)throws IOException{return new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);}
  static Map<String,String> parseForm(String body){Map<String,String> m=new HashMap<>();for(String pair:body.split("&")){if(pair.isBlank())continue;String[] a=pair.split("=",2);String k=URLDecoder.decode(a[0],StandardCharsets.UTF_8);String v=a.length>1?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"";m.put(k,v);}return m;}
  static String esc(String s){if(s==null)return"";return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","").replace("\t","\\t");}
  static String error(Exception ex){return "{\"success\":false,\"message\":\""+esc(ex.getMessage()==null?"Server error":ex.getMessage())+"\"}";}
  static void json(HttpExchange e,int code,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.getResponseHeaders().set("Access-Control-Allow-Origin","*");e.sendResponseHeaders(code,b.length);try(OutputStream os=e.getResponseBody()){os.write(b);}}
}
