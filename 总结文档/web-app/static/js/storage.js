/**
 * 本地存储封装模块 - v1.1
 * 提供命名空间、版本控制和数据导出导入功能
 */

const STORAGE_KEY = 'docblog_v1';
const STORAGE_VERSION = 2;

export const Storage = {
  /**
   * 获取存储值
   * @param {string} key - 键名
   * @param {*} defaultValue - 默认值
   * @returns {*}
   */
  get(key, defaultValue = null) {
    try {
      const item = localStorage.getItem(`${STORAGE_KEY}_${key}`);
      if (item === null) return defaultValue;
      return JSON.parse(item);
    } catch {
      return defaultValue;
    }
  },

  /**
   * 设置存储值
   * @param {string} key - 键名
   * @param {*} value - 值
   */
  set(key, value) {
    localStorage.setItem(`${STORAGE_KEY}_${key}`, JSON.stringify(value));
  },

  /**
   * 删除存储值
   * @param {string} key - 键名
   */
  remove(key) {
    localStorage.removeItem(`${STORAGE_KEY}_${key}`);
  },

  /**
   * 导出所有数据
   * @returns {Object} 包含版本和时间戳的数据对象
   */
  export() {
    const data = {};
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(STORAGE_KEY)) {
        data[key] = localStorage.getItem(key);
      }
    }
    return {
      version: STORAGE_VERSION,
      timestamp: Date.now(),
      app: 'docblog',
      data
    };
  },

  /**
   * 导入数据
   * @param {Object} backup - 备份数据对象
   * @throws {Error} 版本不兼容时抛出错误
   */
  import(backup) {
    if (!backup || !backup.data) {
      throw new Error('无效的备份文件');
    }
    if (backup.version > STORAGE_VERSION) {
      throw new Error(`备份版本 ${backup.version} 高于当前版本 ${STORAGE_VERSION}`);
    }
    Object.entries(backup.data).forEach(([key, value]) => {
      localStorage.setItem(key, value);
    });
  },

  /**
   * 清空所有应用数据
   */
  clear() {
    const keysToRemove = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(STORAGE_KEY)) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach(key => localStorage.removeItem(key));
  }
};

// 兼容旧版本数据迁移
function migrateFromV1() {
  const oldKeys = ['doc-theme', 'doc-font-size', 'doc-favorites', 'doc-search-history'];
  const hasOldData = oldKeys.some(key => localStorage.getItem(key) !== null);
  if (!hasOldData) return;

  // 迁移主题
  const theme = localStorage.getItem('doc-theme');
  if (theme) Storage.set('theme', theme);

  // 迁移字体大小
  const fontSize = localStorage.getItem('doc-font-size');
  if (fontSize) Storage.set('fontSize', parseFloat(fontSize));

  // 迁移收藏
  const favorites = localStorage.getItem('doc-favorites');
  if (favorites) Storage.set('favorites', JSON.parse(favorites));

  // 迁移搜索历史
  const history = localStorage.getItem('doc-search-history');
  if (history) Storage.set('searchHistory', JSON.parse(history));

  // 删除旧数据
  oldKeys.forEach(key => localStorage.removeItem(key));
}

// 自动执行迁移
migrateFromV1();
