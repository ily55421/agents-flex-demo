/**
 * Service Worker - v1.2
 * 实现离线缓存、后台同步、推送通知支持
 */

const CACHE_NAME = 'docblog-v1.3';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/css/style.css',
  '/css/prism-tomorrow.css',
  '/js/app.js',
  '/js/storage.js',
  '/js/theme.js',
  '/js/router.js',
  '/js/utils.js',
  '/js/ui.js',
  '/js/prism.min.js',
  '/manifest.json'
];

// 安装：缓存静态资源
self.addEventListener('install', (event) => {
  console.log('[SW] Installing...');
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('[SW] Caching static assets');
        return cache.addAll(STATIC_ASSETS);
      })
      .catch(err => console.error('[SW] Cache failed:', err))
  );
  self.skipWaiting();
});

// 激活：清理旧缓存
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating...');
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames
          .filter(name => name !== CACHE_NAME)
          .map(name => {
            console.log('[SW] Deleting old cache:', name);
            return caches.delete(name);
          })
      );
    }).then(() => self.clients.claim())
  );
});

// 拦截请求：Cache-First 策略
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // 跳过非 GET 请求
  if (request.method !== 'GET') return;

  // 只处理 HTTP/HTTPS 请求，跳过 chrome-extension 等
  if (!request.url.startsWith('http')) {
    return;
  }

  // API 请求：Network-First，失败时返回离线数据
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request));
    return;
  }

  // 静态资源：Cache-First，后台更新
  event.respondWith(cacheFirst(request));
});

// Cache-First 策略
async function cacheFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);

  if (cached) {
    // 后台更新缓存
    fetch(request)
      .then(response => {
        if (response.ok) {
          cache.put(request, response.clone());
        }
      })
      .catch(() => {});
    return cached;
  }

  // 缓存未命中，从网络获取
  try {
    const response = await fetch(request);
    if (response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  } catch (err) {
    console.error('[SW] Fetch failed:', err);
    // 返回离线页面
    if (request.mode === 'navigate') {
      return cache.match('/index.html');
    }
    return new Response('Offline', { status: 503, statusText: 'Service Unavailable' });
  }
}

// Network-First 策略（用于 API）
async function networkFirst(request) {
  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (err) {
    const cache = await caches.open(CACHE_NAME);
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    return new Response(
      JSON.stringify({ error: 'offline', message: '当前处于离线状态' }),
      { status: 503, headers: { 'Content-Type': 'application/json' } }
    );
  }
}

// 后台同步
self.addEventListener('sync', (event) => {
  if (event.tag === 'sync-favorites') {
    event.waitUntil(syncFavorites());
  } else if (event.tag === 'sync-reading-progress') {
    event.waitUntil(syncReadingProgress());
  }
});

async function syncFavorites() {
  // 从 IndexedDB 读取待同步的收藏数据
  // 发送到服务器
  console.log('[SW] Syncing favorites...');
}

async function syncReadingProgress() {
  console.log('[SW] Syncing reading progress...');
}

// 推送通知（预留）
self.addEventListener('push', (event) => {
  const data = event.data?.json() || {};
  event.waitUntil(
    self.registration.showNotification(data.title || '文档博客', {
      body: data.body || '您有新的文档更新',
      icon: '/icon-192.png',
      badge: '/icon-72.png',
      data: data.url || '/'
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    clients.openWindow(event.notification.data)
  );
});
