package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"
)

// Result 统一返回对象
type Result struct {
	Code       int         `json:"code"`
	Msg        string      `json:"msg"`
	Data       interface{} `json:"data"`
	CostMillis int64       `json:"cost_millis"`
	RequestID  string      `json:"request_id"`
}

// Ok 成功响应
func Ok(data interface{}) *Result {
	return &Result{
		Code: 200,
		Msg:  "success",
		Data: data,
	}
}

// Fail 失败响应
func Fail(code int, msg string) *Result {
	return &Result{
		Code: code,
		Msg:  msg,
	}
}

// ToJSON 转换为JSON
func (r *Result) ToJSON() ([]byte, error) {
	return json.Marshal(r)
}

// SecurityException 安全异常
type SecurityException struct {
	Message string
}

func (e *SecurityException) Error() string {
	return e.Message
}

/*
ScheduledJobBase 定时任务回调基类

【使用说明】

一、核心信息
    - 请求方法：POST
    - 请求头：X-Secret（密钥）、X-Request-Id（请求ID）
    - 请求体：可选，不做强制要求

二、子类必须实现的方法
    1. RoutePath()   - 返回路由路径
    2. SecretKey()   - 返回密钥，用于 X-Secret 校验
    3. RunJob(param) - 执行业务逻辑，返回 *Result

三、子类需开放的接口
    - 提供 POST 接口接收回调请求，内部调用 Handle()

四、日志规范
    - 务必在日志中输出 requestId，便于快速定位日志
    - 示例：fmt.Printf("[%s] 开始执行任务\n", requestId)

【请求头示例】
    X-Request-Id: abc123def456
    X-Secret: your-secret-key

*/
type ScheduledJobBase interface {
	// RoutePath 返回路由路径
	RoutePath() string
	// SecretKey 返回密钥，用于 X-Secret 校验
	SecretKey() string
	// RunJob 执行业务逻辑
	RunJob(param map[string]interface{}) *Result
}

// Handle 处理请求
func Handle(base ScheduledJobBase, w http.ResponseWriter, r *http.Request) {
	startTime := time.Now()
	requestID := r.Header.Get("X-Request-Id")

	if requestID == "" {
		result := Fail(400, "缺少请求头 X-Request-Id")
		result.CostMillis = time.Since(startTime).Milliseconds()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
		return
	}

	defer func() {
		if err := recover(); err != nil {
			fmt.Printf("[%s] 任务执行出错: %v\n", requestID, err)
			result := Fail(500, "执行失败")
			result.RequestID = requestID
			result.CostMillis = time.Since(startTime).Milliseconds()
			w.Header().Set("Content-Type", "application/json")
			json.NewEncoder(w).Encode(result)
		}
	}()

	// 验证密钥
	inputSecret := r.Header.Get("X-Secret")
	if inputSecret != base.SecretKey() {
		result := Fail(401, "密钥验证失败")
		result.RequestID = requestID
		result.CostMillis = time.Since(startTime).Milliseconds()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
		return
	}

	// 解析请求体
	var param map[string]interface{}
	if r.Body != nil {
		json.NewDecoder(r.Body).Decode(&param)
	}
	if param == nil {
		param = make(map[string]interface{})
	}

	// 执行业务逻辑
	result := base.RunJob(param)
	if result == nil {
		result = Ok(nil)
	}

	result.RequestID = requestID
	result.CostMillis = time.Since(startTime).Milliseconds()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// RegisterRoute 注册路由
func RegisterRoute(mux *http.ServeMux, base ScheduledJobBase) {
	mux.HandleFunc(base.RoutePath(), func(w http.ResponseWriter, r *http.Request) {
		if r.Method == "POST" {
			Handle(base, w, r)
		} else {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		}
	})
}

// GetInt 获取整数参数
func GetInt(param map[string]interface{}, key string, defaultVal int) int {
	if val, ok := param[key]; ok {
		switch v := val.(type) {
		case int:
			return v
		case int64:
			return int(v)
		case string:
			if i, err := strconv.Atoi(v); err == nil {
				return i
			}
		}
	}
	return defaultVal
}

// GetString 获取字符串参数
func GetString(param map[string]interface{}, key string, defaultVal string) string {
	if val, ok := param[key]; ok {
		if v, ok := val.(string); ok {
			return v
		}
	}
	return defaultVal
}
