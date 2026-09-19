/**
 * 工具函数模块 - v1.1
 */

/**
 * 防抖函数
 * @param {Function} fn - 要执行的函数
 * @param {number} delay - 延迟时间（毫秒）
 * @returns {Function}
 */
export function debounce(fn, delay = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}

/**
 * 节流函数
 * @param {Function} fn - 要执行的函数
 * @param {number} limit - 限制时间（毫秒）
 * @returns {Function}
 */
export function throttle(fn, limit = 100) {
  let inThrottle;
  return (...args) => {
    if (!inThrottle) {
      fn.apply(this, args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  };
}

/**
 * 格式化日期
 * @param {string} dateStr - 日期字符串
 * @returns {string}
 */
export function formatDate(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
}

/**
 * 转义 HTML 特殊字符
 * @param {string} text - 原始文本
 * @returns {string}
 */
export function escapeHtml(text) {
  if (!text) return '';
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * 高亮搜索关键词
 * @param {string} text - 原始文本
 * @param {string} query - 搜索关键词
 * @returns {string}
 */
export function highlightText(text, query) {
  if (!query || !text) return escapeHtml(text);
  const terms = query.split(/\s+/).filter(t => t.length > 0);
  let result = escapeHtml(text);
  terms.forEach(term => {
    const regex = new RegExp(`(${escapeRegExp(term)})`, 'gi');
    result = result.replace(regex, '<mark>$1</mark>');
  });
  return result;
}

/**
 * 转义正则表达式特殊字符
 * @param {string} str - 原始字符串
 * @returns {string}
 */
function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * 计算阅读时间
 * @param {number} wordCount - 字数
 * @param {number} wpm - 每分钟阅读字数（默认300）
 * @returns {number} 阅读分钟数
 */
export function readingTime(wordCount, wpm = 300) {
  return Math.max(1, Math.ceil(wordCount / wpm));
}

/**
 * 下载 JSON 文件
 * @param {Object} data - 要下载的数据
 * @param {string} filename - 文件名
 */
export function downloadJSON(data, filename) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * 读取文件内容为 JSON
 * @param {File} file - 文件对象
 * @returns {Promise<Object>}
 */
export function readJSONFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        resolve(JSON.parse(e.target.result));
      } catch (err) {
        reject(new Error('无效的 JSON 文件'));
      }
    };
    reader.onerror = () => reject(new Error('读取文件失败'));
    reader.readAsText(file);
  });
}

/**
 * 平滑滚动到元素
 * @param {Element} element - 目标元素
 * @param {number} offset - 偏移量
 */
export function scrollToElement(element, offset = 80) {
  if (!element) return;
  const top = element.getBoundingClientRect().top + window.pageYOffset - offset;
  window.scrollTo({ top, behavior: 'smooth' });
}

/**
 * 监听元素进入视口
 * @param {Element} element - 目标元素
 * @param {Function} callback - 回调函数
 * @param {Object} options - IntersectionObserver 选项
 */
export function onVisible(element, callback, options = {}) {
  if (!element || !window.IntersectionObserver) {
    callback();
    return;
  }
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        callback();
        observer.disconnect();
      }
    });
  }, { threshold: 0.1, ...options });
  observer.observe(element);
}
