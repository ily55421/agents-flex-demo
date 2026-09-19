/**
 * UI 组件模块 - v1.1
 * 提供 Toast、Modal、Skeleton 等通用组件
 */

import { escapeHtml } from './utils.js';

// ─── Toast 轻提示 ────────────────────────────

let toastContainer = null;

function getToastContainer() {
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.className = 'toast-container';
    document.body.appendChild(toastContainer);
  }
  return toastContainer;
}

/**
 * 显示 Toast 提示
 * @param {string} message - 提示内容
 * @param {string} type - 类型: success | error | warning | info
 * @param {number} duration - 显示时长（毫秒）
 */
export function showToast(message, type = 'info', duration = 3000) {
  const container = getToastContainer();
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;

  const icons = {
    success: '✓',
    error: '✕',
    warning: '⚠',
    info: 'ℹ'
  };

  toast.innerHTML = `
    <span class="toast-icon">${icons[type] || icons.info}</span>
    <span class="toast-message">${escapeHtml(message)}</span>
  `;

  container.appendChild(toast);

  // 动画进入
  requestAnimationFrame(() => {
    toast.classList.add('show');
  });

  // 自动关闭
  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// ─── Modal 弹窗 ──────────────────────────────

let activeModal = null;

/**
 * 显示 Modal 弹窗
 * @param {Object} options - 配置选项
 * @param {string} options.title - 标题
 * @param {string} options.content - 内容（HTML）
 * @param {string} options.size - 尺寸: sm | md | lg
 * @param {boolean} options.showClose - 是否显示关闭按钮
 * @returns {Object} { close: Function }
 */
export function showModal(options = {}) {
  const { title = '', content = '', size = 'md', showClose = true } = options;

  // 关闭已存在的弹窗
  if (activeModal) activeModal.close();

  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';

  const modal = document.createElement('div');
  modal.className = `modal modal-${size}`;

  modal.innerHTML = `
    ${showClose ? '<button class="modal-close">&times;</button>' : ''}
    ${title ? `<div class="modal-header"><h3>${escapeHtml(title)}</h3></div>` : ''}
    <div class="modal-body">${content}</div>
  `;

  overlay.appendChild(modal);
  document.body.appendChild(overlay);

  // 防止背景滚动
  document.body.style.overflow = 'hidden';

  // 动画
  requestAnimationFrame(() => {
    overlay.classList.add('show');
    modal.classList.add('show');
  });

  const close = () => {
    overlay.classList.remove('show');
    modal.classList.remove('show');
    setTimeout(() => {
      overlay.remove();
      document.body.style.overflow = '';
      if (activeModal === modalObj) activeModal = null;
    }, 300);
  };

  // 事件绑定
  if (showClose) {
    modal.querySelector('.modal-close').addEventListener('click', close);
  }
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) close();
  });

  const modalObj = { close, overlay, modal };
  activeModal = modalObj;

  return modalObj;
}

/**
 * 显示确认对话框
 * @param {string} message - 确认消息
 * @param {string} confirmText - 确认按钮文字
 * @param {string} cancelText - 取消按钮文字
 * @returns {Promise<boolean>}
 */
export function confirmDialog(message, confirmText = '确认', cancelText = '取消') {
  return new Promise((resolve) => {
    const content = `
      <p class="confirm-message">${escapeHtml(message)}</p>
      <div class="confirm-actions">
        <button class="btn btn-secondary" id="confirmCancel">${escapeHtml(cancelText)}</button>
        <button class="btn btn-primary" id="confirmOk">${escapeHtml(confirmText)}</button>
      </div>
    `;

    const modal = showModal({ content, showClose: false, size: 'sm' });

    modal.modal.querySelector('#confirmCancel').addEventListener('click', () => {
      modal.close();
      resolve(false);
    });
    modal.modal.querySelector('#confirmOk').addEventListener('click', () => {
      modal.close();
      resolve(true);
    });
  });
}

// ─── Skeleton 骨架屏 ─────────────────────────

/**
 * 创建骨架屏元素
 * @param {string} type - 类型: text | title | card | list
 * @param {number} count - 数量
 * @returns {string} HTML 字符串
 */
export function createSkeleton(type = 'text', count = 1) {
  const items = [];
  for (let i = 0; i < count; i++) {
    items.push(`<div class="skeleton skeleton-${type}"></div>`);
  }
  return items.join('');
}

/**
 * 显示加载状态
 * @param {Element} container - 容器元素
 * @param {string} skeletonType - 骨架屏类型
 * @param {number} count - 数量
 */
export function showLoading(container, skeletonType = 'text', count = 3) {
  if (!container) return;
  container.innerHTML = `<div class="skeleton-wrapper">${createSkeleton(skeletonType, count)}</div>`;
}

/**
 * 隐藏加载状态
 * @param {Element} container - 容器元素
 */
export function hideLoading(container) {
  if (!container) return;
  const wrapper = container.querySelector('.skeleton-wrapper');
  if (wrapper) wrapper.remove();
}

// ─── Dropdown 下拉菜单 ───────────────────────

/**
 * 创建下拉菜单
 * @param {Element} trigger - 触发元素
 * @param {Array} items - 菜单项 [{text, icon, action, divider}]
 * @returns {Object} { open, close, destroy }
 */
export function createDropdown(trigger, items) {
  let menu = null;
  let isOpen = false;

  function open() {
    if (isOpen) return;
    closeAllDropdowns();

    menu = document.createElement('div');
    menu.className = 'dropdown-menu';

    items.forEach(item => {
      if (item.divider) {
        menu.appendChild(document.createElement('hr'));
        return;
      }
      const el = document.createElement('button');
      el.className = 'dropdown-item';
      el.innerHTML = `${item.icon || ''} ${escapeHtml(item.text)}`;
      el.addEventListener('click', (e) => {
        e.stopPropagation();
        if (item.action) item.action();
        close();
      });
      menu.appendChild(el);
    });

    document.body.appendChild(menu);

    // 定位
    const rect = trigger.getBoundingClientRect();
    menu.style.top = `${rect.bottom + 4}px`;
    menu.style.left = `${rect.left}px`;

    requestAnimationFrame(() => menu.classList.add('show'));
    isOpen = true;
  }

  function close() {
    if (!isOpen || !menu) return;
    menu.classList.remove('show');
    setTimeout(() => {
      if (menu) menu.remove();
      menu = null;
    }, 200);
    isOpen = false;
  }

  function destroy() {
    close();
    trigger.removeEventListener('click', toggle);
    document.removeEventListener('click', onDocClick);
  }

  function toggle(e) {
    e.stopPropagation();
    isOpen ? close() : open();
  }

  function onDocClick(e) {
    if (menu && !menu.contains(e.target) && e.target !== trigger) {
      close();
    }
  }

  trigger.addEventListener('click', toggle);
  document.addEventListener('click', onDocClick);

  return { open, close, destroy };
}

function closeAllDropdowns() {
  document.querySelectorAll('.dropdown-menu.show').forEach(menu => {
    menu.classList.remove('show');
    setTimeout(() => menu.remove(), 200);
  });
}
