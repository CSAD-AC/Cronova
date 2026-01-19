package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"reflect"
	"strconv"
	"time"
)

const (
	defaultSecretKey      = "default-secret-key-placeholder"
	defaultEndpoint       = "回调服务"
	placeholderStructName = "ScheduledJobBase"
)

type Result struct {
	Code       int         `json:"code"`
	Msg        string      `json:"msg"`
	Data       interface{} `json:"data"`
	CostMillis int64       `json:"cost_millis"`
	RequestID  string      `json:"request_id"`
}

func Ok(data interface{}) *Result {
	return &Result{Code: 200, Msg: "success", Data: data}
}

func Fail(code int, msg string) *Result {
	return &Result{Code: code, Msg: msg}
}

func (r *Result) ToJSON() ([]byte, error) {
	return json.Marshal(r)
}

type SecurityException struct {
	Message string
}

func (e *SecurityException) Error() string {
	return e.Message
}

type ScheduledJobBase interface {
	RoutePath() string
	SecretKey() string
	Endpoint() string
	RunJob(param map[string]interface{}) *Result
}

/**
 * 统一处理入口
 *
 * 【使用说明】
 * 子类在 RoutePath 对应的 POST 处理器中调用此函数
 */
func Handle(base ScheduledJobBase, w http.ResponseWriter, r *http.Request) {
	startTime := time.Now()
	requestID := r.Header.Get("X-Request-Id")

	if requestID == "" {
		result := Fail(400, "缺少请求头 X-Request-Id")
		result.CostMillis = time.Since(startTime).Milliseconds()
		writeJSON(w, result)
		return
	}

	defer func() {
		if err := recover(); err != nil {
			fmt.Printf("[%s] 任务执行出错: %v\n", requestID, err)
			result := Fail(500, "执行失败")
			result.RequestID = requestID
			result.CostMillis = time.Since(startTime).Milliseconds()
			writeJSON(w, result)
		}
	}()

	if err := validateMethodOverrides(base, requestID); err != nil {
		fmt.Printf("[%s] 方法重写校验失败: %v\n", requestID, err)
		result := Fail(500, "配置错误: "+err.Error())
		result.RequestID = requestID
		result.CostMillis = time.Since(startTime).Milliseconds()
		writeJSON(w, result)
		return
	}

	inputSecret := r.Header.Get("X-Secret")
	if inputSecret != base.SecretKey() {
		result := Fail(401, "密钥验证失败")
		result.RequestID = requestID
		result.CostMillis = time.Since(startTime).Milliseconds()
		writeJSON(w, result)
		return
	}

	var param map[string]interface{}
	if r.Body != nil {
		json.NewDecoder(r.Body).Decode(&param)
	}
	if param == nil {
		param = make(map[string]interface{})
	}

	result := base.RunJob(param)
	if result == nil {
		result = Ok(nil)
	}

	result.RequestID = requestID
	result.CostMillis = time.Since(startTime).Milliseconds()
	writeJSON(w, result)
}

/**
 * 健康检查入口
 *
 * 【接口约定】
 * - 路径格式：<回调接口路径>/health（强制约定）
 * - 功能：仅验证 X-Secret 和 X-Request-Id 请求头，不执行业务逻辑
 * - 要求：健康接口和回调接口必须使用相同的 BaseUrl
 *
 * 【响应格式】
 * {
 *   "code": 200,
 *   "msg": "success",
 *   "data": {
 *     "status": "UP",
 *     "timestamp": "2024-01-19T10:30:00",
 *     "endpoint": "数据同步服务",
 *     "message": "服务正常"
 *   },
 *   "cost_millis": 5,
 *   "request_id": "abc123def456"
 * }
 *
 * 子类注册示例：
 * healthPath := base.RoutePath() + "/health"
 * mux.HandleFunc(healthPath, func(w http.ResponseWriter, r *http.Request) {
 *     if r.Method == http.MethodGet {
 *         HealthCheck(base, w, r)
 *     }
 * })
 */
func HealthCheck(base ScheduledJobBase, w http.ResponseWriter, r *http.Request) {
	startTime := time.Now()
	requestID := r.Header.Get("X-Request-Id")

	// 验证请求头
	if requestID == "" {
		result := Fail(400, "缺少请求头 X-Request-Id")
		result.CostMillis = time.Since(startTime).Milliseconds()
		writeJSON(w, result)
		return
	}

	// 验证密钥
	inputSecret := r.Header.Get("X-Secret")
	if inputSecret != base.SecretKey() {
		result := Fail(401, "密钥验证失败")
		result.RequestID = requestID
		result.CostMillis = time.Since(startTime).Milliseconds()
		writeJSON(w, result)
		return
	}

	// 健康检查成功，构造响应数据
	healthData := map[string]interface{}{
		"status":    "UP",
		"timestamp": time.Now().Format(time.RFC3339),
		"endpoint":  base.Endpoint(),
		"message":   "服务正常",
	}

	result := Ok(healthData)
	result.RequestID = requestID
	result.CostMillis = time.Since(startTime).Milliseconds()

	fmt.Printf("[%s] 健康检查通过，endpoint: %s\n", requestID, base.Endpoint())
	writeJSON(w, result)
}

func validateMethodOverrides(base ScheduledJobBase, requestID string) error {
	val := reflect.ValueOf(base)
	if val.Kind() == reflect.Ptr {
		val = val.Elem()
	}

	typeName := val.Type().Name()
	if typeName == placeholderStructName {
		return fmt.Errorf("请勿直接使用基类，请创建结构体并实现 RoutePath()、SecretKey()、Endpoint() 和 RunJob() 方法")
	}

	secretKey := base.SecretKey()
	if secretKey == "" || secretKey == defaultSecretKey {
		return fmt.Errorf("%s 的 SecretKey() 方法未正确实现，返回了空或默认占位值", typeName)
	}

	fmt.Printf("[%s] 方法重写校验通过: %s\n", requestID, typeName)
	return nil
}

func writeJSON(w http.ResponseWriter, result *Result) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

/**
 * 注册回调路由
 *
 * 【健康检查】
 * RegisterRoute() 会自动注册健康检查接口（路径：回调接口 + "/health"）
 *
 * 示例：
 * type MyCallback struct{}
 * func (c *MyCallback) RoutePath() string { return "/api/callback/job" }
 * func (c *MyCallback) SecretKey() string { return "my-secret" }
 * func (c *MyCallback) Endpoint() string { return "数据同步服务" }
 * func (c *MyCallback) RunJob(param map[string]interface{}) *Result { return Ok(nil) }
 *
 * mux := http.NewServeMux()
 * RegisterRoute(mux, &MyCallback{})
 *
 * // 健康检查接口自动注册为：/api/callback/job/health
 */
func RegisterRoute(mux *http.ServeMux, base ScheduledJobBase) {
	mux.HandleFunc(base.RoutePath(), func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			Handle(base, w, r)
		} else {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		}
	})

	// 自动注册健康检查接口（路径：回调接口 + "/health"）
	healthPath := base.RoutePath() + "/health"
	mux.HandleFunc(healthPath, func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet {
			HealthCheck(base, w, r)
		} else {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		}
	})
}

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

func GetString(param map[string]interface{}, key string, defaultVal string) string {
	if val, ok := param[key]; ok {
		if v, ok := val.(string); ok {
			return v
		}
	}
	return defaultVal
}
