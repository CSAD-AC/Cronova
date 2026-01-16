package main

import (
	"fmt"
	"math/rand"
	"net/http"
	"time"
)

type DataSyncResult struct {
	RecordsProcessed int   `json:"records_processed"`
	DurationMs       int64 `json:"duration_ms"`
	Status           string `json:"status"`
}

type DataSyncCallback struct {
	service *DataSyncService
}

func NewDataSyncCallback() *DataSyncCallback {
	return &DataSyncCallback{service: NewDataSyncService()}
}

func (c *DataSyncCallback) RoutePath() string {
	return "/api/callback/data-sync/job"
}

func (c *DataSyncCallback) SecretKey() string {
	return "data-sync-secret-2026"
}

func (c *DataSyncCallback) RunJob(param map[string]interface{}) *Result {
	fmt.Printf("开始数据同步: syncType=%s\n", GetString(param, "syncType", ""))
	result := c.service.Sync()
	fmt.Printf("数据同步完成: records=%d\n", result.RecordsProcessed)
	return Ok(result)
}

type DataSyncService struct {
	rand *rand.Rand
}

func NewDataSyncService() *DataSyncService {
	return &DataSyncService{rand: rand.New(rand.NewSource(time.Now().UnixNano()))}
}

func (s *DataSyncService) Sync() *DataSyncResult {
	startTime := time.Now()
	time.Sleep(time.Duration(1000+s.rand.Intn(2000)) * time.Millisecond)
	return &DataSyncResult{
		RecordsProcessed: 100 + s.rand.Intn(900),
		DurationMs:       time.Since(startTime).Milliseconds(),
		Status:           "success",
	}
}

func main() {
	mux := http.NewServeMux()
	RegisterRoute(mux, NewDataSyncCallback())
	fmt.Println("启动服务: POST /api/callback/data-sync/job")
	http.ListenAndServe(":8080", mux)
}
