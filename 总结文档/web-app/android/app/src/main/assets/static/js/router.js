/**
 * 路由管理模块 - v1.1
 * 支持 hash 路由和导航历史
 */

export const Router = {
  routes: new Map(),
  beforeHooks: [],
  afterHooks: [],

  /**
   * 注册路由
   * @param {string} path - 路由路径
   * @param {Function} handler - 处理函数
   */
  register(path, handler) {
    this.routes.set(path, handler);
  },

  /**
   * 导航到指定路径
   * @param {string} path - 目标路径
   */
  navigate(path) {
    window.location.hash = path;
  },

  /**
   * 返回上一页
   */
  back() {
    window.history.back();
  },

  /**
   * 获取当前路由路径
   * @returns {string}
   */
  current() {
    return window.location.hash.slice(1) || '/';
  },

  /**
   * 解析路由参数
   * @param {string} path - 路径
   * @returns {Object} { path, query }
   */
  parse(path) {
    const [routePath, queryStr] = path.split('?');
    const query = {};
    if (queryStr) {
      queryStr.split('&').forEach(pair => {
        const [key, value] = pair.split('=');
        if (key) query[decodeURIComponent(key)] = decodeURIComponent(value || '');
      });
    }
    return { path: routePath, query };
  },

  /**
   * 处理当前路由
   */
  handle() {
    const fullPath = this.current();
    const { path, query } = this.parse(fullPath);

    // 执行前置钩子
    for (const hook of this.beforeHooks) {
      const result = hook(path, query);
      if (result === false) return;
    }

    // 查找匹配的路由
    let handler = this.routes.get(path);
    let params = [];

    // 支持动态路由匹配
    if (!handler) {
      for (const [routePath, routeHandler] of this.routes) {
        const matchResult = this.match(routePath, path);
        if (matchResult) {
          handler = routeHandler;
          params = matchResult;
          break;
        }
      }
    }

    // 默认路由
    if (!handler) {
      handler = this.routes.get('/') || this.routes.get('default');
    }

    if (handler) {
      handler(query, params);
    }

    // 执行后置钩子
    this.afterHooks.forEach(hook => hook(path, query));
  },

  /**
   * 路由匹配
   * @param {string} pattern - 路由模式
   * @param {string} path - 实际路径
   * @returns {Array|null} 匹配成功返回参数数组，失败返回 null
   */
  match(pattern, path) {
    // 简单实现，支持 :param 格式
    const regex = new RegExp('^' + pattern.replace(/:([^/]+)/g, '([^/]+)') + '$');
    const match = path.match(regex);
    if (match) {
      return match.slice(1); // 返回捕获组
    }
    return null;
  },

  /**
   * 添加前置钩子
   * @param {Function} fn - 钩子函数
   */
  beforeEach(fn) {
    this.beforeHooks.push(fn);
  },

  /**
   * 添加后置钩子
   * @param {Function} fn - 钩子函数
   */
  afterEach(fn) {
    this.afterHooks.push(fn);
  },

  /**
   * 初始化路由监听
   */
  init() {
    window.addEventListener('hashchange', () => this.handle());
    window.addEventListener('load', () => this.handle());
  }
};
