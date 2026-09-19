/**
 * 知识图谱可视化组件
 * 使用 Canvas 绘制力导向图
 */

export class KnowledgeGraph {
  constructor(containerId, options = {}) {
    this.container = document.getElementById(containerId);
    if (!this.container) return;
    
    this.width = options.width || this.container.clientWidth || 800;
    this.height = options.height || 400;
    this.nodeRadius = options.nodeRadius || 20;
    this.centerRadius = options.centerRadius || 30;
    this.colors = {
      center: '#6c8cff',
      same_category: 'rgba(108, 140, 255, 0.8)',
      similar: 'rgba(250, 204, 21, 0.8)',
      shared_tag: 'rgba(34, 197, 94, 0.8)',
      reference: 'rgba(249, 115, 22, 0.8)',
      edge: 'rgba(148, 163, 184, 0.3)',
      text: getComputedStyle(document.body).getPropertyValue('--text').trim() || '#e2e8f0'
    };
    
    this.nodes = [];
    this.edges = [];
    this.animationId = null;
    this.hoveredNode = null;
    this.onNodeClick = options.onNodeClick || (() => {});
    
    this.init();
  }
  
  init() {
    // 创建 Canvas
    this.canvas = document.createElement('canvas');
    this.canvas.width = this.width;
    this.canvas.height = this.height;
    this.canvas.style.width = '100%';
    this.canvas.style.height = '100%';
    this.canvas.style.cursor = 'grab';
    this.container.appendChild(this.canvas);
    
    this.ctx = this.canvas.getContext('2d');
    
    // 事件监听
    this.canvas.addEventListener('mousemove', this.handleMouseMove.bind(this));
    this.canvas.addEventListener('click', this.handleClick.bind(this));
    this.canvas.addEventListener('mouseleave', () => {
      this.hoveredNode = null;
      this.canvas.style.cursor = 'grab';
    });
    
    // 响应式
    this.resizeObserver = new ResizeObserver(() => {
      this.width = this.container.clientWidth || 800;
      this.height = this.container.clientHeight || 400;
      this.canvas.width = this.width;
      this.canvas.height = this.height;
      if (this.nodes.length) this.layout();
    });
    this.resizeObserver.observe(this.container);
  }
  
  setData(data) {
    this.nodes = data.nodes || [];
    this.edges = data.edges || [];
    this.centerNode = data.center;
    
    // 初始化位置
    const cx = this.width / 2;
    const cy = this.height / 2;
    
    this.nodes.forEach((node, i) => {
      if (node.id === this.centerNode.id) {
        node.x = cx;
        node.y = cy;
        node.vx = 0;
        node.vy = 0;
      } else {
        const angle = (i / this.nodes.length) * Math.PI * 2;
        const dist = 100 + Math.random() * 50;
        node.x = cx + Math.cos(angle) * dist;
        node.y = cy + Math.sin(angle) * dist;
        node.vx = 0;
        node.vy = 0;
      }
    });
    
    this.startAnimation();
  }
  
  layout() {
    const cx = this.width / 2;
    const cy = this.height / 2;
    
    // 力导向算法
    const k = 150; // 理想边长
    const repulsion = 5000;
    const spring = 0.05;
    const damping = 0.9;
    const centerGravity = 0.01;
    
    // 计算力
    for (let i = 0; i < this.nodes.length; i++) {
      const nodeA = this.nodes[i];
      if (nodeA.id === this.centerNode?.id) continue; // 中心节点固定
      
      let fx = 0, fy = 0;
      
      // 斥力
      for (let j = 0; j < this.nodes.length; j++) {
        if (i === j) continue;
        const nodeB = this.nodes[j];
        const dx = nodeA.x - nodeB.x;
        const dy = nodeA.y - nodeB.y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const force = repulsion / (dist * dist);
        fx += (dx / dist) * force;
        fy += (dy / dist) * force;
      }
      
      // 引力（边）
      for (const edge of this.edges) {
        let other = null;
        if (edge.source === nodeA.id) other = this.nodes.find(n => n.id === edge.target);
        else if (edge.target === nodeA.id) other = this.nodes.find(n => n.id === edge.source);
        
        if (other) {
          const dx = other.x - nodeA.x;
          const dy = other.y - nodeA.y;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          const force = (dist - k) * spring * (edge.weight || 0.5);
          fx += (dx / dist) * force;
          fy += (dy / dist) * force;
        }
      }
      
      // 中心引力
      fx += (cx - nodeA.x) * centerGravity;
      fy += (cy - nodeA.y) * centerGravity;
      
      // 应用力
      nodeA.vx = (nodeA.vx + fx) * damping;
      nodeA.vy = (nodeA.vy + fy) * damping;
    }
    
    // 更新位置
    for (const node of this.nodes) {
      if (node.id === this.centerNode?.id) continue;
      node.x += node.vx;
      node.y += node.vy;
      
      // 边界限制
      const r = node.id === this.centerNode?.id ? this.centerRadius : this.nodeRadius;
      node.x = Math.max(r, Math.min(this.width - r, node.x));
      node.y = Math.max(r, Math.min(this.height - r, node.y));
    }
  }
  
