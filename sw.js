const CACHE = 'financas-v91';
const SHELL = [
  './', './index.html',
  './icons/icon-192.png', './icons/icon-512.png',
  './icons/icon-maskable-512.png', './icons/apple-touch-icon.png', './icons/favicon.png'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return; // cotações vão direto à rede

  // NUNCA tocar no próprio service worker nem no manifest: é por eles que o
  // navegador descobre que existe versão nova. Servir do cache aqui prende o
  // app na versão instalada para sempre.
  if (url.pathname.endsWith('/sw.js') || url.pathname.endsWith('.webmanifest')) return;

  const ehPagina = req.mode === 'navigate' || req.destination === 'document';

  if (ehPagina) {
    // rede primeiro, e sem passar pelo cache HTTP: o GitHub Pages manda
    // guardar a página por minutos, e "rede primeiro" por cima disso ainda
    // entrega a versão de antes — que é pior do que não atualizar, porque
    // parece atualizado
    e.respondWith(
      fetch(new Request(req.url, {cache: 'no-cache', credentials: 'same-origin'}))
        .then(res => {
          if (res && res.ok) caches.open(CACHE).then(c => c.put(req, res.clone()));
          return res;
        })
        .catch(() => caches.match(req).then(hit => hit || caches.match('./index.html')))
    );
    return;
  }

  // demais arquivos: cache primeiro, revalidando em segundo plano
  e.respondWith(
    caches.match(req).then(hit => {
      const net = fetch(req).then(res => {
        if (res && res.ok) caches.open(CACHE).then(c => c.put(req, res.clone()));
        return res;
      }).catch(() => hit);
      return hit || net;
    })
  );
});
