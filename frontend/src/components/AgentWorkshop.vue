<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {IconArrowRight, IconMessages, IconRefresh, IconRobot} from '@tabler/icons-vue'
import TaskComposer from '@/components/TaskComposer.vue'
import {agentApi} from '@/api/agent'
import type {AgentDefinition, CreateAgentPayload, EmbeddingProfile, ModelProfile} from '@/types/agent'

const props = defineProps<{
  /** 当前工作区生效的 Agent；创建成功后用于「前往对话」入口。 */
  agent: AgentDefinition | null
  busy: boolean
}>()
const emit = defineEmits<{
  /** 提交 Agent 配置：由父组件调用 store.createAgent（后端创建真实 Agent）。 */
  create: [configuration: CreateAgentPayload]
  reset: []
  configureEmbedding: [profile: EmbeddingProfile]
  applyModel: [profile: ModelProfile]
  /** 携带目标 Agent 跳转到 Agent 对话页。 */
  goChat: [definition: AgentDefinition | null]
}>()

const agents = ref<AgentDefinition[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

/** 已创建的 Agent 清单：供快速查看与跳转对话。 */
const knownAgents = computed(() => agents.value)

/** 拉取 Agent 清单；失败仅提示，不阻断创建表单。 */
async function loadAgents() {
  loading.value = true
  loadError.value = null
  try {
    agents.value = await agentApi.listAgents()
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : 'Agent 清单加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAgents()
})
</script>

<template>
  <div class="agent-workshop">
    <section v-if="props.agent" class="surface workshop-goto">
      <div class="workshop-goto-text">
        <IconMessages :size="18"/>
        <span>当前 Agent「<strong>{{ props.agent.name }}</strong>」已就绪，可前往对话页开始会话。</span>
      </div>
      <button class="primary-button" type="button" @click="emit('goChat', props.agent)">
        前往 Agent 对话<IconArrowRight :size="16"/>
      </button>
    </section>

    <div class="workshop-columns">
      <TaskComposer class="workshop-composer" :busy="props.busy" :disabled="false" :agent="props.agent"
                    :show-reset="false" @create="(configuration) => emit('create', configuration)"
                    @configure-embedding="(profile) => emit('configureEmbedding', profile)"
                    @apply-model="(profile) => emit('applyModel', profile)"/>

      <aside class="surface workshop-agents" aria-labelledby="workshop-agents-heading">
        <div class="section-heading">
          <IconRobot :size="18" :stroke-width="1.8" aria-hidden="true"/>
          <h2 id="workshop-agents-heading">已创建的 Agent</h2>
          <button class="text-button" type="button" :disabled="loading" title="刷新清单"
                  aria-label="刷新清单" @click="loadAgents">
            <IconRefresh :size="15"/>
          </button>
        </div>
        <p class="workshop-hint">同名同版本的 Agent 复用同一定义（重建不产生副本）；要迭代配置请递增版本号。不会改写历史会话。</p>
        <p v-if="loadError" class="workshop-error" role="alert">{{ loadError }}</p>
        <ul v-if="knownAgents.length" class="workshop-list">
          <li v-for="item in knownAgents" :key="item.agentId" class="workshop-item">
            <div class="workshop-item-main">
              <span class="workshop-item-name">
                {{ item.name }}
                <span v-if="item.runnable === false" class="workshop-item-archived">归档</span>
              </span>
              <span class="workshop-item-meta">
                v{{ item.version }} · {{ item.modelProvider }}/{{ item.modelName }} · {{ item.tools.length }} 工具
              </span>
              <span v-if="item.description" class="workshop-item-desc">{{ item.description }}</span>
            </div>
            <button class="text-button" type="button"
                    :title="item.runnable === false
                        ? `选中「${item.name}」并到对话页发送消息时自动重建`
                        : `用「${item.name}」开始对话`"
                    @click="emit('goChat', item)">
              对话<IconArrowRight :size="14"/>
            </button>
          </li>
        </ul>
        <p v-else-if="!loading" class="workshop-empty">还没有 Agent。左侧填写配置后点击「创建 Agent」。</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.agent-workshop {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.workshop-goto {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-left: 3px solid #059669;
}

.workshop-goto-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #374151;
}

.workshop-columns {
  display: grid;
  grid-template-columns: minmax(420px, 560px) minmax(0, 1fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
  align-items: start;
}

.workshop-composer {
  max-height: calc(100vh - 190px);
  overflow-y: auto;
}

.workshop-agents {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.workshop-agents h2 {
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

.workshop-hint {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}

.workshop-error {
  margin: 0;
  font-size: 12px;
  color: #b91c1c;
}

.workshop-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}

.workshop-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 8px 10px;
}

.workshop-item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.workshop-item-name {
  font-size: 13px;
  font-weight: 650;
  color: #111827;
}

.workshop-item-archived {
  font-size: 11px;
  font-weight: 500;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 999px;
  padding: 0 6px;
  margin-left: 4px;
}

.workshop-item-meta {
  font-size: 11.5px;
  color: #6b7280;
}

.workshop-item-desc {
  font-size: 11.5px;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workshop-empty {
  margin: 0;
  font-size: 12.5px;
  color: #6b7280;
}

@media (max-width: 1100px) {
  .workshop-columns {
    grid-template-columns: 1fr;
  }
}
</style>
