const state = {
  halls: [],
  foods: [],
  activeFoodType: 'veg',
  hallFilter: 'all',
  cart: JSON.parse(localStorage.getItem('fh_cart') || '{}'),
  selectedHall: JSON.parse(localStorage.getItem('fh_hall') || 'null')
};

const money = n => '₹' + Number(n || 0).toLocaleString('en-IN');
const saveCart = () => localStorage.setItem('fh_cart', JSON.stringify(state.cart));
const saveHall = () => localStorage.setItem('fh_hall', JSON.stringify(state.selectedHall));
const cartQty = () => Object.values(state.cart).reduce((a, b) => a + b, 0);

function showToast(message) {
  const el = document.getElementById('toast');
  if (!el) return;
  el.textContent = message;
  el.classList.add('show');
  setTimeout(() => el.classList.remove('show'), 1800);
}

function updateNavCount() {
  const count = cartQty();
  document.querySelectorAll('#navCartCount,#toolbarCartCount').forEach(el => el.textContent = count);
}

async function getJSON(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error('API error ' + response.status);
  return response.json();
}

async function loadHalls() {
  try {
    state.halls = await getJSON('/api/halls');
    renderHalls();
  } catch (e) {
    document.getElementById('hallGrid').innerHTML = '<div class="empty-state">Cannot connect to Java backend. Start Java first, then refresh this page.</div>';
  }
}

function renderHalls() {
  const grid = document.getElementById('hallGrid');
  if (!grid) return;
  const items = state.halls.filter(h => state.hallFilter === 'all' || h.category === state.hallFilter);
  grid.innerHTML = items.map(h => `
    <article class="hall-card" id="${h.category}">
      <div class="hall-art">${h.icon}</div>
      <div class="hall-body">
        <div class="meta-row"><span class="tag">${h.capacity} guests</span><span>${h.categoryLabel}</span></div>
        <h3>${h.name}</h3><p>${h.description}</p>
        <div class="price-row-small"><span class="price">${money(h.price)}</span>
          <button class="btn btn-primary hall-select" data-id="${h.id}">${state.selectedHall?.id === h.id ? 'Selected ✓' : 'Choose Hall'}</button>
        </div>
      </div>
    </article>`).join('');
  grid.querySelectorAll('.hall-select').forEach(btn => btn.addEventListener('click', () => {
    state.selectedHall = state.halls.find(h => h.id === Number(btn.dataset.id));
    saveHall(); renderHalls(); showToast('Hall selected');
  }));
}

async function loadFoods() {
  try {
    state.foods = await getJSON('/api/foods?type=' + state.activeFoodType);
    renderFoods();
  } catch (e) {
    document.getElementById('foodGrid').innerHTML = '<div class="empty-state">Cannot connect to Java backend. Start Java first, then refresh this page.</div>';
  }
}

function renderFoods() {
  const grid = document.getElementById('foodGrid');
  if (!grid) return;
  grid.innerHTML = state.foods.map(f => {
    const qty = state.cart[f.id] || 0;
    return `<article class="food-card" data-food-id="${f.id}">
      <div class="food-art">${f.icon}</div>
      <div class="food-body"><h3>${f.name}</h3><p>${f.description}</p>
        <div class="food-footer"><strong>${money(f.price)}</strong>
        ${qty ? `<div class="qty-stepper"><button class="minus" data-id="${f.id}">−</button><span>${qty}</span><button class="plus" data-id="${f.id}">+</button></div>` : `<button class="add-btn add-food" data-id="${f.id}">Add +</button>`}</div>
      </div></article>`;
  }).join('');
  grid.querySelectorAll('.add-food').forEach(btn => btn.addEventListener('click', () => changeQty(Number(btn.dataset.id), 1, true)));
  grid.querySelectorAll('.plus').forEach(btn => btn.addEventListener('click', () => changeQty(Number(btn.dataset.id), 1, true)));
  grid.querySelectorAll('.minus').forEach(btn => btn.addEventListener('click', () => changeQty(Number(btn.dataset.id), -1, false)));
}

function changeQty(id, delta, isAdd) {
  const next = Math.max(0, (state.cart[id] || 0) + delta);
  const card = document.querySelector(`[data-food-id="${id}"]`);
  if (card && !isAdd) { card.classList.remove('removing'); void card.offsetWidth; card.classList.add('removing'); }
  if (next === 0) delete state.cart[id]; else state.cart[id] = next;
  saveCart(); updateNavCount(); renderFoods();
  showToast(isAdd ? 'Item added to cart ✨' : 'Item removed from cart');
}

function wireFoodFilters() {
  document.querySelectorAll('.food-filter').forEach(btn => btn.addEventListener('click', () => {
    document.querySelectorAll('.food-filter').forEach(b => b.classList.remove('active'));
    btn.classList.add('active'); state.activeFoodType = btn.dataset.foodFilter; loadFoods();
  }));
}

