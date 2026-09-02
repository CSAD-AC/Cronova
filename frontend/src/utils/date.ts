import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'

dayjs.extend(utc)
dayjs.extend(timezone)

/**
 * 格式化日期时间 (将UTC转换为本地北京时间)
 * @param date 日期字符串或Date对象
 * @param format 格式化模板，默认为 'YYYY-MM-DD HH:mm:ss'
 * @returns 格式化后的字符串
 */
export function formatDateTime(date: string | Date | undefined | null, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  
  // 含 Z/T 的字符串 dayjs 自动识别为 UTC，此处显式转回本地时区（北京时间）
  return dayjs.utc(date).tz('Asia/Shanghai').format(format)
}

/**
 * 格式化持续时间 (毫秒转为可读字符串)
 * @param ms 毫秒数
 * @returns 格式化后的字符串
 */
export function formatDuration(ms: number | undefined | null): string {
  if (!ms || ms < 0) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
  
  const minutes = Math.floor(ms / 60000)
  const seconds = ((ms % 60000) / 1000).toFixed(0)
  return `${minutes}m ${seconds}s`
}