  draw() {
    this.ctx.clearRect(0, 0, this.width, this.height);
    
    // 绘制边
    for (const edge of this.edges) {
      const source = this.nodes.find(n => n.id === edge.source);
      const target = this.nodes.find(n => n.id === edge.target);
      if (!source || !target) continue;
      
      this.ctx.beginPath();
      this.ctx.moveTo(source.x, source.y);
      this.ctx.lineTo(target.x, target.y);
      this.ctx.strokeStyle = this.colors.edge;
      this.ctx.lineWidth = (edge.weight || 0.5) * 3;
      this.ctx.stroke();
      
      // 关系类型标签
      const mx = (source.x + target.x) / 2;
      const my = (source.y + target.y) / 2;
      this.ctx.fillStyle = 'rgba(148, 163, 184, 0.6)';
      this.ctx.font = '10px sans-serif';
      this.ctx.textAlign = 'center';
      const labels = {
        'same_category': '同分类',
        'similar': '相似',
        'shared_tag': '同标签',
        'reference': '引用'
      };
      this.ctx.fillText(labels[edge.type] || edge.type, mx, my - 2);
    }
    
    // 绘制节点
    for (const node of this.nodes) {
      const isCenter = node.id === this.centerNode?.id;
      const r = isCenter ? this.centerRadius : this.nodeRadius;
      const isHovered = this.hoveredNode?.id === node.id;
      
      // 节点圆形
      this.ctx.beginPath();
      this.ctx.arc(node.x, node.y, r + (isHovered ? 3 : 0), 0, Math.PI * 2);
      
      // 颜色
      let color = this.colors.similar;
      if (isCenter) color = this.colors.center;
      else if (node.category_slug) {
        // 根据分类生成颜色
        const hue = this.hashString(node.category_slug) % 360;
        color = `hsla(${hue}, 70%, 60%, 0.8)`;
      }
      
      this.ctx.fillStyle = color;
      this.ctx.fill();
      
      // 边框
      this.ctx.strokeStyle = isHovered ? '#fff' : 'rgba(255,255,255,0.3)';
      this.ctx.lineWidth = isHovered ? 3 : 1;
      this.ctx.stroke();
      
      // 图标
      this.ctx.fillStyle = '#fff';
      this.ctx.font = `${isCenter ? 20 : 14}px sans-serif`;
      this.ctx.textAlign = 'center';
      this.ctx.textBaseline = 'middle';
      this.ctx.fillText(node.icon || '📄', node.x, node.y);
      
      // 标题
      if (isCenter || isHovered) {
        this.ctx.fillStyle = this.colors.text;
        this.ctx.font = `${isCenter ? 14 : 11}px sans-serif`;
        this.ctx.textAlign = 'center';
        const title = this.truncateText(node.title, isCenter ? 20 : 12);
        this.ctx.fillText(title, node.x, node.y + r + 15);
      }
    }
  }
  
  startAnimation() {
    if (this.animationId) cancelAnimationFrame(this.animationId);
    
    let frame = 0;
    const animate = () => {
      this.layout();
      this.draw();
      frame++;
      if (frame < 300) { // 动画300帧后停止
        this.animationId = requestAnimationFrame(animate);
      }
    };
    animate();
  }
  
  handleMouseMove(e) {
    const rect = this.canvas.getBoundingClientRect();
    const x = (e.clientX - rect.left) * (this.canvas.width / rect.width);
    const y = (e.clientY - rect.top) * (this.canvas.height / rect.height);
    
    let found = null;
    for (const node of this.nodes) {
      const r = node.id === this.centerNode?.id ? this.centerRadius : this.nodeRadius;
      const dx = x - node.x;
      const dy = y - node.y;
      if (dx * dx + dy * dy < r * r) {
        found = node;
        break;
      }
    }
    
    this.hoveredNode = found;
    this.canvas.style.cursor = found ? 'pointer' : 'grab';
    this.draw();
  }
  
  handleClick(e) {
    if (this.hoveredNode) {
      this.onNodeClick(this.hoveredNode);
    }
  }
  
  hashString(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash;
    }
    return Math.abs(hash);
  }
  
  truncateText(text, maxLen) {
    if (!text) return '';
    if (text.length <= maxLen) return text;
    return text.substring(0, maxLen) + '...';
  }
  
  destroy() {
    if (this.animationId) cancelAnimationFrame(this.animationId);
    if (this.resizeObserver) this.resizeObserver.disconnect();
    if (this.canvas) this.canvas.remove();
  }
}
