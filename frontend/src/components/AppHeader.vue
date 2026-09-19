<script setup lang="ts">
import {computed} from 'vue'
import {IconBrandOpenSource} from '@tabler/icons-vue'
import type {AgentRun} from '@/types/agent'

const props = defineProps<{
  run: AgentRun | null
  connectionState: 'idle' | 'connecting' | 'live' | 'reconnecting' | 'archived'
}>()

/** 将内部 SSE 连接状态翻译为头部展示的中文状态。 */
const connectionLabel = computed(() => ({
  idle: '未连接',
  connecting: '正在连接',
  live: 'SSE 实时',
  reconnecting: '正在重连',
  archived: '只读归档',
}[props.connectionState]))
</script>

<template>
  <header class="app-header">
    <div class="brand-lockup">
      <div class="brand-mark" aria-hidden="true">
        <IconBrandOpenSource :size="22" :stroke-width="1.8"/>
      </div>
      <div>
        <div class="brand-name">Agents-Flex</div>
        <div class="brand-product">Agent 运行时演示</div>
      </div>
    </div>

    <div class="header-meta">
      <div v-if="run" class="run-reference">
        <span>运行</span>
        <code>{{ run.runId.slice(0, 8) }}</code>
      </div>
      <div class="connection-indicator" :data-state="connectionState" role="status">
        <span class="semantic-dot" aria-hidden="true"/>
        {{ connectionLabel }}
      </div>
    </div>
  </header>
</template>
