package main

import (
	"fmt"
	"math/rand"
	"net/http"
	"time"
)

// DataSyncResult 数据同步结果
type DataSyncResult struct {
	RecordsProcessed int    `json:"records_processed"`
	DurationMs       int64  `json:"duration_ms"`
	Status           string `json:"status"`
	Message          string `json:"message"`
}

// DataSyncCallback 数据同步回调控制器
type DataSyncCallback struct {
	service *DataSyncService
}

// NewDataSyncCallback 创建数据同步回调
func NewDataSyncCallback() *DataSyncCallback {
	return &DataSyncCallback{
		service: NewDataSyncService(),
	}
}

// RoutePath 返回路由路径
func (c *DataSyncCallback) RoutePath() string {
	return "/api/callback/data-sync/job"
}

// SecretKey 返回密钥
func (c *DataSyncCallback) SecretKey() string {
	return "data-sync-secret-key-2026"
}

// RunJob 执行业务逻辑
func (c *DataSyncCallback) RunJob(param map[string]interface{}) *Result {
	syncType := GetString(param, "syncType", "")
	date := GetString(param, "date", "")

	fmt.Printf("开始执行数据同步任务, sync_type=%s, date=%s\n", syncType, date)

	result := c.service.Sync(syncType, date)

	fmt.Printf("数据同步完成, 处理记录数=%d, 耗时=%dms\n", result.RecordsProcessed, result.DurationMs)

	return Ok(result)
}

// DataSyncService 数据同步服务
type DataSyncService struct {
	rand *rand.Rand
}

// NewDataSyncService 创建数据同步服务
func NewDataSyncService() *DataSyncService {
	return &DataSyncService{
		rand: rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

// Sync 执行数据同步
func (s *DataSyncService) Sync(syncType string, date string) *DataSyncResult {
	startTime := time.Now()

	fmt.Printf("执行数据同步, 类型=%s, 日期=%s\n", syncType, date)

	// 模拟业务处理
	time.Sleep(time.Duration(1000+s.rand.Intn(2000)) * time.Millisecond)

	records := 100 + s.rand.Intn(900)

	result := &DataSyncResult{
		RecordsProcessed: records,
		DurationMs:       time.Since(startTime).Milliseconds(),
		Status:           "success",
		Message:          fmt.Sprintf("成功同步 %d 条记录", records),
	}

	return result
}

func main() {
	mux := http.NewServeMux()

	callback := NewDataSyncCallback()
	RegisterRoute(mux, callback)

	fmt.Println("启动数据同步回调服务...")
	fmt.Println("路由: POST /api/callback/data-sync/job")

	server := &http.Server{
		Addr:    ":8080",
		Handler: mux,
	}

	fmt.Println("服务启动成功，监听端口 8080")
	if err := server.ListenAndServe(); err != nil {
		fmt.Printf("服务启动失败: %v\n", err)
	}
}
