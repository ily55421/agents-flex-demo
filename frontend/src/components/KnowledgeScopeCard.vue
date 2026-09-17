<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {IconDatabase, IconRefresh} from '@tabler/icons-vue'
import {knowledgeApi} from '@/api/agent'
import type {AgentDefinition, KnowledgeDocument} from '@/types/agent'
import ConfigFieldLabel from '@/components/ConfigFieldLabel.vue'

const props = defineProps<{
  /** 当前工作区生效的 Agent；其 knowledgeNamespace 决定回显的范围。 */
  agent: AgentDefinition | null
  busy: boolean
}>()
const emit = defineEmits<{
  /** 切换检索范围：父组件用新范围重建 Agent，从下一次新会话对话生效。 */
  change: [docId: string]
}>()

const docs = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const scope = ref('all')

/** 选中 Agent 变化时回显它绑定的检索范围（含重建成功后的新范围）。 */
watch(() => props.agent?.knowledgeNamespace, (namespace) => {
  scope.value = namespace ?? 'all'
}, {immediate: true})

/** 范围切换是否可用：需要先选中 Agent（重建要以它的完整配置为基础）。 */
const changeDisabled = computed(() => props.busy || !props.agent)

async function loadDocs() {
  loading.value = true
  try {
    docs.value = await knowledgeApi.documents()
  } catch {
    docs.value = []
  } finally {
    loading.value = false
  }
}

function onScopeChange() {
  if (changeDisabled.value) {
    // 无 Agent 时下拉回退到展示值，不产生无效切换。
    scope.value = props.agent?.knowledgeNamespace ?? 'all'
    return
  }
  emit('change', scope.value)
}

onMounted(() => {
  void loadDocs()
})
</script>

<template>
  <section class="surface scope-card" aria-labelledby="scope-card-heading">
    <div class="section-heading">
      <IconDatabase :size="18" :stroke-width="1.8" aria-hidden="true"/>
      <h2 id="scope-card-heading">知识库范围</h2>
      <button class="text-button" type="button" :disabled="loading" title="刷新文档清单"
              aria-label="刷新文档清单" @click="loadDocs">
        <IconRefresh :size="15"/>
      </button>
    </div>

    <div class="field-block">
      <ConfigFieldLabel for-id="workspace-knowledge-scope" text="回答时检索的知识库"
                        help="限定 Agent 回答问题时 search_knowledge 检索的文档范围；切换会以当前 Agent 配置重建运行对象。"/>
      <select id="workspace-knowledge-scope" v-model="scope" :disabled="changeDisabled"
              @change="onScopeChange">
        <option value="all">全部文档</option>
        <option v-for="doc in docs" :key="doc.docId" :value="doc.docId">
          {{ doc.title }}（{{ doc.chunkCount }} 片）
        </option>
      </select>
    </div>

    <p v-if="!agent" class="scope-hint">先在上方选择 Agent 后即可切换检索范围。</p>
    <p v-else class="scope-hint">切换会重建 Agent，从下一次新会话开始生效；当前进行中的会话保持原范围。</p>
    <p v-if="agent?.runnable === false" class="scope-hint archived">
      当前为归档 Agent：先发送一条消息自动重建后，再切换范围。
    </p>
  </section>
</template>

<style scoped>
.scope-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.scope-card h2 {
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

.scope-hint {
  margin: 0;
  font-size: 11.5px;
  color: #6b7280;
  line-height: 1.5;
}

.scope-hint.archived {
  color: #92400e;
}
</style>