function wireHallFilters() {
  document.querySelectorAll('[data-hall-filter]').forEach(btn => btn.addEventListener('click', () => {
    document.querySelectorAll('[data-hall-filter]').forEach(b => b.classList.remove('active'));
    btn.classList.add('active'); state.hallFilter = btn.dataset.hallFilter; renderHalls();
  }));
}

async function loadAllFoodForBooking() {
  const [veg, nonveg] = await Promise.all([getJSON('/api/foods?type=veg'), getJSON('/api/foods?type=nonveg')]);
  state.foods = [...veg, ...nonveg];
}

function renderBooking() {
  const hallBox = document.getElementById('selectedHall');
  const cartList = document.getElementById('cartList');
  if (!hallBox || !cartList) return;
  if (state.selectedHall) hallBox.innerHTML = `<strong>${state.selectedHall.name}</strong><small>${state.selectedHall.capacity} guests · ${state.selectedHall.categoryLabel} · ${money(state.selectedHall.price)}</small>`;
  else hallBox.innerHTML = `<strong>No hall selected</strong><small><a class="text-link" href="halls.html">Choose a hall first →</a></small>`;

  const selectedItems = Object.entries(state.cart).map(([id, qty]) => ({food: state.foods.find(f => f.id === Number(id)), qty})).filter(x => x.food);
  document.getElementById('emptyCartMessage').classList.toggle('hidden', selectedItems.length > 0);
  cartList.innerHTML = selectedItems.map(({food, qty}) => `<div class="cart-item"><div><div class="cart-item-title">${food.icon} ${food.name}</div><div class="cart-item-sub">${qty} × ${money(food.price)}</div></div><strong>${money(qty * food.price)}</strong></div>`).join('');
  const foodTotal = selectedItems.reduce((sum, x) => sum + x.qty * x.food.price, 0);
  const hallTotal = state.selectedHall?.price || 0;
  document.getElementById('hallTotal').textContent = money(hallTotal);
  document.getElementById('foodTotal').textContent = money(foodTotal);
  document.getElementById('grandTotal').textContent = money(hallTotal + foodTotal);
  return { selectedItems, foodTotal, hallTotal, total: hallTotal + foodTotal };
}

async function submitBooking(event) {
  event.preventDefault();
  if (!state.selectedHall) { showToast('Please select a hall first'); return; }
  const totals = renderBooking();
  const name = document.getElementById('customerName').value.trim();
  const phone = document.getElementById('phone').value.trim();
  const date = document.getElementById('eventDate').value;
  const eventType = document.getElementById('eventType').value;
  const notes = document.getElementById('notes').value.trim();
  const paymentMethod = document.querySelector('input[name="paymentMethod"]:checked').value;
  const items = totals.selectedItems.map(x => `${x.food.id}:${x.qty}`).join('|');
  const params = new URLSearchParams({name, phone, eventDate: date, eventType, notes, paymentMethod, hallId: String(state.selectedHall.id), items, total: String(totals.total)});
  const btn = document.getElementById('confirmBtn'); btn.disabled = true; btn.textContent = 'Processing booking…';
  try {
    const response = await fetch('/api/bookings', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:params});
    const result = await response.json();
    if (!response.ok || !result.success) throw new Error(result.message || 'Booking failed');
    localStorage.setItem('fh_last_booking', JSON.stringify({...result, name, phone, date, eventType, paymentMethod, hallName: state.selectedHall.name, total: totals.total}));
    localStorage.removeItem('fh_cart'); localStorage.removeItem('fh_hall');
    window.location.href = 'success.html';
  } catch (e) {
    showToast(e.message || 'Booking failed'); btn.disabled = false; btn.textContent = 'Confirm Booking 🎉';
  }
}

function renderSuccess() {
  const box = document.getElementById('successDetails');
  if (!box) return;
  const b = JSON.parse(localStorage.getItem('fh_last_booking') || 'null');
  if (!b) { box.innerHTML = '<p>No recent booking found.</p>'; return; }
  box.innerHTML = `<div><span>Booking ID</span><strong>#${b.bookingId}</strong></div><div><span>Name</span><strong>${b.name}</strong></div><div><span>Hall</span><strong>${b.hallName}</strong></div><div><span>Date</span><strong>${b.date}</strong></div><div><span>Payment</span><strong>${b.paymentMethod}</strong></div><div><span>Total</span><strong>${money(b.total)}</strong></div>`;
}

function setMinDate(){const input=document.getElementById('eventDate'); if(input){const d=new Date(); input.min=new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10);}}

(async function init(){
  updateNavCount();
  const page = document.body.dataset.page;
  if(page==='halls'){wireHallFilters(); await loadHalls();}
  if(page==='food'){wireFoodFilters(); await loadFoods();}
  if(page==='booking'){
    try{await loadAllFoodForBooking();}catch(e){console.error(e)}
    renderBooking(); setMinDate();
    document.getElementById('bookingForm').addEventListener('submit', submitBooking);
    document.getElementById('clearCartBtn').addEventListener('click', ()=>{state.cart={};saveCart();updateNavCount();renderBooking();showToast('Food cart cleared');});
  }
  if(page==='success') renderSuccess();
})();
