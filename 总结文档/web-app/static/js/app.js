/**
 * 文档博客前端应用 - v1.1
 * 模块化重构版本，使用 ES6 Module
 * 功能：SPA 路由、SQLite FTS5 搜索、主题切换、章节导航
 */

import { Storage } from './storage.js';
import { Theme } from './theme.js';
import { Router } from './router.js';
import { debounce, throttle, formatDate, escapeHtml, highlightText, readingTime, downloadJSON, readJSONFile } from './utils.js';
import { showToast, showModal, confirmDialog, createSkeleton, showLoading, hideLoading } from './ui.js';
import { KnowledgeGraph } from './knowledge-graph.js';

// 全局暴露（供 HTML 内联事件使用）
window.showToast = showToast;
window.showModal = showModal;
window.confirmDialog = confirmDialog;

const App = {
  state: {
    view: 'home',
    categories: [],
    documents: [],
    currentCategory: null,
    currentDoc: null,
    searchQuery: '',
    searchCategory: '',
    page: 1,
    totalPages: 0,
    totalDocs: 0,
    stats: {},
    tocItems: [],
    activeTocId: null,
    fontSize: 0.93,
    favorites: [],
    searchHistory: [],
    suggestIndex: -1,
    suggestItems: [],
    tags: [],
    adminMode: false,
    selectedDocs: [],
  },

  // ─── 初始化 ──────────────────────────────────
  async init() {
    Theme.init();
    this.initFontSize();
    this.initFavorites();
    this.initSearchHistory();
    this.initReadingMode();
    this.initServiceWorker();
    this.initErrorMonitoring();
    this.initPerformanceMonitoring();
    this.bindEvents();
    this.initRouter();
    await this.loadCategories();
    await this.loadTags();
    await this.loadStats();
    this.renderHome();
    Router.handle();
  },

  // ─── Service Worker 注册 ─────────────────────
  initServiceWorker() {
    if ('serviceWorker' in navigator) {
      window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
          .then(reg => {
            console.log('[App] SW registered:', reg.scope);
            // 检测更新
            reg.addEventListener('updatefound', () => {
              const newWorker = reg.installing;
              newWorker.addEventListener('statechange', () => {
                if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                  showToast('发现新版本，刷新页面更新', 'info', 5000);
                }
              });
            });
          })
          .catch(err => console.error('[App] SW registration failed:', err));
      });
    }
  },

  // ─── 阅读模式 ────────────────────────────────
  initReadingMode() {
    const saved = Storage.get('readingMode', false);
    if (saved) {
      document.body.classList.add('reading-mode');
    }
  },

  toggleReadingMode() {
    document.body.classList.toggle('reading-mode');
    const isReadingMode = document.body.classList.contains('reading-mode');
    Storage.set('readingMode', isReadingMode);
    showToast(isReadingMode ? '已进入阅读模式' : '已退出阅读模式', 'info');
  },

  // ─── 字体大小 ────────────────────────────────
  initFontSize() {
    const saved = Storage.get('fontSize');
    if (saved) {
      this.state.fontSize = saved;
      this.applyFontSize();
    }
  },

  applyFontSize() {
    document.documentElement.style.setProperty('--article-font-size', `${this.state.fontSize}rem`);
    Storage.set('fontSize', this.state.fontSize);
  },

  increaseFontSize() {
    if (this.state.fontSize < 1.3) {
      this.state.fontSize = Math.round((this.state.fontSize + 0.05) * 100) / 100;
      this.applyFontSize();
      showToast('字体已放大', 'info', 1500);
    }
  },

  decreaseFontSize() {
    if (this.state.fontSize > 0.75) {
      this.state.fontSize = Math.round((this.state.fontSize - 0.05) * 100) / 100;
      this.applyFontSize();
      showToast('字体已缩小', 'info', 1500);
    }
  },

  // ─── 收藏管理 ────────────────────────────────
  initFavorites() {
    this.state.favorites = Storage.get('favorites', []);
  },

  isFavorite(docId) {
    return this.state.favorites.includes(parseInt(docId));
  },

  toggleFavorite(docId) {
    docId = parseInt(docId);
    const idx = this.state.favorites.indexOf(docId);
    if (idx >= 0) {
      this.state.favorites.splice(idx, 1);
      showToast('已取消收藏', 'info');
    } else {
      this.state.favorites.push(docId);
      showToast('已添加到收藏', 'success');
    }
    Storage.set('favorites', this.state.favorites);
    this.renderSidebar();
    const btn = document.getElementById('favoriteBtn');
    if (btn) {
      btn.classList.toggle('active', this.isFavorite(docId));
      btn.innerHTML = this.isFavorite(docId) ? '⭐ 已收藏' : '☆ 收藏';
    }
  },

  // ─── 搜索历史 ────────────────────────────────
  initSearchHistory() {
    this.state.searchHistory = Storage.get('searchHistory', []);
  },

  addSearchHistory(query) {
    if (!query || !query.trim()) return;
    const q = query.trim();
    this.state.searchHistory = this.state.searchHistory.filter(h => h !== q);
    this.state.searchHistory.unshift(q);
    if (this.state.searchHistory.length > 10) {
      this.state.searchHistory = this.state.searchHistory.slice(0, 10);
    }
    Storage.set('searchHistory', this.state.searchHistory);
  },

  removeSearchHistory(query) {
    this.state.searchHistory = this.state.searchHistory.filter(h => h !== query);
    Storage.set('searchHistory', this.state.searchHistory);
    this.renderSearchHistory();
  },

  clearSearchHistory() {
    this.state.searchHistory = [];
    Storage.remove('searchHistory');
    this.renderSearchHistory();
    showToast('搜索历史已清空', 'info');
  },

  // ─── 事件绑定 ────────────────────────────────
  bindEvents() {
    const searchInput = document.getElementById('searchInput');
    let searchTimeout;

    // 输入时搜索建议
    searchInput.addEventListener('input', (e) => {
      clearTimeout(searchTimeout);
      const q = e.target.value.trim();
      this.hideSearchHistory();
      if (q.length >= 1) {
        searchTimeout = setTimeout(() => this.loadSearchSuggest(q), 150);
      } else {
        this.hideSearchSuggest();
      }
    });

    // 聚焦时显示搜索历史
    searchInput.addEventListener('focus', () => {
      const q = searchInput.value.trim();
      if (!q && this.state.searchHistory.length > 0) {
        this.renderSearchHistory();
      }
    });

    // 键盘事件
    searchInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        this.hideSearchSuggest();
        this.hideSearchHistory();
        const q = searchInput.value.trim();
        if (q) {
          this.addSearchHistory(q);
          this.state.searchQuery = q;
          this.state.page = 1;
          this.doSearch();
        }
      }
      if (e.key === 'Escape') {
        this.hideSearchSuggest();
        this.hideSearchHistory();
        searchInput.value = '';
        searchInput.blur();
        if (this.state.view === 'search') {
          this.renderHome();
        }
      }
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        this.navigateSuggest(e.key === 'ArrowDown' ? 1 : -1);
      }
    });

    // 点击外部关闭下拉
    document.addEventListener('click', (e) => {
      if (!e.target.closest('.search-box')) {
        this.hideSearchSuggest();
        this.hideSearchHistory();
      }
    });

    // 键盘快捷键
    document.addEventListener('keydown', (e) => {
      // Ctrl+K 搜索
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        searchInput.focus();
        searchInput.select();
      }
      // ? 显示快捷键帮助
      if (e.key === '?' && !e.ctrlKey && !e.metaKey && document.activeElement.tagName !== 'INPUT') {
        e.preventDefault();
        this.showShortcutsHelp();
      }
      // ESC 关闭弹窗
      if (e.key === 'Escape') {
        const modal = document.querySelector('.modal-overlay.show');
        if (modal) modal.querySelector('.modal-close')?.click();
      }
      // g 回到首页
      if (e.key === 'g' && !e.ctrlKey && !e.metaKey && document.activeElement.tagName !== 'INPUT') {
        this.renderHome();
      }
    });

    // 移动端菜单
    document.getElementById('menuToggle').addEventListener('click', () => {
      document.getElementById('sidebar').classList.toggle('open');
      document.getElementById('overlay').classList.toggle('show');
    });
    document.getElementById('overlay').addEventListener('click', () => {
      document.getElementById('sidebar').classList.remove('open');
      document.getElementById('overlay').classList.remove('show');
    });

    // 主题切换
    document.getElementById('themeToggle').addEventListener('click', () => {
      Theme.toggle();
    });

    // 返回顶部
    window.addEventListener('scroll', throttle(() => {
      const btn = document.getElementById('backToTop');
      btn.classList.toggle('show', window.scrollY > 400);
      if (this.state.view === 'article') {
        this.updateActiveTocOnScroll();
        this.saveScrollPosition();
      }
    }, 100));
    document.getElementById('backToTop').addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    // 图片灯箱
    document.getElementById('lightboxClose').addEventListener('click', () => {
      document.getElementById('lightbox').classList.remove('show');
    });
    document.getElementById('lightbox').addEventListener('click', (e) => {
      if (e.target.id === 'lightbox') {
        document.getElementById('lightbox').classList.remove('show');
      }
    });

    // 移动端侧边栏滑动手势
    this.initTouchGestures();
  },

  // ─── 移动端手势 ──────────────────────────────
  initTouchGestures() {
    let startX = 0;
    let startY = 0;
    const sidebar = document.getElementById('sidebar');

    document.addEventListener('touchstart', (e) => {
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
    }, { passive: true });

    document.addEventListener('touchend', (e) => {
      const endX = e.changedTouches[0].clientX;
      const endY = e.changedTouches[0].clientY;
      const diffX = endX - startX;
      const diffY = endY - startY;

      // 左滑关闭侧边栏（在侧边栏打开时）
      if (sidebar.classList.contains('open') && diffX < -50 && Math.abs(diffX) > Math.abs(diffY)) {
        sidebar.classList.remove('open');
        document.getElementById('overlay').classList.remove('show');
      }
      // 右滑打开侧边栏（从屏幕左边缘开始）
      if (!sidebar.classList.contains('open') && startX < 30 && diffX > 80 && Math.abs(diffX) > Math.abs(diffY)) {
        sidebar.classList.add('open');
        document.getElementById('overlay').classList.add('show');
      }
    }, { passive: true });
  },

  // ─── 快捷键帮助 ──────────────────────────────
  showShortcutsHelp() {
    const content = `
      <div class="shortcuts-list">
        <div class="shortcut-item"><kbd>Ctrl</kbd> + <kbd>K</kbd> <span>聚焦搜索框</span></div>
        <div class="shortcut-item"><kbd>?</kbd> <span>显示快捷键帮助</span></div>
        <div class="shortcut-item"><kbd>Esc</kbd> <span>关闭弹窗/返回</span></div>
        <div class="shortcut-item"><kbd>G</kbd> <span>回到首页</span></div>
        <div class="shortcut-item"><kbd>↑</kbd> <kbd>↓</kbd> <span>搜索建议导航</span></div>
      </div>
    `;
    showModal({ title: '⌨️ 键盘快捷键', content, size: 'sm' });
  },

  // ─── 路由初始化 ──────────────────────────────
  initRouter() {
    Router.register('/', () => this.renderHome());
    Router.register('favorites', () => this.renderFavorites());
    Router.register('tags', () => this.renderTagsCloud());
    Router.register('trash', () => this.renderTrash());
    Router.register('category/:slug', (query, params) => this.renderCategory(params[0]));
    Router.register('article/:id', (query, params) => this.renderArticle(params[0]));
    Router.register('search/:query', (query, params) => {
      const q = decodeURIComponent(params[0]);
      this.state.searchQuery = q;
      document.getElementById('searchInput').value = q;
      this.doSearch();
    });
    Router.register('tag/:slug', (query, params) => this.renderTag(params[0]));
    Router.init();
  },

  // ─── 搜索建议 ────────────────────────────────
  async loadSearchSuggest(q) {
    try {
      const data = await this.api(`search-suggest?q=${encodeURIComponent(q)}&limit=8`);
      this.state.suggestItems = data.suggestions || [];
      this.state.suggestIndex = -1;
      this.renderSearchSuggest(q);
    } catch (e) {
      this.hideSearchSuggest();
    }
  },

  renderSearchSuggest(q) {
    const container = document.getElementById('searchSuggest');
    if (!this.state.suggestItems.length) {
      container.innerHTML = '<div class="search-suggest-empty">无匹配建议</div>';
      container.classList.add('show');
      return;
    }
    container.innerHTML = this.state.suggestItems.map((item, i) => `
      <div class="search-suggest-item ${i === this.state.suggestIndex ? 'active' : ''}" data-index="${i}" data-id="${item.id}">
        <span class="suggest-icon">${item.icon || '📄'}</span>
        <span class="suggest-title">${highlightText(escapeHtml(item.title), q)}</span>
        <span class="suggest-cat">${item.category_name}</span>
      </div>
    `).join('');
    container.querySelectorAll('.search-suggest-item').forEach(el => {
      el.addEventListener('click', () => {
        this.navigate(`article/${el.dataset.id}`);
        this.hideSearchSuggest();
      });
    });
    container.classList.add('show');
  },

  hideSearchSuggest() {
    document.getElementById('searchSuggest').classList.remove('show');
    this.state.suggestIndex = -1;
  },

  navigateSuggest(dir) {
    if (!this.state.suggestItems.length) return;
    this.state.suggestIndex += dir;
    if (this.state.suggestIndex < -1) this.state.suggestIndex = -1;
    if (this.state.suggestIndex >= this.state.suggestItems.length) {
      this.state.suggestIndex = this.state.suggestItems.length - 1;
    }
    if (this.state.suggestIndex >= 0) {
      const item = this.state.suggestItems[this.state.suggestIndex];
      document.getElementById('searchInput').value = item.title;
    }
    this.renderSearchSuggest(document.getElementById('searchInput').value);
  },

  // ─── 搜索历史 UI ─────────────────────────────
  renderSearchHistory() {
    const container = document.getElementById('searchHistory');
    if (!this.state.searchHistory.length) {
      container.classList.remove('show');
      return;
    }
    container.innerHTML = `
      <div class="search-history-header">
        <span>最近搜索</span>
        <button onclick="App.clearSearchHistory()">清空</button>
      </div>
      ${this.state.searchHistory.map(q => `
        <div class="search-history-item" data-query="${escapeHtml(q)}">
          <span class="history-icon">🕐</span>
          <span class="history-text">${escapeHtml(q)}</span>
          <button class="history-delete" onclick="event.stopPropagation(); App.removeSearchHistory('${escapeHtml(q).replace(/'/g, "\\'")}')">&times;</button>
        </div>
      `).join('')}
    `;
    container.querySelectorAll('.search-history-item').forEach(el => {
      el.addEventListener('click', () => {
        const q = el.dataset.query;
        document.getElementById('searchInput').value = q;
        this.state.searchQuery = q;
        this.state.page = 1;
        this.hideSearchHistory();
        this.doSearch();
      });
    });
    container.classList.add('show');
  },

  hideSearchHistory() {
    document.getElementById('searchHistory').classList.remove('show');
  },

  // ─── API 调用 ────────────────────────────────
  async api(path, options = {}) {
    try {
      const url = `/api/${path}`;
      const opts = {
        method: options.method || 'GET',
        headers: { 'Content-Type': 'application/json' }
      };
      // 管理操作自动附加 Token
      const token = localStorage.getItem('admin_token');
      if (token) {
        opts.headers['X-Admin-Token'] = token;
      }
      if (options.body) {
        opts.body = JSON.stringify(options.body);
      }
      const res = await fetch(url, opts);
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }
      return res.json();
    } catch (err) {
      console.error(`[API] /api/${path} failed:`, err.message);
      // 返回空数据避免页面崩溃
      return { documents: [], total: 0, page: 1, pages: 0, categories: [], tags: [], suggestions: [] };
    }
  },

  async loadCategories() {
    const data = await this.api('categories');
    this.state.categories = data.categories;
    this.renderSidebar();
  },

  async loadStats() {
    const data = await this.api('stats');
    this.state.stats = data;
  },

  async loadDocuments(categorySlug, page = 1) {
    const data = await this.api(`documents?category=${encodeURIComponent(categorySlug)}&page=${page}&limit=20`);
    return data;
  },

  async loadDocument(id) {
    const data = await this.api(`document?id=${id}`);
    return data.document;
  },

  async search(query, page = 1, category = '') {
    let url = `search?q=${encodeURIComponent(query)}&page=${page}&limit=20`;
    if (category) url += `&category=${encodeURIComponent(category)}`;
    const data = await this.api(url);
    return data;
  },

  async loadRelated(id) {
    const data = await this.api(`related?id=${id}&limit=5`);
    return data.documents || [];
  },

  async loadTags() {
    const data = await this.api('tags');
    this.state.tags = data.tags || [];
  },

  async loadTagDocuments(slug, page = 1) {
    const data = await this.api(`tag?slug=${encodeURIComponent(slug)}&page=${page}&limit=20`);
    return data;
  },

  async loadTrash(page = 1) {
    const data = await this.api(`trash?page=${page}&limit=20`);
    return data;
  },

  async softDeleteDoc(id) {
    const data = await this.api('delete', { method: 'POST', body: { id } });
    return data;
  },

  async restoreDoc(id) {
    const data = await this.api('restore', { method: 'POST', body: { id } });
    return data;
  },

  async purgeDoc(id) {
    const data = await this.api('purge', { method: 'POST', body: { id } });
    return data;
  },

  async batchRecategorize(ids, slug) {
    const data = await this.api('batch-recategorize', { method: 'POST', body: { ids, slug } });
    return data;
  },

  // ─── 数据导出导入 ────────────────────────────
  exportData() {
    const data = Storage.export();
    downloadJSON(data, `docblog-backup-${new Date().toISOString().slice(0, 10)}.json`);
    showToast('数据已导出', 'success');
  },

  async importData(file) {
    try {
      const data = await readJSONFile(file);
      Storage.import(data);
      this.initFavorites();
      this.initSearchHistory();
      this.initFontSize();
      this.renderSidebar();
      showToast('数据已导入', 'success');
    } catch (err) {
      showToast(err.message, 'error');
    }
  },

  // ─── 渲染：首页 ──────────────────────────────
  renderHome() {
    this.state.view = 'home';
    this.hideToc();
    document.getElementById('searchInput').value = '';
    const { stats, categories } = this.state;

    const html = `
      <div class="stats-row">
        <div class="stat-card">
          <div class="label">📄 文档总数</div>
          <div class="value">${stats.total_documents || 0}</div>
        </div>
        <div class="stat-card">
          <div class="label">📂 分类数量</div>
          <div class="value green">${stats.total_categories || 0}</div>
        </div>
        <div class="stat-card">
          <div class="label">📝 总字数</div>
          <div class="value orange">${this.formatNumber(stats.total_words || 0)}</div>
        </div>
      </div>
      <div class="section-title">
        📂 文档分类
        <span class="badge">${categories.length} 个分类</span>
      </div>
      <div class="category-grid">
        ${categories.map(cat => `
          <div class="category-card" onclick="App.navigate('category/${cat.slug}')">
            <div class="cat-header">
              <span class="cat-icon">${cat.icon}</span>
              <span class="cat-name">${cat.name}</span>
            </div>
            <div class="cat-count">${cat.doc_count} 篇文档</div>
          </div>
        `).join('')}
      </div>
      <div class="section-title">
        🕐 最新文档
      </div>
      <div class="doc-list" id="recentDocs">
        ${createSkeleton('card', 3)}
      </div>
    `;

    document.getElementById('content').innerHTML = html;
    this.loadRecentDocs();
    this.updateActiveNav('home');
    window.scrollTo(0, 0);
  },

  async loadRecentDocs() {
    try {
      const data = await this.api('documents?page=1&limit=10');
      const container = document.getElementById('recentDocs');
      if (!container) return; // 页面已切换
      if (!data.documents.length) {
        container.innerHTML = '<div class="empty"><div class="empty-icon">📭</div><p>暂无文档</p></div>';
        return;
      }
      container.innerHTML = data.documents.map(doc => this.renderDocCard(doc)).join('');
    } catch (err) {
      console.error('[loadRecentDocs] failed:', err);
    }
  },

  // ─── 渲染：收藏页 ────────────────────────────
  async renderFavorites() {
    this.state.view = 'favorites';
    this.hideToc();
    document.getElementById('searchInput').value = '';

    if (!this.state.favorites.length) {
      document.getElementById('content').innerHTML = `
        <div class="article-header">
          <div class="breadcrumb"><a href="#" onclick="App.renderHome(); return false;">首页</a> / 我的收藏</div>
          <h1>⭐ 我的收藏</h1>
        </div>
        <div class="empty"><div class="empty-icon">⭐</div><p>暂无收藏文档</p></div>
      `;
      this.updateActiveNav('favorites');
      return;
    }

    const docs = [];
    for (const id of this.state.favorites) {
      try {
        const doc = await this.loadDocument(id);
        if (doc && !doc.error) docs.push(doc);
      } catch (e) {}
    }

    document.getElementById('content').innerHTML = `
      <div class="article-header">
        <div class="breadcrumb"><a href="#" onclick="App.renderHome(); return false;">首页</a> / 我的收藏</div>
        <h1>⭐ 我的收藏</h1>
        <div class="article-meta"><span>共 ${docs.length} 篇收藏</span></div>
      </div>
      <div class="doc-list">
        ${docs.length ? docs.map(doc => this.renderDocCard(doc)).join('') : '<div class="empty"><div class="empty-icon">📭</div><p>暂无收藏文档</p></div>'}
      </div>
    `;
    this.updateActiveNav('favorites');
    window.scrollTo(0, 0);
  },

  // ─── 渲染：分类页 ────────────────────────────
  async renderCategory(slug) {
    this.state.view = 'category';
    this.hideToc();
    this.state.currentCategory = slug;
    this.state.page = 1;

    const cat = this.state.categories.find(c => c.slug === slug);
    const catName = cat ? cat.name : slug;
    const catIcon = cat ? cat.icon : '📁';

    document.getElementById('content').innerHTML = `
      <div class="article-header">
        <div class="breadcrumb">
          <a href="#" onclick="App.renderHome(); return false;">首页</a> / ${catName}
        </div>
        <h1>${catIcon} ${catName}</h1>
      </div>
      <div class="doc-list" id="categoryDocs">
        ${createSkeleton('card', 3)}
      </div>
      <div class="pagination" id="pagination"></div>
    `;

    await this.loadCategoryDocs(slug, 1);
    this.updateActiveNav(slug);
    window.scrollTo(0, 0);
  },

  async loadCategoryDocs(slug, page) {
    const data = await this.loadDocuments(slug, page);
    this.state.page = page;
    this.state.totalPages = data.pages;
    this.state.totalDocs = data.total;

    const container = document.getElementById('categoryDocs');
    if (!data.documents.length) {
      container.innerHTML = '<div class="empty"><div class="empty-icon">📭</div><p>该分类下暂无文档</p></div>';
      return;
    }
    container.innerHTML = data.documents.map(doc => this.renderDocCard(doc)).join('');
    this.renderPagination();
  },

  // ─── 渲染：搜索页 ────────────────────────────
  async doSearch() {
    const q = this.state.searchQuery;
    if (!q) return;

    this.state.view = 'search';
    this.hideToc();
    this.state.page = 1;
    window.location.hash = `search/${encodeURIComponent(q)}`;

    const catOptions = this.state.categories.map(c =>
      `<option value="${c.slug}" ${this.state.searchCategory === c.slug ? 'selected' : ''}>${c.name}</option>`
    ).join('');

    document.getElementById('content').innerHTML = `
      <div class="search-header">
        <h2>🔍 搜索结果</h2>
        <div class="search-info">
          关键词：<span class="search-query">${escapeHtml(q)}</span>
          <span id="searchCount"></span>
        </div>
      </div>
      <div class="search-filter-bar">
        <select id="searchCategoryFilter" onchange="App.onSearchCategoryChange(this.value)">
          <option value="">全部分类</option>
          ${catOptions}
        </select>
      </div>
      <div class="doc-list" id="searchResults">
        ${createSkeleton('card', 3)}
      </div>
      <div class="pagination" id="pagination"></div>
    `;

    await this.loadSearchResults(q, 1);
    this.updateActiveNav('');
    window.scrollTo(0, 0);
  },

  async loadSearchResults(q, page) {
    const data = await this.search(q, page, this.state.searchCategory);
    this.state.page = page;
    this.state.totalPages = data.pages;

    document.getElementById('searchCount').textContent = ` — 共找到 ${data.total} 条结果`;

    const container = document.getElementById('searchResults');
    if (!data.documents.length) {
      container.innerHTML = `<div class="empty"><div class="empty-icon">🔍</div><p>没有找到与「${escapeHtml(q)}」相关的文档</p></div>`;
      return;
    }
    container.innerHTML = data.documents.map(doc => this.renderDocCard(doc, q)).join('');
    this.renderPagination();
  },

  onSearchCategoryChange(slug) {
    this.state.searchCategory = slug;
    this.state.page = 1;
    this.loadSearchResults(this.state.searchQuery, 1);
  },

  // ─── 渲染：文章页 ────────────────────────────
  async renderArticle(id) {
    this.state.view = 'article';

    document.getElementById('content').innerHTML = createSkeleton('text', 5);
    this.hideToc();

    const doc = await this.loadDocument(id);
    if (!doc) {
      document.getElementById('content').innerHTML = '<div class="empty"><div class="empty-icon">❌</div><p>文档不存在</p></div>';
      return;
    }

    this.state.currentDoc = doc;
    const cat = this.state.categories.find(c => c.id === doc.category_id);
    const isFav = this.isFavorite(id);

    document.getElementById('content').innerHTML = `
      <div class="article-header">
        <div class="breadcrumb">
          <a href="#" onclick="App.renderHome(); return false;">首页</a> /
          <a href="#" onclick="App.navigate('category/${cat ? cat.slug : ''}'); return false;">${doc.category_name}</a> /
          <span>${doc.title}</span>
        </div>
        <h1>${doc.title}</h1>
        <div class="article-meta">
          <span>📂 ${doc.category_name}</span>
          <span>📝 ${this.formatNumber(doc.word_count)} 字</span>
          <span>⏱️ ${doc.read_time} 分钟阅读</span>
          <span>📅 更新：${doc.updated_at}</span>
        </div>
        <div class="article-toolbar">
          <button class="favorite-btn ${isFav ? 'active' : ''}" id="favoriteBtn" onclick="App.toggleFavorite(${doc.id})">
            ${isFav ? '⭐ 已收藏' : '☆ 收藏'}
          </button>
          <div class="font-size-controls">
            <button onclick="App.decreaseFontSize()" title="缩小字体">A-</button>
            <button onclick="App.increaseFontSize()" title="放大字体">A+</button>
          </div>
          <div class="in-page-search">
            <input type="text" id="inPageSearchInput" placeholder="页面搜索..." onkeydown="App.handleSearchKeydown(event)">
            <span id="searchMatchCount"></span>
            <button onclick="App.prevSearchMatch()" title="上一个匹配">↑</button>
            <button onclick="App.nextSearchMatch()" title="下一个匹配">↓</button>
            <button onclick="App.clearInPageSearch()" title="清除搜索">×</button>
          </div>
        </div>
      </div>
      <div class="article-content" id="articleContent">
        ${doc.html_content}
      </div>
      <div class="related-docs" id="relatedDocs"></div>
      <div class="knowledge-graph-section">
        <h3>🕸️ 知识图谱</h3>
        <div class="knowledge-graph-container" id="knowledgeGraph"></div>
        <div class="graph-legend">
          <span class="legend-item"><span class="legend-dot" style="background:#6c8cff"></span>当前文档</span>
          <span class="legend-item"><span class="legend-dot" style="background:rgba(250,204,21,0.8)"></span>相似文档</span>
          <span class="legend-item"><span class="legend-dot" style="background:rgba(108,140,255,0.8)"></span>同分类</span>
        </div>
      </div>
    `;

    // 绑定图片灯箱
    document.querySelectorAll('.article-content img').forEach(img => {
      img.addEventListener('click', () => this.openLightbox(img.src));
      // 图片懒加载
      img.setAttribute('loading', 'lazy');
    });

    this.buildToc();
    this.highlightCode();
    this.renderMermaid();
    this.loadRelatedDocs(id);
    this.loadKnowledgeGraph(id);
    this.restoreScrollPosition(id);
    this.updateActiveNav('');
  },

  async loadRelatedDocs(id) {
    const docs = await this.loadRelated(id);
    const container = document.getElementById('relatedDocs');
    if (!docs.length || !container) return;
    container.innerHTML = `
      <h3>📎 相关文档</h3>
      <div class="doc-list">
        ${docs.map(doc => this.renderDocCard(doc)).join('')}
      </div>
    `;
  },

  // ─── 知识图谱 ────────────────────────────────
  async loadKnowledgeGraph(id) {
    try {
      const data = await this.api(`graph?id=${id}`);
      if (data.error || !data.nodes || data.nodes.length <= 1) return;
      
      const container = document.getElementById('knowledgeGraph');
      if (!container) return;
      
      // 销毁旧实例
      if (this.knowledgeGraph) {
        this.knowledgeGraph.destroy();
      }
      
      this.knowledgeGraph = new KnowledgeGraph('knowledgeGraph', {
        height: 350,
        onNodeClick: (node) => {
          if (node.id !== id) {
            this.navigate(`article/${node.id}`);
          }
        }
      });
      
      this.knowledgeGraph.setData(data);
    } catch (err) {
      console.error('[KnowledgeGraph] failed:', err);
    }
  },

  // ─── 阅读进度 ────────────────────────────────
  saveScrollPosition() {
    if (!this.state.currentDoc) return;
    Storage.set(`scroll_${this.state.currentDoc.id}`, window.scrollY);
  },

  restoreScrollPosition(id) {
    const saved = Storage.get(`scroll_${id}`);
    if (saved && parseInt(saved) > 100) {
      const btn = document.createElement('button');
      btn.className = 'continue-reading show';
      btn.id = 'continueReading';
      btn.textContent = '⬇ 继续阅读';
      btn.onclick = () => {
        window.scrollTo({ top: parseInt(saved), behavior: 'smooth' });
        btn.remove();
      };
      document.body.appendChild(btn);
      setTimeout(() => {
        const b = document.getElementById('continueReading');
        if (b) b.classList.remove('show');
      }, 5000);
    }
  },

  // ─── 图片灯箱 ────────────────────────────────
  openLightbox(src) {
    const lightbox = document.getElementById('lightbox');
    const img = document.getElementById('lightboxImg');
    img.src = src;
    lightbox.classList.add('show');
  },

  // ─── 代码高亮 ────────────────────────────────
  highlightCode() {
    if (window.Prism) {
      Prism.highlightAllUnder(document.getElementById('articleContent'));
    }
  },

  // ─── Mermaid 图表渲染 ─────────────────────────
  renderMermaid() {
    console.log('[Mermaid] renderMermaid called');
    const content = document.getElementById('articleContent');
    if (!content) {
      console.log('[Mermaid] articleContent not found');
      return;
    }

    // 如果 mermaid 还没加载，延迟重试
    if (!window.mermaid) {
      console.log('[Mermaid] not loaded yet, retrying in 500ms...');
      setTimeout(() => this.renderMermaid(), 500);
      return;
    }

    console.log('[Mermaid] window.mermaid is ready');

    // 匹配两种结构: pre > code.language-mermaid 或 pre.language-mermaid > code
    const selectors = [
      'pre code.language-mermaid',
      'pre.language-mermaid code'
    ];
    const blocks = [];
    selectors.forEach(sel => {
      content.querySelectorAll(sel).forEach(block => {
        if (!blocks.includes(block)) blocks.push(block);
      });
    });

    console.log(`[Mermaid] found ${blocks.length} blocks to render`);

    if (blocks.length === 0) {
      // 尝试查找所有 pre 元素，看看是否有 mermaid 相关类
      const allPres = content.querySelectorAll('pre');
      console.log(`[Mermaid] total pre elements: ${allPres.length}`);
      allPres.forEach((pre, i) => {
        console.log(`[Mermaid] pre[${i}] class: "${pre.className}", code class: "${pre.querySelector('code')?.className || 'none'}"`);
      });
      return;
    }

    blocks.forEach((block, index) => {
      const pre = block.closest('pre');
      if (!pre) return;

      const id = `mermaid-${Date.now()}-${index}`;
      const graphDefinition = block.textContent.trim();
      if (!graphDefinition) return;

      console.log(`[Mermaid] rendering block ${index}, length: ${graphDefinition.length}`);

      const container = document.createElement('div');
      container.className = 'mermaid-container';
      container.id = id;

      pre.replaceWith(container);

      window.mermaid.render(id + '-svg', graphDefinition)
        .then(({ svg }) => {
          container.innerHTML = svg;
          console.log(`[Mermaid] block ${index} rendered successfully`);
        })
        .catch(err => {
          console.error('[Mermaid] render failed:', err);
          container.innerHTML = `<pre class="mermaid-error">图表渲染失败: ${err.message}</pre>`;
        });
    });
  },

  // ─── 章节导航（TOC）───────────────────────────
  buildToc() {
    const content = document.getElementById('articleContent');
    if (!content) return;

    const headings = content.querySelectorAll('h2, h3, h4');
    if (headings.length === 0) {
      this.hideToc();
      return;
    }

    this.state.tocItems = [];
    const tocNav = document.getElementById('tocNav');
    let html = '';

    headings.forEach((heading, index) => {
      const level = parseInt(heading.tagName[1]);
      const id = heading.id || `toc-heading-${index}`;
      if (!heading.id) heading.id = id;

      this.state.tocItems.push({ id, element: heading });
      html += `<div class="toc-item level-${level}" data-toc-id="${id}" onclick="App.scrollToHeading('${id}')">${escapeHtml(heading.textContent)}</div>`;
    });

    tocNav.innerHTML = html;
    document.getElementById('tocSidebar').classList.add('show');
  },

  hideToc() {
    document.getElementById('tocSidebar').classList.remove('show');
    this.state.tocItems = [];
    this.state.activeTocId = null;
  },

  scrollToHeading(id) {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  },

  // ─── 页面内搜索 ────────────────────────────────
  handleSearchKeydown(e) {
    if (e.key === 'Enter') {
      if (e.shiftKey) {
        this.prevSearchMatch();
      } else {
        this.nextSearchMatch();
      }
    } else if (e.key === 'Escape') {
      this.clearInPageSearch();
    }
  },

  nextSearchMatch() {
    const matches = this._findSearchMatches();
    if (matches.length === 0) return;

    this.state.searchMatchIndex = (this.state.searchMatchIndex || 0) + 1;
    if (this.state.searchMatchIndex >= matches.length) {
      this.state.searchMatchIndex = 0;
    }
    this._scrollToSearchMatch(matches);
  },

  prevSearchMatch() {
    const matches = this._findSearchMatches();
    if (matches.length === 0) return;

    this.state.searchMatchIndex = (this.state.searchMatchIndex || 0) - 1;
    if (this.state.searchMatchIndex < 0) {
      this.state.searchMatchIndex = matches.length - 1;
    }
    this._scrollToSearchMatch(matches);
  },

  _findSearchMatches() {
    const articleContent = document.getElementById('articleContent');
    if (!articleContent) return [];

    const searchText = document.getElementById('inPageSearchInput').value.trim();
    if (!searchText) {
      this._clearHighlights();
      document.getElementById('searchMatchCount').textContent = '';
      return [];
    }

    this._clearHighlights();

    const content = articleContent.innerHTML;
    const regex = new RegExp(`(${this._escapeRegex(searchText)})`, 'gi');
    let matches = [];

    articleContent.innerHTML = content.replace(regex, (match) => {
      matches.push(match);
      return `<mark class="search-highlight">${match}</mark>`;
    });

    matches = articleContent.querySelectorAll('.search-highlight');
    const count = matches.length;
    document.getElementById('searchMatchCount').textContent = count > 0 ? `${this.state.searchMatchIndex + 1}/${count}` : '无匹配';

    return matches;
  },

  _scrollToSearchMatch(matches) {
    if (!matches || matches.length === 0) return;

    const match = matches[this.state.searchMatchIndex];
    if (match) {
      match.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
    document.getElementById('searchMatchCount').textContent = `${this.state.searchMatchIndex + 1}/${matches.length}`;
  },

  _clearHighlights() {
    const articleContent = document.getElementById('articleContent');
    if (!articleContent) return;

    const highlights = articleContent.querySelectorAll('.search-highlight');
    highlights.forEach(h => {
      h.outerHTML = h.innerHTML;
    });
  },

  clearInPageSearch() {
    this._clearHighlights();
    const input = document.getElementById('inPageSearchInput');
    if (input) input.value = '';
    const countEl = document.getElementById('searchMatchCount');
    if (countEl) countEl.textContent = '';
    this.state.searchMatchIndex = 0;
  },

  _escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  },

  updateActiveTocOnScroll() {
    if (!this.state.tocItems.length) return;

    const scrollPos = window.scrollY + 100;
    let activeId = null;

    for (let i = this.state.tocItems.length - 1; i >= 0; i--) {
      const item = this.state.tocItems[i];
      if (item.element.offsetTop <= scrollPos) {
        activeId = item.id;
        break;
      }
    }

    if (!activeId && this.state.tocItems.length > 0) {
      activeId = this.state.tocItems[0].id;
    }

    if (activeId !== this.state.activeTocId) {
      this.state.activeTocId = activeId;
      document.querySelectorAll('.toc-item').forEach(el => {
        el.classList.toggle('active', el.dataset.tocId === activeId);
      });
    }
  },

  // ─── 渲染：文档卡片 ──────────────────────────
  renderDocCard(doc, highlightQuery = null) {
    const title = highlightQuery
      ? highlightText(doc.title, highlightQuery)
      : escapeHtml(doc.title);
    const snippet = doc.snippet
      ? highlightText(escapeHtml(doc.snippet), highlightQuery || '')
      : '';

    return `
      <div class="doc-card" onclick="App.navigate('article/${doc.id}')">
        <div class="doc-title">
          <span class="icon">${doc.icon || '📄'}</span>
          ${title}
        </div>
        <div class="doc-meta">
          <span>📂 ${doc.category_name}</span>
          <span>📝 ${this.formatNumber(doc.word_count)} 字</span>
          <span>⏱️ ${doc.read_time || 1} 分钟</span>
          <span>📅 ${doc.updated_at}</span>
        </div>
        ${snippet ? `<div class="doc-snippet">${snippet}</div>` : ''}
      </div>
    `;
  },

  // ─── 渲染：分页 ──────────────────────────────
  renderPagination() {
    const { page, totalPages } = this.state;
    if (totalPages <= 1) return;

    const container = document.getElementById('pagination');
    let html = '';

    html += `<button ${page <= 1 ? 'disabled' : ''} onclick="App.goToPage(${page - 1})">← 上一页</button>`;

    const pages = this.getPageNumbers(page, totalPages);
    for (const p of pages) {
      if (p === '...') {
        html += `<span class="page-info">...</span>`;
      } else {
        html += `<button class="${p === page ? 'active' : ''}" onclick="App.goToPage(${p})">${p}</button>`;
      }
    }

    html += `<button ${page >= totalPages ? 'disabled' : ''} onclick="App.goToPage(${page + 1})">下一页 →</button>`;

    container.innerHTML = html;
  },

  getPageNumbers(current, total) {
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
    const pages = [];
    if (current <= 4) {
      for (let i = 1; i <= 5; i++) pages.push(i);
      pages.push('...', total);
    } else if (current >= total - 3) {
      pages.push(1, '...');
      for (let i = total - 4; i <= total; i++) pages.push(i);
    } else {
      pages.push(1, '...', current - 1, current, current + 1, '...', total);
    }
    return pages;
  },

  async goToPage(page) {
    if (this.state.view === 'category') {
      await this.loadCategoryDocs(this.state.currentCategory, page);
    } else if (this.state.view === 'search') {
      await this.loadSearchResults(this.state.searchQuery, page);
    } else if (this.state.view === 'tag') {
      await this.loadTagDocs(this.state.currentCategory, page);
    } else if (this.state.view === 'trash') {
      await this.loadTrashDocs(page);
    }
    window.scrollTo(0, 0);
  },

  // ─── 渲染：侧边栏 ────────────────────────────
  renderSidebar() {
    const nav = document.getElementById('sidebarNav');
    const favCount = this.state.favorites.length;
    const trashCount = this.state.stats.trashed || 0;

    nav.innerHTML = `
      <div class="nav-section">
        <div class="nav-item ${this.state.view === 'home' ? 'active' : ''}" onclick="App.renderHome()">
          <span class="icon">🏠</span>
          <span>首页</span>
        </div>
        <div class="nav-item ${this.state.view === 'favorites' ? 'active' : ''}" onclick="App.navigate('favorites')">
          <span class="icon">⭐</span>
          <span>我的收藏</span>
          ${favCount > 0 ? `<span class="count">${favCount}</span>` : ''}
        </div>
        <div class="nav-item ${this.state.view === 'tags' ? 'active' : ''}" onclick="App.navigate('tags')">
          <span class="icon">🏷️</span>
          <span>标签云</span>
          ${this.state.tags.length > 0 ? `<span class="count">${this.state.tags.length}</span>` : ''}
        </div>
        <div class="nav-item ${this.state.view === 'trash' ? 'active' : ''}" onclick="App.navigate('trash')">
          <span class="icon">🗑️</span>
          <span>回收站</span>
          ${trashCount > 0 ? `<span class="count">${trashCount}</span>` : ''}
        </div>
      </div>
      <div class="nav-section">
        <div class="nav-section-title">分类目录</div>
        ${this.state.categories.map(cat => `
          <div class="nav-item" data-slug="${cat.slug}" onclick="App.navigate('category/${cat.slug}')">
            <span class="icon">${cat.icon}</span>
            <span>${cat.name}</span>
            <span class="count">${cat.doc_count}</span>
          </div>
        `).join('')}
      </div>
      <div class="nav-section">
        <div class="nav-section-title">数据管理</div>
        <div class="nav-item" onclick="App.exportData()">
          <span class="icon">💾</span>
          <span>导出数据</span>
        </div>
        <div class="nav-item">
          <label style="cursor:pointer;display:flex;align-items:center;gap:8px;width:100%;">
            <span class="icon">📂</span>
            <span>导入数据</span>
            <input type="file" accept=".json" style="display:none" onchange="App.importData(this.files[0])">
          </label>
        </div>
      </div>
    `;

    document.getElementById('sidebarStats').textContent =
      `${this.state.stats.total_documents || 0} 篇文档 · ${this.state.stats.total_categories || 0} 个分类`;
  },

  updateActiveNav(slug) {
    document.querySelectorAll('.nav-item').forEach(el => {
      const isHome = slug === 'home' && el.querySelector('.icon')?.textContent === '🏠';
      const isFav = slug === 'favorites' && el.querySelector('.icon')?.textContent === '⭐';
      const isTags = slug === 'tags' && el.querySelector('.icon')?.textContent === '🏷️';
      const isTrash = slug === 'trash' && el.querySelector('.icon')?.textContent === '🗑️';
      el.classList.toggle('active', el.dataset.slug === slug || isHome || isFav || isTags || isTrash);
    });
  },

  // ─── 导航 ────────────────────────────────────
  navigate(hash) {
    window.location.hash = hash;
  },

  // ─── 渲染：标签云 ────────────────────────────
  async renderTagsCloud() {
    this.state.view = 'tags';
    this.hideToc();
    document.getElementById('searchInput').value = '';

    if (!this.state.tags.length) {
      document.getElementById('content').innerHTML = `
        <div class="article-header">
          <div class="breadcrumb"><a href="#" onclick="App.renderHome(); return false;">首页</a> / 标签云</div>
          <h1>🏷️ 标签云</h1>
        </div>
        <div class="empty"><div class="empty-icon">🏷️</div><p>暂无标签</p></div>
      `;
      this.updateActiveNav('tags');
      return;
    }

    const maxCount = Math.max(...this.state.tags.map(t => t.doc_count || 0), 1);
    document.getElementById('content').innerHTML = `
      <div class="article-header">
        <div class="breadcrumb"><a href="#" onclick="App.renderHome(); return false;">首页</a> / 标签云</div>
        <h1>🏷️ 标签云</h1>
        <div class="article-meta"><span>共 ${this.state.tags.length} 个标签</span></div>
      </div>
      <div class="tags-cloud">
        ${this.state.tags.map(tag => {
          const size = 0.85 + (tag.doc_count / maxCount) * 0.5;
          return `<span class="tag-item" style="font-size:${size}rem;" onclick="App.navigate('tag/${tag.slug}')">${tag.name} <small>(${tag.doc_count})</small></span>`;
        }).join('')}
      </div>
    `;
    this.updateActiveNav('tags');
    window.scrollTo(0, 0);
  },

  // ─── 渲染：标签文档页 ────────────────────────
  async renderTag(slug) {
    this.state.view = 'tag';
    this.hideToc();
    document.getElementById('searchInput').value = '';

    const tag = this.state.tags.find(t => t.slug === slug);
    const tagName = tag ? tag.name : slug;

    document.getElementById('content').innerHTML = `
      <div class="article-header">
        <div class="breadcrumb">
          <a href="#" onclick="App.renderHome(); return false;">首页</a> /
          <a href="#" onclick="App.navigate('tags'); return false;">标签云</a> / ${tagName}
        </div>
        <h1>🏷️ ${tagName}</h1>
      </div>
      <div class="doc-list" id="tagDocs">
        ${createSkeleton('card', 3)}
      </div>
      <div class="pagination" id="pagination"></div>
    `;

    await this.loadTagDocs(slug, 1);
    this.updateActiveNav('');
    window.scrollTo(0, 0);
  },

  async loadTagDocs(slug, page) {
    const data = await this.loadTagDocuments(slug, page);
    this.state.page = page;
    this.state.totalPages = data.pages;
    this.state.totalDocs = data.total;

    const container = document.getElementById('tagDocs');
    if (!data.documents.length) {
      container.innerHTML = '<div class="empty"><div class="empty-icon">📭</div><p>该标签下暂无文档</p></div>';
      return;
    }
    container.innerHTML = data.documents.map(doc => this.renderDocCard(doc)).join('');
    this.renderPagination();
  },

  // ─── 渲染：回收站 ────────────────────────────
  async renderTrash() {
    this.state.view = 'trash';
    this.hideToc();
    document.getElementById('searchInput').value = '';

    document.getElementById('content').innerHTML = `
      <div class="article-header">
        <div class="breadcrumb"><a href="#" onclick="App.renderHome(); return false;">首页</a> / 回收站</div>
        <h1>🗑️ 回收站</h1>
      </div>
      <div class="doc-list" id="trashDocs">
        ${createSkeleton('card', 3)}
      </div>
      <div class="pagination" id="pagination"></div>
    `;

    await this.loadTrashDocs(1);
    this.updateActiveNav('trash');
    window.scrollTo(0, 0);
  },

  async loadTrashDocs(page) {
    const data = await this.loadTrash(page);
    this.state.page = page;
    this.state.totalPages = data.pages;
    this.state.totalDocs = data.total;

    const container = document.getElementById('trashDocs');
    if (!data.documents.length) {
      container.innerHTML = '<div class="empty"><div class="empty-icon">🗑️</div><p>回收站为空</p></div>';
      return;
    }
    container.innerHTML = data.documents.map(doc => `
      <div class="doc-card">
        <div class="doc-title">
          <span class="icon">${doc.icon || '📄'}</span>
          ${escapeHtml(doc.title)}
        </div>
        <div class="doc-meta">
          <span>📂 ${doc.category_name}</span>
          <span>📝 ${this.formatNumber(doc.word_count)} 字</span>
          <span>🗑️ 删除于 ${doc.deleted_at}</span>
        </div>
        <div class="doc-actions">
          <button class="btn btn-sm" onclick="App.restoreDoc(${doc.id}).then(() => App.renderTrash())">↩ 恢复</button>
          <button class="btn btn-sm btn-danger" onclick="if(confirm('确定彻底删除？')) App.purgeDoc(${doc.id}).then(() => App.renderTrash())">🗑 彻底删除</button>
        </div>
      </div>
    `).join('');
    this.renderPagination();
  },

  // ─── 工具函数 ────────────────────────────────
  formatNumber(n) {
    if (n >= 10000) return (n / 10000).toFixed(1) + 'w';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
    return n.toString();
  },

  // ─── 错误监控 ────────────────────────────────
  initErrorMonitoring() {
    window.addEventListener('error', (e) => {
      this.reportError({
        type: 'javascript',
        message: e.message,
        filename: e.filename,
        lineno: e.lineno,
        colno: e.colno,
        stack: e.error?.stack,
        url: window.location.href,
        userAgent: navigator.userAgent,
        timestamp: Date.now()
      });
    });

    window.addEventListener('unhandledrejection', (e) => {
      this.reportError({
        type: 'promise',
        message: e.reason?.message || String(e.reason),
        stack: e.reason?.stack,
        url: window.location.href,
        timestamp: Date.now()
      });
    });
  },

  reportError(errorInfo) {
    if (navigator.onLine) {
      fetch('/api/log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(errorInfo)
      }).catch(() => {});
    }
    // 同时存储到本地，稍后同步
    const errors = Storage.get('pendingErrors', []);
    errors.push(errorInfo);
    if (errors.length > 50) errors.shift();
    Storage.set('pendingErrors', errors);
  },

  // ─── 性能监控 ────────────────────────────────
  initPerformanceMonitoring() {
    if (!window.PerformanceObserver) return;

    const metrics = {};
    let reported = false;

    // FCP
    try {
      new PerformanceObserver((list) => {
        const entries = list.getEntries();
        const fcp = entries.find(e => e.name === 'first-contentful-paint');
        if (fcp) metrics.fcp = Math.round(fcp.startTime);
      }).observe({ entryTypes: ['paint'] });
    } catch (e) {}

    // LCP
    try {
      new PerformanceObserver((list) => {
        const entries = list.getEntries();
        const lastEntry = entries[entries.length - 1];
        metrics.lcp = Math.round(lastEntry.startTime);
      }).observe({ entryTypes: ['largest-contentful-paint'] });
    } catch (e) {}

    // CLS
    try {
      let cls = 0;
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (!entry.hadRecentInput) {
            cls += entry.value;
          }
        }
        metrics.cls = Math.round(cls * 1000) / 1000;
      }).observe({ entryTypes: ['layout-shift'] });
    } catch (e) {}

    // 上报函数
    const reportMetrics = () => {
      if (reported || Object.keys(metrics).length === 0) return;
      reported = true;
      
      const payload = JSON.stringify({
        ...metrics,
        url: window.location.href,
        timestamp: Date.now()
      });
      
      if (navigator.sendBeacon) {
        navigator.sendBeacon('/api/performance', payload);
      } else {
        fetch('/api/performance', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload
        }).catch(() => {});
      }
    };

    // 页面上报
    window.addEventListener('beforeunload', reportMetrics);

    // 5秒后上报（如果用户不离开页面）
    setTimeout(reportMetrics, 5000);
  },

  // ─── 阅读统计 ────────────────────────────────
  recordReadingStats(docId, duration) {
    const today = new Date().toISOString().slice(0, 10);
    const key = `reading_${today}`;
    const stats = Storage.get(key, { docs: [], totalTime: 0 });

    if (!stats.docs.includes(docId)) {
      stats.docs.push(docId);
    }
    stats.totalTime += duration;
    Storage.set(key, stats);
  },

  getWeeklyReadingStats() {
    const stats = [];
    for (let i = 6; i >= 0; i--) {
      const date = new Date();
      date.setDate(date.getDate() - i);
      const key = `reading_${date.toISOString().slice(0, 10)}`;
      const dayStats = Storage.get(key, { docs: [], totalTime: 0 });
      stats.push({
        date: date.toISOString().slice(0, 10),
        docs: dayStats.docs.length,
        minutes: Math.round(dayStats.totalTime / 60)
      });
    }
    return stats;
  },

  // ─── 搜索语法解析 ────────────────────────────
  parseSearchQuery(query) {
    const result = {
      include: [],
      exclude: [],
      phrase: [],
      category: null,
      tag: null,
      raw: query
    };

    // 精确短语 "..."
    const phraseRegex = /"([^"]+)"/g;
    let match;
    while ((match = phraseRegex.exec(query)) !== null) {
      result.phrase.push(match[1]);
    }
    query = query.replace(phraseRegex, '');

    // 分类限定 category:xxx
    const categoryMatch = query.match(/category:(\S+)/i);
    if (categoryMatch) {
      result.category = categoryMatch[1];
      query = query.replace(categoryMatch[0], '');
    }

    // 标签限定 tag:xxx
    const tagMatch = query.match(/tag:(\S+)/i);
    if (tagMatch) {
      result.tag = tagMatch[1];
      query = query.replace(tagMatch[0], '');
    }

    // 解析布尔运算符
    const tokens = query.trim().split(/\s+/);
    for (let i = 0; i < tokens.length; i++) {
      if (tokens[i].toUpperCase() === 'NOT' && i + 1 < tokens.length) {
        result.exclude.push(tokens[i + 1]);
        i++;
      } else if (tokens[i] && tokens[i].toUpperCase() !== 'AND') {
        result.include.push(tokens[i]);
      }
    }

    return result;
  },
};

// ─── 启动 ──────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => App.init());

// 暴露到全局（供 HTML 内联事件使用）
window.App = App;
