/* UniHub service worker
   - pages: network first (always fresh), cached copy when offline, offline page as last resort
   - app files, icons, images, materials index: served from cache instantly, refreshed in the background
   - other sites (Drive, Apps Script, analytics) are never cached */
const VERSION = 'unihub-v17';
const PAGES = VERSION + '-pages';
const ASSETS = VERSION + '-assets';

const CORE = [
    '/',
    '/offline.html',
    '/guides/search',
    '/guides/saved',
    '/upload/uploadmethod',
    '/banks/subjects',
    '/assets/app/app.css?v=4',
    '/assets/app/app.js?v=4',
    '/assets/app/materials.json',
    '/assets/img/ASU_Jordan_logo-removebg-preview.png',
    '/icons/icon-192x192.png',
    '/manifest.json'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(ASSETS)
            .then(cache => Promise.all(CORE.map(u => cache.add(u).catch(() => {}))))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(k => !k.startsWith(VERSION)).map(k => caches.delete(k))))
            .then(() => self.registration.navigationPreload && self.registration.navigationPreload.enable())
            .then(() => self.clients.claim())
    );
});

// Tapping a chat notification opens that room, in a tab of this site if there is one
self.addEventListener('notificationclick', event => {
    event.notification.close();
    const url = (event.notification.data && event.notification.data.url) || '/chat/';
    event.waitUntil((async () => {
        const tabs = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
        const tab = tabs.find(t => new URL(t.url).origin === self.location.origin);
        if (!tab) return self.clients.openWindow(url);
        await tab.focus();
        return tab.navigate(url).catch(() => self.clients.openWindow(url));
    })());
});

async function trim(cacheName, max) {
    const cache = await caches.open(cacheName);
    const keys = await cache.keys();
    for (let i = 0; i < keys.length - max; i++) await cache.delete(keys[i]);
}

self.addEventListener('fetch', event => {
    const req = event.request;
    if (req.method !== 'GET') return;
    const url = new URL(req.url);
    if (url.origin !== self.location.origin) return;
    if (/\.(mp4|mov|webm)$/i.test(url.pathname)) return; // big media streams straight from the network

    // Pages
    if (req.mode === 'navigate') {
        event.respondWith((async () => {
            try {
                const preload = await event.preloadResponse;
                const res = preload || await fetch(req);
                if (res && res.ok) {
                    const copy = res.clone();
                    caches.open(PAGES).then(c => c.put(req, copy)).then(() => trim(PAGES, 80));
                }
                return res;
            } catch (e) {
                return (await caches.match(req, { ignoreSearch: true })) || caches.match('/offline.html');
            }
        })());
        return;
    }

    // Static files: stale-while-revalidate
    event.respondWith((async () => {
        const cache = await caches.open(ASSETS);
        const hit = await cache.match(req);
        const net = fetch(req).then(res => {
            if (res && res.ok && res.type === 'basic') cache.put(req, res.clone()).then(() => trim(ASSETS, 200));
            return res;
        }).catch(() => hit || Response.error());
        if (hit) { event.waitUntil(net); return hit; }
        return net;
    })());
});
