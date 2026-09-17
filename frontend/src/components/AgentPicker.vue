<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {IconRefresh, IconRobot, IconSettings} from '@tabler/icons-vue'
import {agentApi} from '@/api/agent'
import type {AgentDefinition} from '@/types/agent'
import ConfigFieldLabel from '@/components/ConfigFieldLabel.vue'

const props = defineProps<{
  /** 当前工作区生效的 Agent；用于回显选中项。 */
  agent: AgentDefinition | null
  busy: boolean
}>()
const emit = defineEmits<{
  /** 选中某个可运行 Agent；切换逻辑（含清空当前对话）由父组件处理。 */
  select: [definition: AgentDefinition]
  /** 跳转到 Agent 维护页创建或管理智能体。 */
  goWorkshop: []
  /** 跳转到模型配置页。 */
  goModels: []
}>()

const agents = ref<AgentDefinition[]>([])
const selectedAgentId = ref('')
const loading = ref(false)
const loadError = ref<string | null>(null)

/** 服务重启后运行对象未重建的归档 Agent 不可对话，单独分组展示。 */
const runnableAgents = computed(() => agents.value.filter((item) => item.runnable !== false))
const archivedAgents = computed(() => agents.value.filter((item) => item.runnable === false))

/** 下拉选中值：优先跟随外部 agent 状态（URL 恢复、新建 Agent 后自动回显），否则用本地选择。 */
const selectedId = computed<string>({
  get: () => props.agent?.agentId ?? selectedAgentId.value,
  set: (value: string) => {
    selectedAgentId.value = value
  },
})

/** 拉取智能体清单；失败时展示错误但不阻断对话页。 */
async function loadAgents() {
  loading.value = true
  loadError.value = null
  try {
    agents.value = await agentApi.listAgents()
    if (selectedAgentId.value && !agents.value.some((item) => item.agentId === selectedAgentId.value)) {
      selectedAgentId.value = ''
    }
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '智能体列表加载失败'
  } finally {
    loading.value = false
  }
}

/** 下拉选择即触发 select；清空选择（value=''）不产生副作用。 */
function onSelectionChange() {
  if (!selectedAgentId.value) return
  const definition = agents.value.find((item) => item.agentId === selectedAgentId.value)
  if (definition) emit('select', definition)
}

onMounted(() => {
  void loadAgents()
})
</script>

<template>
  <section class="surface agent-picker" aria-labelledby="agent-picker-heading">
    <div class="section-heading">
      <IconRobot :size="18" :stroke-width="1.8" aria-hidden="true"/>
      <h2 id="agent-picker-heading">选择 Agent</h2>
      <button class="text-button" type="button" :disabled="loading" title="刷新智能体列表"
              aria-label="刷新智能体列表" @click="loadAgents">
        <IconRefresh :size="15"/>
      </button>
    </div>

    <div class="field-block">
      <ConfigFieldLabel for-id="workspace-agent-select" text="对话智能体"
                        help="选择已创建的 Agent 直接开始对话；发送第一条消息即创建新会话。"/>
      <select id="workspace-agent-select" v-model="selectedId"
              :disabled="loading || Boolean(busy)" @change="onSelectionChange">
        <option value="" disabled>{{ loading ? '加载智能体…' : '选择要对话的智能体…' }}</option>
        <option v-for="item in runnableAgents" :key="item.agentId" :value="item.agentId">
          {{ item.name }} · {{ item.modelProvider }}/{{ item.modelName }}
        </option>
        <optgroup v-if="archivedAgents.length" label="历史归档（选中发送消息时自动重建）">
          <option v-for="item in archivedAgents" :key="item.agentId" :value="item.agentId">
            {{ item.name }} · {{ item.modelProvider }}/{{ item.modelName }}
          </option>
        </optgroup>
      </select>
    </div>

    <div v-if="agent" class="agent-picker-brief">
      <span class="agent-picker-name">{{ agent.name }}</span>
      <span>{{ agent.modelProvider }} · {{ agent.modelName }} · {{ agent.tools.length }} 工具</span>
      <span v-if="agent.description" class="agent-picker-desc">{{ agent.description }}</span>
      <span v-if="agent.runnable === false" class="agent-picker-archived-hint">
        历史归档：发送第一条消息时自动重建（需要 API Key 的服务请到「Agent 维护」页填写）。
      </span>
    </div>

    <p v-if="loadError" class="agent-picker-error" role="alert">加载失败：{{ loadError }}</p>
    <p v-else-if="!runnableAgents.length && !archivedAgents.length && !loading" class="agent-picker-empty">
      还没有 Agent，先到「Agent 维护」页创建一个。
    </p>

    <div class="agent-picker-links">
      <button class="text-button" type="button" @click="emit('goWorkshop')">
        <IconRobot :size="15"/>创建 / 维护 Agent
      </button>
      <button class="text-button" type="button" @click="emit('goModels')">
        <IconSettings :size="15"/>模型配置
      </button>
    </div>
  </section>
</template>

<style scoped>
.agent-picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.agent-picker h2 {
  margin: 0;
  font-size: 14px;
  color: #111827;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-heading .text-button {
  margin-left: auto;
}

.agent-picker-brief {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12px;
  color: #047857;
}

.agent-picker-name {
  font-weight: 650;
  color: #065f46;
}

.agent-picker-desc {
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.agent-picker-archived-hint {
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 11.5px;
}

.agent-picker-error {
  margin: 0;
  font-size: 12px;
  color: #b91c1c;
}

.agent-picker-empty {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}

.agent-picker-links {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
