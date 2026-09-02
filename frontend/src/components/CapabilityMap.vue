<script setup lang="ts">
import {IconCircleCheck, IconTransform} from '@tabler/icons-vue'
import type {CapabilitySource} from '@/types/agent'

defineProps<{ capabilities: CapabilitySource[] }>()
</script>

<template>
  <section class="surface capability-map" aria-labelledby="capability-heading">
    <div class="section-heading compact-heading">
      <h2 id="capability-heading">能力来源</h2>
    </div>
    <ul class="capability-list">
      <li v-for="item in capabilities" :key="item.name">
        <IconCircleCheck v-if="item.source === 'NATIVE'" :size="16" :stroke-width="2" aria-hidden="true"/>
        <IconTransform v-else :size="16" :stroke-width="2" aria-hidden="true"/>
        <span>{{ item.name }}</span>
        <code>{{ item.source }}</code>
      </li>
    </ul>
  </section>
</template>
