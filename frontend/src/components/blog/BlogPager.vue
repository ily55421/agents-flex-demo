<script setup lang="ts">
/**
 * 通用分页条：上一页 / 数字页 / 省略号 / 下一页。
 *
 * 页码窗口自行计算而不引组件库：总页数多时只渲染「首尾 + 当前页附近的窗口」，
 * 避免 1000 页文档撑出上百个按钮；窗口算法放在这里，BlogPanel 只关心 current 与 pages。
 */
import {computed} from 'vue'
import {IconChevronLeft, IconChevronRight} from '@tabler/icons-vue'

const props = defineProps<{
  /** 当前页（从 1 开始）。 */
  page: number
  /** 总页数，后端保证最小为 1。 */
  pages: number
}>()

const emit = defineEmits<{(event: 'change', page: number): void}>()

/** 省略号两侧的页码窗口宽度（不含首尾页）。 */
const WINDOW = 5

type PagerItem = {kind: 'page'; value: number} | {kind: 'gap'; key: string}

const items = computed<PagerItem[]>(() => {
  const total = Math.max(1, props.pages)
  const current = Math.min(Math.max(1, props.page), total)
  if (total <= WINDOW + 2) {
    return Array.from({length: total}, (_, index) => ({kind: 'page' as const, value: index + 1}))
  }
  // 先按当前页居中取窗口，再夹到 [2, total-1]，保证首尾页始终单独出现
  let start = Math.max(2, current - Math.floor(WINDOW / 2))
  let end = Math.min(total - 1, start + WINDOW - 1)
  start = Math.max(2, end - WINDOW + 1)
  const result: PagerItem[] = [{kind: 'page', value: 1}]
  if (start > 2) result.push({kind: 'gap', key: 'head'})
  for (let value = start; value <= end; value++) result.push({kind: 'page', value})
  if (end < total - 1) result.push({kind: 'gap', key: 'tail'})
  result.push({kind: 'page', value: total})
  return result
})

/** 越界或重复点击同一页时不发事件，避免无意义的重复请求。 */
function go(target: number) {
  const total = Math.max(1, props.pages)
  const next = Math.min(Math.max(1, target), total)
  if (next === props.page) return
  emit('change', next)
}
</script>

<template>
  <nav v-if="pages > 1" class="blog-pager" aria-label="分页导航">
    <button type="button" class="blog-pager-step" :disabled="page <= 1" @click="go(page - 1)">
      <IconChevronLeft :size="14" aria-hidden="true"/>上一页
    </button>
    <template v-for="item in items" :key="item.kind === 'page' ? `p${item.value}` : item.key">
      <button v-if="item.kind === 'page'" type="button" class="blog-pager-page"
              :class="{active: item.value === page}"
              :aria-current="item.value === page ? 'page' : undefined"
              @click="go(item.value)">{{ item.value }}</button>
      <span v-else class="blog-pager-gap" aria-hidden="true">…</span>
    </template>
    <button type="button" class="blog-pager-step" :disabled="page >= pages" @click="go(page + 1)">
      下一页<IconChevronRight :size="14" aria-hidden="true"/>
    </button>
    <span class="blog-pager-total">共 {{ pages }} 页</span>
  </nav>
</template>

<style scoped>
.blog-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-5);
}

.blog-pager-step,
.blog-pager-page {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 32px;
  height: 30px;
  padding: 0 var(--space-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-soft);
  font-size: var(--font-small);
}

.blog-pager-step:hover:not(:disabled),
.blog-pager-page:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.blog-pager-page.active {
  border-color: var(--accent);
  background: var(--accent);
  color: #ffffff;
  font-weight: 700;
}

.blog-pager-gap {
  min-width: 18px;
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-small);
}

.blog-pager-total {
  margin-left: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

@media (max-width: 640px) {
  .blog-pager-total { display: none; }
}
</style>
