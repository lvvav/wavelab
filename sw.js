// Soundlife: сервис-воркер. Кэширует оболочку сайта и обложки, чтобы всё открывалось быстро и без сети.
// Музыка сюда не попадает: треки хранятся отдельно (кнопка «Сохранить офлайн»).
var V = 'sl-shell-v41';
var IMG = 'sl-img-v1';
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

  // Обложки-картинки из папки music: сразу из кэша, а в фоне обновляем (stale-while-revalidate)
  if (/\/music\//.test(u.pathname)) {
    if (!/\.(jpe?g|png|webp)$/i.test(u.pathname)) return; // треки, meta.json и т.п. не трогаем
    e.respondWith(
      caches.open(IMG).then(function (c) {
        return c.match(r).then(function (m) {
          var net = fetch(r).then(function (res) {
            if (res && res.ok) c.put(r, res.clone());
            return res;
          }).catch(function () { return m; });
          return m || net;
        });
      })
    );
    return;
  }

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
