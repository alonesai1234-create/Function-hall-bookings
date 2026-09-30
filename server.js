const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = Number(process.env.NODE_PORT || 3000);
const JAVA_API = process.env.JAVA_API || 'http://localhost:8080';
const PUBLIC_DIR = path.join(__dirname, 'frontend');

const mime = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.png':'image/png', '.jpg':'image/jpeg', '.jpeg':'image/jpeg', '.svg':'image/svg+xml', '.ico':'image/x-icon'
};

function send(res, code, data, type='text/plain; charset=utf-8') { res.writeHead(code, {'Content-Type':type}); res.end(data); }
function safePath(urlPath) { const decoded = decodeURIComponent(urlPath); const clean = path.normalize(decoded).replace(/^([.][.][/\\])+/, ''); return path.join(PUBLIC_DIR, clean === '/' ? 'index.html' : clean.replace(/^[/\\]/, '')); }

async function proxy(req, res) {
  const body = await new Promise(resolve => {
    let raw=''; req.on('data', c => raw += c); req.on('end', () => resolve(raw));
  });
  const target = new URL(req.url, JAVA_API);
  const headers = {'Content-Type': req.headers['content-type'] || 'application/json'};
  const response = await fetch(target, {method:req.method, headers, body: ['GET','HEAD'].includes(req.method) ? undefined : body});
  const text = await response.text();
  res.writeHead(response.status, {'Content-Type': response.headers.get('content-type') || 'application/json; charset=utf-8'});
  res.end(text);
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.url.startsWith('/api/')) { await proxy(req,res); return; }
    const filePath = safePath(new URL(req.url, `http://${req.headers.host}`).pathname);
    if (!filePath.startsWith(PUBLIC_DIR)) { send(res,403,'Forbidden'); return; }
    fs.readFile(filePath, (err,data) => {
      if (err) { send(res,404,'Page not found'); return; }
      send(res,200,data,mime[path.extname(filePath).toLowerCase()] || 'application/octet-stream');
    });
  } catch (e) { send(res,502,JSON.stringify({message:'Backend unavailable', detail:e.message}),'application/json'); }
});

server.listen(PORT, () => console.log(`Frontend server running at http://localhost:${PORT}`));
console.log(`Proxying /api/* to ${JAVA_API}`);
