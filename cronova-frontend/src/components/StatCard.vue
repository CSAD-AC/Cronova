<template>
  <div class="stat-card" :class="`tone-${tone}`">
    <div class="stat-icon">
      <el-icon :size="20">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="stat-body">
      <div class="stat-label">{{ title }}</div>
      <div class="stat-value cronova-mono">
        {{ formattedValue
        }}<span v-if="unit" class="stat-unit">{{ unit }}</span>
        <span v-if="pulse" class="stat-pulse" title="运行中" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'

/**
 * 统计卡片组件：仪表盘/列表页的指标读数卡。
 * 数字使用等宽仪表字体；pulse 为 true 时显示呼吸脉冲角标（签名元素）。
 */
const props = withDefaults(
  defineProps<{
    /** 指标标题 */
    title: string
    /** 指标数值 */
    value: number | string
    /** 数值单位（如 %、ms） */
    unit?: string
    /** 图标组件（Element Plus 图标） */
    icon?: Component | string
    /** 色调：primary / success / running / accent / pending / danger */
    tone?: string
    /** 是否显示运行中脉冲角标 */
    pulse?: boolean
  }>(),
  {
    tone: 'primary',
    pulse: false,
  },
)

/** 千分位格式化后的展示值（非数字原样返回） */
const formattedValue = computed(() => {
  if (typeof props.value === 'number') {
    return props.value.toLocaleString()
  }
  return props.value
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid var(--cronova-border);
  background: var(--cronova-surface);
  box-shadow: var(--cronova-card-shadow);
  transition: all .18s ease;
  position: relative;
  overflow: hidden;
}

.stat-card:hover {
  border-color: var(--cronova-border-strong);
  transform: translateY(-1px);
}

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.tone-primary .stat-icon { background: var(--cronova-primary-soft); color: var(--cronova-primary); }
.tone-success .stat-icon { background: var(--cronova-success-soft); color: var(--cronova-success); }
.tone-running .stat-icon { background: var(--cronova-running-soft); color: var(--cronova-running); }
.tone-accent .stat-icon { background: var(--cronova-accent-soft); color: var(--cronova-accent); }
.tone-pending .stat-icon { background: var(--cronova-pending-soft); color: var(--cronova-pending); }
.tone-danger .stat-icon { background: var(--cronova-danger-soft); color: var(--cronova-danger); }

.tone-primary .stat-value { color: var(--cronova-primary); }
.tone-success .stat-value { color: var(--cronova-success); }
.tone-running .stat-value { color: var(--cronova-running); }
.tone-accent .stat-value { color: var(--cronova-accent); }
.tone-pending .stat-value { color: var(--cronova-pending); }
.tone-danger .stat-value { color: var(--cronova-danger); }

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--cronova-muted);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
}

.stat-unit {
  font-size: 13px;
  font-weight: 500;
  margin-left: 3px;
  opacity: .8;
}

/* 运行中脉冲角标 */
.stat-pulse {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cronova-running);
  margin-left: 8px;
  vertical-align: middle;
  animation: cronova-pulse-running 2s infinite;
}
</style>
