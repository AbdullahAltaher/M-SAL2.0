// Serves a built app at http://localhost:4321/M-SAL2.0/ the way GitHub Pages does (unknown paths
// get index.html, like its 404.html), to try the service worker and the update pill locally.
// Usage: node scripts/serve-pages.mjs <folder holding the build> (default: frontend/dist/msal2/browser)
// Copy a newer build into that folder while it runs to "deploy" it. HANDOFF.md section 5 has the steps.
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const root = resolve(process.argv[2] ?? join(here, '../frontend/dist/msal2/browser'));
const base = '/M-SAL2.0/';
const types = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml',
};

createServer((req, res) => {
  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (!path.startsWith(base)) {
    res.writeHead(302, { Location: base });
    return res.end();
  }
  let file = resolve(root, '.' + path.slice(base.length - 1));
  if (file !== root && !file.startsWith(root + sep)) {
    res.writeHead(403);
    return res.end();
  }
  if (!existsSync(file) || statSync(file).isDirectory()) file = join(root, 'index.html');
  res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'max-age=600' });
  createReadStream(file).pipe(res);
}).listen(4321, () => console.log(`serving ${root} at http://localhost:4321${base}`));
