package main

import (
	"fmt"
	"math/rand"
	"net/http"
	"time"
)

/**
 * 数据同步回调服务
 *
 * 【接口说明】
 * 1. 回调接口：POST /api/callback/data-sync
 * 2. 健康接口：GET  /api/callback/data-sync/health
 *
 * 【健康检查接口约定】
 * - 路径格式：<回调接口路径>/health（强制约定）
 * - 当前：/api/callback/data-sync/health
 * - 功能：仅验证 X-Secret 和 X-Request-Id 请求头
 * - 要求：健康接口和回调接口必须使用相同的 BaseUrl
 *
 * 【自动注册】
 * RegisterRoute() 函数会自动注册健康检查接口，无需额外代码
 */

type DataSyncResult struct {
	RecordsProcessed int    `json:"records_processed"`
	DurationMs       int64  `json:"duration_ms"`
	Status           string `json:"status"`
	Message          string `json:"message"`
}

type DataSyncCallback struct {
	rand *rand.Rand
}

func NewDataSyncCallback() *DataSyncCallback {
	return &DataSyncCallback{
		rand: rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

func (c *DataSyncCallback) RoutePath() string {
	return "/api/callback/data-sync"
}

func (c *DataSyncCallback) SecretKey() string {
	/**
	 * 返回密钥
	 * 【注意】此密钥需要与调度器回调配置中的密钥保持一致
	 */
	return "data-sync-secret-key-2026"
}

func (c *DataSyncCallback) Endpoint() string {
	/**
	 * 返回模块名称
	 * 用于健康检查响应的 endpoint 字段
	 */
	return "数据同步服务"
}

func (c *DataSyncCallback) RunJob(param map[string]interface{}) *Result {
	requestID := GetString(param, "request_id", "")
	syncType := GetString(param, "syncType", "delta")
	date := GetString(param, "date", "2024-01-01")

	// 【规范】日志开头带上 [request_id]
	fmt.Printf("[%s] 开始执行数据同步任务\n", requestID)
	fmt.Printf("[%s] 参数: syncType=%s, date=%s\n", requestID, syncType, date)

	startTime := time.Now()

	// 执行同步逻辑
	result := c.service.Sync()

	durationMs := time.Since(startTime).Milliseconds()
	fmt.Printf("[%s] 同步完成: %d 条记录，耗时: %dms\n",
		requestID, result.RecordsProcessed, durationMs)

	return Ok(result)
}

type DataSyncService struct {
	rand *rand.Rand
}

func NewDataSyncService() *DataSyncService {
	return &DataSyncService{
		rand: rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

func (s *DataSyncService) Sync() *DataSyncResult {
	startTime := time.Now()

	// 模拟耗时操作
	time.Sleep(time.Duration(1000+s.rand.Intn(2000)) * time.Millisecond)

	records := 100 + s.rand.Intn(900)

	return &DataSyncResult{
		RecordsProcessed: records,
		DurationMs:       time.Since(startTime).Milliseconds(),
		Status:           "success",
		Message:          fmt.Sprintf("成功同步 %d 条记录", records),
	}
}

// ===== 主函数 =====
func main() {
	callback := NewDataSyncCallback()

	mux := http.NewServeMux()

	// 注册路由（会自动同时注册健康检查接口 /api/callback/data-sync/health）
	RegisterRoute(mux, callback)

	fmt.Println("========================================")
	fmt.Println("✅ 数据同步回调服务已启动")
	fmt.Println("📌 回调接口: POST /api/callback/data-sync")
	fmt.Println("📌 健康接口: GET  /api/callback/data-sync/health")
	fmt.Println("🔑 密钥: data-sync-secret-key-2026")
	fmt.Println("📝 模块名称: 数据同步服务")
	fmt.Println("========================================")

	http.ListenAndServe(":8080", mux)
}
