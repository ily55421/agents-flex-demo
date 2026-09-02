<script setup lang="ts">
import ConfigHelpIcon from '@/components/ConfigHelpIcon.vue'

/**
 * 配置字段标题的统一展示组件。
 * 图标同时支持鼠标悬停和键盘聚焦，帮助内容使用 aria-describedby 关联到标题，
 * 让鼠标用户和屏幕阅读器用户都能理解该配置的作用与适用场景。
 */
defineProps<{
  forId: string
  text: string
  help: string
}>()
</script>

<template>
  <label class="config-field-label" :for="forId">
    <span>{{ text }}</span>
    <ConfigHelpIcon :label="text" :help="help"/>
  </label>
</template>
