<script setup lang="ts">
import { ref } from 'vue'
import { IconCheck, IconShieldExclamation, IconX } from '@tabler/icons-vue'
import type { Suspension, ToolCallView } from '@/types/agent'

defineProps<{ suspension: Suspension; toolCall?: ToolCallView; busy: boolean }>()
const emit = defineEmits<{ decide: [approved: boolean, reason?: string] }>()
const reason = ref('该报告仍需内部复核')
</script>

<template>
  <section class="interaction-panel approval-panel" aria-labelledby="approval-heading">
    <div class="interaction-icon approval-icon"><IconShieldExclamation :size="23" :stroke-width="1.8" /></div>
    <div class="interaction-content">
      <div class="interaction-kicker">HUMAN APPROVAL REQUIRED</div>
      <h2 id="approval-heading">{{ suspension.message }}</h2>
      <p>{{ suspension.metadata.approvalReason }}</p>

      <dl class="approval-details">
        <div><dt>Action</dt><dd>{{ suspension.metadata.toolName }}</dd></div>
        <div><dt>Risk</dt><dd>{{ suspension.metadata.riskLevel }}</dd></div>
        <div><dt>Policy</dt><dd>{{ suspension.metadata.approvalCode }}</dd></div>
        <div><dt>Arguments</dt><dd><code>{{ JSON.stringify(toolCall?.arguments ?? {}) }}</code></dd></div>
      </dl>

      <div class="approval-reason field-block">
        <label for="reject-reason">拒绝原因</label>
        <input id="reject-reason" v-model="reason" type="text" />
      </div>
      <p class="approval-choice-note">你可以拒绝外部发布并保留内部草稿，也可以允许 Agent 继续执行发布。</p>
      <div class="approval-actions">
        <button class="danger-button" type="button" :disabled="busy" @click="emit('decide', false, reason)">
          <IconX :size="17" :stroke-width="2" />拒绝
        </button>
        <button class="primary-button" type="button" :disabled="busy" @click="emit('decide', true)">
          <IconCheck :size="17" :stroke-width="2" />批准并恢复
        </button>
      </div>
    </div>
  </section>
</template>
