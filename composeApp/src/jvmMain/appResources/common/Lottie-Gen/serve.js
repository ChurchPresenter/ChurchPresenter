const http = require('http');
const fs = require('fs');
const path = require('path');

const DIR = __dirname;
const PORT = 8090;
const PRESETS_FILE = path.join(DIR, 'presets.json');
const COLORS_FILE = path.join(DIR, 'color-themes.json');
const LOGOS_DIR = path.join(DIR, 'logos');

const MIME = {
  '.html': 'text/html',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
};

http.createServer((req, res) => {
  // API: save presets
  if (req.method === 'POST' && req.url === '/api/presets') {
    let body = '';
    req.on('data', c => body += c);
    req.on('end', () => {
      fs.writeFileSync(PRESETS_FILE, body, 'utf8');
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"ok":true}');
    });
    return;
  }

  // API: read presets
  if (req.method === 'GET' && req.url === '/api/presets') {
    let data = '[]';
    try { data = fs.readFileSync(PRESETS_FILE, 'utf8'); } catch(e) {}
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(data);
    return;
  }

  // API: save color themes
  if (req.method === 'POST' && req.url === '/api/color-themes') {
    let body = '';
    req.on('data', c => body += c);
    req.on('end', () => {
      fs.writeFileSync(COLORS_FILE, body, 'utf8');
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"ok":true}');
    });
    return;
  }

  // API: read color themes
  if (req.method === 'GET' && req.url === '/api/color-themes') {
    let data = '[]';
    try { data = fs.readFileSync(COLORS_FILE, 'utf8'); } catch(e) {}
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(data);
    return;
  }

  // API: list logos
  if (req.method === 'GET' && req.url === '/api/logos') {
    try {
      const files = fs.readdirSync(LOGOS_DIR).filter(f => /\.(png|jpe?g|svg|webp)$/i.test(f));
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(files));
    } catch(e) { res.writeHead(200); res.end('[]'); }
    return;
  }

  // API: upload logo (base64 JSON body: { name, data })
  if (req.method === 'POST' && req.url === '/api/logos') {
    let body = '';
    req.on('data', c => body += c);
    req.on('end', () => {
      try {
        const { name, data } = JSON.parse(body);
        const safeName = path.basename(name);
        const match = data.match(/^data:[^;]+;base64,(.+)$/);
        if (!match || !safeName) { res.writeHead(400); res.end('{"error":"bad data"}'); return; }
        fs.writeFileSync(path.join(LOGOS_DIR, safeName), Buffer.from(match[1], 'base64'));
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ file: safeName }));
      } catch(e) { res.writeHead(500); res.end('{"error":"write failed"}'); }
    });
    return;
  }

  // Static file serving
  let filePath = path.join(DIR, req.url === '/' ? 'lottie-generator.html' : decodeURIComponent(req.url));
  filePath = path.normalize(filePath);
  if (!filePath.startsWith(DIR)) { res.writeHead(403); res.end(); return; }

  fs.readFile(filePath, (err, content) => {
    if (err) { res.writeHead(404); res.end('Not found'); return; }
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    res.end(content);
  });
}).listen(PORT, () => {
  const url = `http://localhost:${PORT}`;
  console.log(`Lottie Generator → ${url}`);
  // Auto-open browser (cross-platform)
  const { exec } = require('child_process');
  const cmd = process.platform === 'win32' ? `start ${url}`
            : process.platform === 'darwin' ? `open ${url}`
            : `xdg-open ${url}`;
  exec(cmd);
});
