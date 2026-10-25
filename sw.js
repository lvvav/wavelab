// Soundlife: сервис-воркер. Кэширует оболочку сайта, чтобы он открывался без сети.
// Музыка сюда не попадает: треки хранятся отдельно (кнопка «Сохранить офлайн»).
var V = 'sl-shell-v1';
var SHELL = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png'];

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(V)
      .then(function (c) { return c.addAll(SHELL); })
      .then(function () { return self.skipWaiting(); })
      .catch(function () {})
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys()
      .then(function (ks) {
        return Promise.all(ks.filter(function (k) { return k.indexOf('sl-shell-') === 0 && k !== V; })
          .map(function (k) { return caches.delete(k); }));
      })
      .then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  var r = e.request, u = new URL(r.url);
  if (r.method !== 'GET' || u.origin !== location.origin || r.headers.has('range')) return;
  if (/\/music\//.test(u.pathname)) return;

  if (r.mode === 'navigate') {
    e.respondWith(
      fetch(r).then(function (res) {
        if (res && res.ok) { var cp = res.clone(); caches.open(V).then(function (c) { c.put('./index.html', cp); }); }
        return res;
      }).catch(function () {
        return caches.match('./index.html').then(function (m) { return m || caches.match('./'); });
      })
    );
    return;
  }

  e.respondWith(
    caches.match(r).then(function (m) {
      return m || fetch(r).then(function (res) {
        if (res && res.ok) { var cp = res.clone(); caches.open(V).then(function (c) { c.put(r, cp); }); }
        return res;
      });
    })
  );
});
