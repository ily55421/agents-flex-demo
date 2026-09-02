<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import {IconHelpCircle} from '@tabler/icons-vue'

/**
 * 配置字段问号提示图标。
 * 提示内容通过 Teleport 放到 body，脱离左侧滚动栏的裁剪上下文；显示时根据图标位置
 * 自动选择下方或上方，并把横向位置限制在视口内，避免长说明被面板或浏览器边缘遮挡。
 */
const props = defineProps<{
  label: string
  help: string
}>()

const trigger = ref<HTMLElement | null>(null)
const popover = ref<HTMLElement | null>(null)
const visible = ref(false)
const position = ref({left: 0, top: 0})
const placement = ref<'above' | 'below'>('below')
let hideTimer: number | undefined

const tooltipId = `field-help-${Math.random().toString(36).slice(2, 10)}`
const popoverStyle = computed(() => ({
  left: `${position.value.left}px`,
  top: `${position.value.top}px`,
}))

/** 根据触发图标和提示层尺寸计算一个不会超出视口的固定定位。 */
function updatePosition() {
  if (!trigger.value || !popover.value) return
  const anchor = trigger.value.getBoundingClientRect()
  const panel = popover.value.getBoundingClientRect()
  const gap = 8
  const edge = 10
  const left = Math.min(Math.max(edge, anchor.left), Math.max(edge, window.innerWidth - panel.width - edge))
  const below = anchor.bottom + gap
  const above = anchor.top - panel.height - gap
  const fitsBelow = below + panel.height <= window.innerHeight - edge || above < edge
  placement.value = fitsBelow ? 'below' : 'above'
  const top = fitsBelow ? below : above
  position.value = {left, top: Math.max(edge, top)}
}

/** 显示提示并在 DOM 更新后测量提示层尺寸。 */
async function show() {
  if (hideTimer) window.clearTimeout(hideTimer)
  visible.value = true
  await nextTick()
  updatePosition()
}

/** 延迟隐藏，允许鼠标从图标移动到 Teleport 后的提示层。 */
function hideSoon() {
  if (hideTimer) window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => {
    visible.value = false
  }, 140)
}

/** 鼠标进入提示层时取消待隐藏任务。 */
function cancelHide() {
  if (hideTimer) window.clearTimeout(hideTimer)
}

function onViewportChange() {
  if (visible.value) updatePosition()
}

onMounted(() => {
  window.addEventListener('resize', onViewportChange)
  window.addEventListener('scroll', onViewportChange, true)
})

onBeforeUnmount(() => {
  if (hideTimer) window.clearTimeout(hideTimer)
  window.removeEventListener('resize', onViewportChange)
  window.removeEventListener('scroll', onViewportChange, true)
})
</script>

<template>
  <span
    ref="trigger"
    class="field-help"
    tabindex="0"
    role="img"
    :aria-label="`${label}：${help}`"
    :aria-describedby="tooltipId"
    @mouseenter="show"
    @mouseleave="hideSoon"
    @focusin="show"
    @focusout="hideSoon"
  >
    <IconHelpCircle :size="15" :stroke-width="1.8" aria-hidden="true"/>
  </span>
  <Teleport to="body">
    <span
      v-if="visible"
      ref="popover"
      :id="tooltipId"
      class="field-help-popover"
      role="tooltip"
      :data-placement="placement"
      :style="popoverStyle"
      @mouseenter="cancelHide"
      @mouseleave="hideSoon"
    >{{ help }}</span>
  </Teleport>
</template>
