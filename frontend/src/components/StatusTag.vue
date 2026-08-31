<template>
  <span class="status-tag" :class="cls">
    <span class="status-dot" :class="{ pulse: isPulsing }" />
    <span class="status-text">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** 状态到展示信息的映射表（样式类 + 中文标签 + 是否脉冲） */
const STATUS_MAP: Record<string, { cls: string; label: string; pulse?: boolean }> = {
  // 任务运行状态
  success:  { cls: 'st-success',  label: '成功' },
  running:  { cls: 'st-running',  label: '运行中', pulse: true },
  failed:   { cls: 'st-danger',   label: '失败' },
  timeout:  { cls: 'st-warning',  label: '超时' },
  paused:   { cls: 'st-warning',  label: '已暂停' },
  pending:  { cls: 'st-pending',  label: '等待中' },
  // 执行器状态
  online:   { cls: 'st-success',  label: '在线', pulse: true },
  offline:  { cls: 'st-danger',   label: '离线' },
  // 任务启用状态
  active:   { cls: 'st-success',  label: '启用' },
}

/**
 * 状态标签组件（签名元素"调度脉搏"）：
 * 全圆角 pill + 状态点，运行中/在线状态带呼吸脉冲动画。
 * 传入 status 原值（如 running / success / online），未匹配时原样展示。
 */
const props = defineProps<{
  /** 状态原值（success/running/failed/pending/paused/timeout/online/offline/active） */
  status: string
}>()

/** 解析后的样式类 */
const cls = computed(() => STATUS_MAP[props.status]?.cls || 'st-pending')

/** 解析后的中文标签（未知状态原样展示） */
const label = computed(() => STATUS_MAP[props.status]?.label || props.status)

/** 是否显示呼吸脉冲（运行中/在线） */
const isPulsing = computed(() => !!STATUS_MAP[props.status]?.pulse)
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* 成功/在线 */
.st-success {
  background: var(--sjdt-success-soft);
  color: var(--sjdt-success);
}
.st-success .status-dot { background: var(--sjdt-success); }

/* 运行中：琥珀 + 呼吸脉冲 */
.st-running {
  background: var(--sjdt-running-soft);
  color: var(--sjdt-running);
}
.st-running .status-dot {
  background: var(--sjdt-running);
  animation: sjdt-pulse-running 2s infinite;
}

/* 失败/离线 */
.st-danger {
  background: var(--sjdt-danger-soft);
  color: var(--sjdt-danger);
}
.st-danger .status-dot { background: var(--sjdt-danger); }

/* 超时/已暂停 */
.st-warning {
  background: var(--sjdt-running-soft);
  color: var(--sjdt-running);
}
.st-warning .status-dot { background: var(--sjdt-running); }

/* 等待中/禁用 */
.st-pending {
  background: var(--sjdt-pending-soft);
  color: var(--sjdt-pending);
}
.st-pending .status-dot { background: var(--sjdt-pending); }
</style>
