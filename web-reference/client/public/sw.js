// Reset service worker — minimal and non-intrusive.
// It deliberately does NOT cache app code or API responses, so the app always
// loads fresh from the network. This avoids any stale/broken cache serving the
// wrong assets. It only: (1) wipes any old caches from previous versions,
// (2) provides an offline fallback page for failed navigations, and
// (3) handles notification clicks.

const CACHE = "weather-v4";

// ─── Install ──────────────────────────────────────────────────────────────────
self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(CACHE)
      .then((c) => c.add("/offline.html"))
      .catch(() => {})
      .then(() => self.skipWaiting())
  );
});

// ─── Activate — purge ALL old caches, take control immediately ──────────────────
self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// ─── Fetch ────────────────────────────────────────────────────────────────────
// Only intercept top-level navigations to provide an offline fallback.
// Everything else (JS, CSS, fonts, /api/*, tiles) is left untouched and goes
// straight to the network — no caching, no interception.
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  if (e.request.mode === "navigate") {
    e.respondWith(
      fetch(e.request).catch(() => caches.match("/offline.html"))
    );
  }
});

// ─── Notifications ────────────────────────────────────────────────────────────
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  e.waitUntil(
    self.clients.matchAll({ type: "window" }).then((clients) => {
      if (clients.length > 0) return clients[0].focus();
      return self.clients.openWindow("/");
    })
  );
});
