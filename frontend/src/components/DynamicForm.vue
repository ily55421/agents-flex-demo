<script setup lang="ts">
import {reactive, watch} from 'vue'
import {IconForms, IconSend} from '@tabler/icons-vue'
import type {JsonSchema} from '@/types/agent'

const props = defineProps<{ schema: JsonSchema; busy: boolean }>()
const emit = defineEmits<{ submit: [values: Record<string, unknown>] }>()
const values = reactive<Record<string, string | number | boolean>>({})

// 每次收到新的 Suspension Schema 都重建表单值，防止上一次挂起留下的字段污染本次提交。
/**
 * 监听 Runtime 下发的 Schema，并仅按 Schema 显式默认值重建表单模型。
 * 枚举没有默认值时保持为空，要求用户主动选择，避免把第一项误当成用户确认的信息。
 * 重建前清空旧字段，避免不同 Suspension 之间发生值污染。
 */
watch(
    () => props.schema,
    (schema) => {
      for (const key of Object.keys(values)) delete values[key]
      for (const [key, field] of Object.entries(schema.properties)) {
        values[key] = field.default ?? ''
      }
    },
    {immediate: true},
)
</script>

<template>
  <section class="interaction-panel form-panel" aria-labelledby="form-heading">
    <div class="interaction-icon form-icon">
      <IconForms :size="22" :stroke-width="1.8"/>
    </div>
    <div class="interaction-content">
      <div class="interaction-kicker">Agents-Flex 表单输入</div>
      <h2 id="form-heading">{{ schema.title }}</h2>
      <p v-if="schema.description">{{ schema.description }}</p>

      <form class="dynamic-form" @submit.prevent="emit('submit', { ...values })">
        <div v-for="(field, key) in schema.properties" :key="key" class="field-block">
          <label :for="`dynamic-${key}`">
            {{ field.title }}
            <span v-if="schema.required?.includes(String(key))" class="required-mark" aria-hidden="true">*</span>
            <span v-if="schema.required?.includes(String(key))" class="sr-only">必填</span>
          </label>
          <select
              v-if="field.enum"
              :id="`dynamic-${key}`"
              v-model="values[key]"
              :required="schema.required?.includes(String(key))"
          >
            <option value="" disabled>请选择</option>
            <option v-for="option in field.enum" :key="option" :value="option">{{ option }}</option>
          </select>
          <input
              v-else
              :id="`dynamic-${key}`"
              v-model="values[key]"
              :type="field.type === 'number' ? 'number' : 'text'"
              :required="schema.required?.includes(String(key))"
          />
          <small v-if="field.description">{{ field.description }}</small>
        </div>
        <button class="primary-button form-submit" type="submit" :disabled="busy">
          <IconSend :size="17" :stroke-width="1.9"/>
          {{ busy ? '正在提交' : '提交并恢复 Agent' }}
        </button>
      </form>
    </div>
  </section>
</template>
