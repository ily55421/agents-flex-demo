/**
 * 主题管理模块 - v1.1
 */

import { Storage } from './storage.js';

const THEME_KEY = 'theme';

export const Theme = {
  current: 'dark',

  /**
   * 初始化主题
   */
  init() {
    const saved = Storage.get(THEME_KEY);
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    this.current = saved || (prefersDark ? 'dark' : 'light');
    this.apply();
  },

  /**
   * 应用当前主题
   */
  apply() {
    document.documentElement.setAttribute('data-theme', this.current);
    Storage.set(THEME_KEY, this.current);

    // 同步 Prism 主题
    const prismLink = document.getElementById('prismTheme');
    if (prismLink) {
      // 可扩展支持亮色主题
      prismLink.href = '/css/prism-tomorrow.css';
    }
  },

  /**
   * 切换主题
   */
  toggle() {
    this.current = this.current === 'dark' ? 'light' : 'dark';
    this.apply();
  },

  /**
   * 设置指定主题
   * @param {string} theme - 'dark' | 'light'
   */
  set(theme) {
    if (theme !== 'dark' && theme !== 'light') return;
    this.current = theme;
    this.apply();
  },

  /**
   * 监听系统主题变化
   * @param {Function} callback - 回调函数
   */
  onSystemChange(callback) {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    mq.addEventListener('change', (e) => {
      callback(e.matches ? 'dark' : 'light');
    });
  }
};
