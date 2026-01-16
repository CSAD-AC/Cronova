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
	RunJob(param map[string]interface{}) *Result
}

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

func validateMethodOverrides(base ScheduledJobBase, requestID string) error {
	val := reflect.ValueOf(base)
	if val.Kind() == reflect.Ptr {
		val = val.Elem()
	}

	typeName := val.Type().Name()
	if typeName == placeholderStructName {
		return fmt.Errorf("请勿直接使用基类，请创建结构体并实现 RoutePath()、SecretKey() 和 RunJob() 方法")
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

func RegisterRoute(mux *http.ServeMux, base ScheduledJobBase) {
	mux.HandleFunc(base.RoutePath(), func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			Handle(base, w, r)
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
