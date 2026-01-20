# Go 回调任务对接文档

## 一、概述

本文档指导 Go 项目如何对接任务调度器的回调功能。当任务执行完成后，调度器会向配置的回调 URL 发送 HTTP 请求，接收方需要：

1. **校验请求头**：验证 `X-Secret` 密钥和 `X-Request-Id` 请求 ID
2. **执行业务逻辑**：执行定时任务的实际逻辑
3. **返回结果**：使用统一的 `Result` 结构体返回执行结果

## 二、请求规范

### 2.1 请求方法

回调接口使用不同的 HTTP 方法：
- **任务回调接口**：POST 方法
- **健康检查接口**：GET 方法

### 2.2 请求头说明

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Request-Id` | 是 | 请求唯一标识，用于日志追踪和请求关联 |
| `X-Secret` | 是 | 密钥，用于身份验证，需与回调配置中的密钥一致 |

**请求头示例**：
```http
POST /api/callback/data-sync HTTP/1.1
Host: your-service.com
X-Request-Id: abc123def456
X-Secret: your-secret-key
```

### 2.3 响应规范

所有回调接口必须返回统一的 JSON 格式，包含 `code`、`msg`、`data` 字段。详细响应示例请参见各接口说明：
- 任务回调接口响应：见 [3.4 响应示例](#34-响应示例)
- 健康检查接口响应：见 [4.3 响应示例](#43-响应示例)

## 三、任务回调接口

### 3.1 接口说明

任务回调接口是调度器触发任务执行时调用的主接口，用于接收调度器的执行请求并执行业务逻辑。

**接口约定**：
- **路径格式**：`<回调接口路径>`（无 `/job` 后缀）
- **示例**：`/api/callback/data-sync`
- **请求方法**：POST
- **功能**：接收调度器任务执行请求，执行业务逻辑，返回执行结果

### 3.2 请求示例

```http
POST /api/callback/data-sync HTTP/1.1
Host: your-service.com
X-Request-Id: task-exec-123456
X-Secret: your-secret-key
Content-Type: application/json

{
    "taskId": "task-001",
    "taskRunId": "run-001",
    "taskName": "数据同步任务",
    "taskDescription": "同步H4A员工信息到ES",
    "executeTime": "2026-01-20T10:00:00"
}
```

### 3.3 请求体字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | string | 任务定义ID |
| `taskRunId` | string | 任务运行实例ID |
| `taskName` | string | 任务名称 |
| `taskDescription` | string | 任务描述（可选） |
| `executeTime` | string | 任务执行时间（可选） |

### 3.4 响应示例

**成功**：
```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "success": true,
        "records_processed": 100,
        "duration_ms": 1500
    }
}
```

**失败**：
```json
{
    "code": 500,
    "msg": "执行失败: 数据库连接超时",
    "data": null
}
```

## 四、健康检查接口

### 4.1 接口说明

健康检查接口用于验证回调服务是否正常运行，验证请求头（X-Secret 和 X-Request-Id）。

**接口约定**：
- **路径格式**：`<回调接口路径>/health`（强制约定）
- **示例**：
  - 回调接口：`/api/callback/data-sync`
  - 健康接口：`/api/callback/data-sync/health`
- **请求方法**：GET
- **功能**：仅验证 X-Secret 和 X-Request-Id 请求头，不执行业务逻辑
- **要求**：健康接口和回调接口必须使用相同的 BaseUrl

**自动注册**：`RegisterRoute()` 函数会自动注册健康检查接口，无需额外代码。

### 4.2 请求示例

```http
GET /api/callback/data-sync/health HTTP/1.1
Host: your-service.com
X-Request-Id: abc123def456
X-Secret: your-secret-key
```

### 4.3 响应示例

**成功**：
```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "endpoint": "数据同步服务",
        "message": "服务正常",
        "status": "UP",
        "timestamp": "2026-01-20T10:03:24.462013500"
    }
}
```

**失败（密钥错误）**：
```json
{
    "code": 401,
    "msg": "密钥验证失败",
    "data": null
}
```

**失败（缺少请求头）**：
```json
{
    "code": 400,
    "msg": "缺少请求头 X-Request-Id",
    "data": null
}
```

### 4.4 data 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | string | 状态，固定为 "UP" |
| `timestamp` | string | 时间戳，格式：RFC3339 |
| `endpoint` | string | 模块名称，如"数据同步服务"、"报表生成服务" |
| `message` | string | 状态描述，固定为 "服务正常" |

## 五、快速集成

### 5.1 引入基类

将 `scheduled_job_base.go` 复制到你的项目中，该文件提供了：

- `Result` 结构体：统一的返回对象
- `ScheduledJobBase` 接口：回调处理接口
- `Handle` 函数：统一处理入口
- `RegisterRoute` 函数：路由注册辅助函数

### 5.2 创建回调处理器

创建结构体实现 `ScheduledJobBase` 接口，并实现三个方法：

```go
package main

import (
    "fmt"
    "net/http"
)

type MyJobCallback struct{}

func (c *MyJobCallback) RoutePath() string {
    return "/api/callback/my-job"
}

func (c *MyJobCallback) SecretKey() string {
    // 【注意】此密钥需要与调度器回调配置中的密钥一致
    return "your-secret-key-here"
}

func (c *MyJobCallback) Endpoint() string {
    return "我的任务服务"
}

func (c *MyJobCallback) RunJob(param map[string]interface{}) *Result {
    requestID := GetString(param, "request_id", "")

    // 【规范】日志开头带上 [request_id]
    fmt.Printf("[%s] 开始执行任务，参数: %v\n", requestID, param)

    resultData := c.doWork(param)

    fmt.Printf("[%s] 任务执行成功，结果: %v\n", requestID, resultData)
    return Ok(resultData)
}

func (c *MyJobCallback) doWork(param map[string]interface{}) map[string]interface{} {
    return map[string]interface{}{
        "status":    "completed",
        "processed": 100,
    }
}

// ===== 启动服务 =====
func main() {
    mux := http.NewServeMux()
    RegisterRoute(mux, &MyJobCallback{})

    fmt.Println("服务已启动")
    fmt.Println("📌 回调接口: POST /api/callback/my-job")
    fmt.Println("📌 健康接口: GET  /api/callback/my-job/health（自动注册）")
    http.ListenAndServe(":8080", mux)
}
```

**注意**：`RegisterRoute()` 函数会自动注册健康检查接口（路径：回调接口 + "/health"），无需额外代码。

## 六、完整示例

### 6.1 完整的回调服务示例

```go
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
 * 【自动注册】
 * RegisterRoute() 函数会自动注册健康检查接口
 */

type DataSyncResult struct {
    RecordsProcessed int   `json:"records_processed"`
    DurationMs       int64 `json:"duration_ms"`
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
    return "数据同步服务"
}

func (c *DataSyncCallback) RunJob(param map[string]interface{}) *Result {
    requestID := GetString(param, "request_id", "")

    // 【规范】日志中必须带上 [request_id]
    fmt.Printf("[%s] 开始执行数据同步任务\n", requestID)

    startTime := time.Now()

    // 执行同步逻辑
    result := c.executeSync()

    durationMs := time.Since(startTime).Milliseconds()
    fmt.Printf("[%s] 同步完成: %d 条记录，耗时: %dms\n", 
               requestID, result.RecordsProcessed, durationMs)

    return Ok(result)
}

func (c *DataSyncCallback) executeSync() *DataSyncResult {
    startTime := time.Now()

    time.Sleep(time.Duration(1000+c.rand.Intn(2000)) * time.Millisecond)

    records := 100 + c.rand.Intn(900)

    return &DataSyncResult{
        RecordsProcessed: records,
        DurationMs:       time.Since(startTime).Milliseconds(),
        Status:           "success",
        Message:          fmt.Sprintf("成功同步 %d 条记录", records),
    }
}

// ===== 启动入口 =====
func main() {
    mux := http.NewServeMux()

    // 注册数据同步回调路由（会自动注册健康检查接口）
    RegisterRoute(mux, NewDataSyncCallback())

    fmt.Println("========================================")
    fmt.Println("✅ 数据同步回调服务已启动")
    fmt.Println("📌 回调接口: POST /api/callback/data-sync")
    fmt.Println("📌 健康接口: GET  /api/callback/data-sync/health（自动注册）")
    fmt.Println("🔑 密钥: data-sync-secret-key-2026")
    fmt.Println("========================================")

    http.ListenAndServe(":8080", mux)
}
```

### 6.2 Result 结构体说明

```go
type Result struct {
    Code       int         `json:"code"`
    Msg        string      `json:"msg"`
    Data       interface{} `json:"data"`
    CostMillis int64       `json:"cost_millis"`
    RequestID  string      `json:"request_id"`
}

// 创建成功响应
func Ok(data interface{}) *Result {
    return &Result{Code: 200, Msg: "success", Data: data}
}

// 创建失败响应
func Fail(code int, msg string) *Result {
    return &Result{Code: code, Msg: msg}
}

// 转换为 JSON
func (r *Result) ToJSON() ([]byte, error) {
    return json.Marshal(r)
}
```

### 6.3 辅助函数

基类提供了常用的辅助函数：

```go
// 获取整型参数
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

// 获取字符串参数
func GetString(param map[string]interface{}, key string, defaultVal string) string {
    if val, ok := param[key]; ok {
        if v, ok := val.(string); ok {
            return v
        }
    }
    return defaultVal
}
```

## 七、日志规范

### 7.1 为什么要记录 RequestId

当出现问题需要排查时，通过 `X-Request-Id` 可以快速定位一次请求的所有日志。

### 7.2 日志示例

```go
package main

import (
    "log"
    "net/http"
)

type ExampleCallback struct{}

func (c *ExampleCallback) RoutePath() string {
    return "/api/callback/example"
}

func (c *ExampleCallback) SecretKey() string {
    return "example-secret-key"
}

func (c *ExampleCallback) Endpoint() string {
    return "示例服务"
}

func (c *ExampleCallback) RunJob(param map[string]interface{}) *Result {
    requestID := GetString(param, "request_id", "")

    // 【规范】日志开头带上 [request_id]
    log.Printf("[%s] 任务开始执行\n", requestID)

    startTime := time.Now()

    // 执行业务逻辑
    result, err := c.doWork(param)
    if err != nil {
        log.Printf("[%s] 任务执行失败: %v\n", requestID, err)
        return Fail(500, "执行失败: "+err.Error())
    }

    duration := time.Since(startTime)
    log.Printf("[%s] 任务执行成功，耗时: %v\n", requestID, duration)

    return Ok(result)
}

func (c *ExampleCallback) doWork(param map[string]interface{}) (interface{}, error) {
    return nil, nil
}

func main() {
    mux := http.NewServeMux()
    RegisterRoute(mux, &ExampleCallback{})
    http.ListenAndServe(":8080", mux)
}
```

### 7.3 日志格式建议

```
2024/01/19 10:30:00 [abc123def456] 任务开始执行
2024/01/19 10:30:01 [abc123def456] 任务执行成功，耗时: 1.5s
2024/01/19 10:30:02 [abc123def456] 任务执行失败: 数据格式错误
```

## 八、使用其他 Web 框架

### 8.1 Gin 框架示例

```go
package main

import (
    "fmt"
    "net/http"
    "time"

    "github.com/gin-gonic/gin"
)

type ResponseBody struct {
    Code       int         `json:"code"`
    Msg        string      `json:"msg"`
    Data       interface{} `json:"data"`
    CostMillis int64       `json:"cost_millis"`
    RequestID  string      `json:"request_id"`
}

const SECRET_KEY = "your-secret-key"

func main() {
    r := gin.Default()

    r.POST("/api/callback/job", func(c *gin.Context) {
        startTime := time.Now()
        requestID := c.GetHeader("X-Request-Id")
        secret := c.GetHeader("X-Secret")

        // 校验密钥
        if secret != SECRET_KEY {
            c.JSON(401, ResponseBody{
                Code:      401,
                Msg:       "密钥验证失败",
                RequestID: requestID,
            })
            return
        }

        // 记录日志
        fmt.Printf("[%s] 开始执行任务\n", requestID)

        // 执行业务逻辑
        result := doWork()

        costMillis := time.Since(startTime).Milliseconds()
        fmt.Printf("[%s] 任务执行成功，耗时: %dms\n", requestID, costMillis)

        c.JSON(http.StatusOK, ResponseBody{
            Code:       200,
            Msg:        "success",
            Data:       result,
            CostMillis: costMillis,
            RequestID:  requestID,
        })
    })

    r.Run(":8080")
}

func doWork() interface{} {
    return map[string]interface{}{
        "status": "completed",
    }
}
```

### 8.2 Echo 框架示例

```go
package main

import (
    "fmt"
    "net/http"
    "time"

    "github.com/labstack/echo/v4"
)

type ResponseBody struct {
    Code       int         `json:"code"`
    Msg        string      `json:"msg"`
    Data       interface{} `json:"data"`
    CostMillis int64       `json:"cost_millis"`
    RequestID  string      `json:"request_id"`
}

const SECRET_KEY = "your-secret-key"

func main() {
    e := echo.New()

    e.POST("/api/callback/job", func(c echo.Context) error {
        startTime := time.Now()
        requestID := c.Request().Header.Get("X-Request-Id")
        secret := c.Request().Header.Get("X-Secret")

        // 校验密钥
        if secret != SECRET_KEY {
            return c.JSON(401, ResponseBody{
                Code:      401,
                Msg:       "密钥验证失败",
                RequestID: requestID,
            })
        }

        fmt.Printf("[%s] 开始执行任务\n", requestID)

        // 执行业务逻辑
        result := doWork()

        costMillis := time.Since(startTime).Milliseconds()
        fmt.Printf("[%s] 任务执行成功，耗时: %dms\n", requestID, costMillis)

        return c.JSON(http.StatusOK, ResponseBody{
            Code:       200,
            Msg:        "success",
            Data:       result,
            CostMillis: costMillis,
            RequestID:  requestID,
        })
    })

    e.Logger.Fatal(e.Start(":8080"))
}

func doWork() interface{} {
    return map[string]interface{}{
        "status": "completed",
    }
}
```

## 九、常见问题

### Q1: 密钥验证失败怎么办？

1. 检查调度器配置中的密钥与代码中 `SecretKey()` 返回的密钥是否一致
2. 检查请求头是否正确传递了 `X-Secret`
3. 检查密钥是否包含特殊字符

### Q2: 如何处理请求体为 null 的情况？

基类已经处理了这种情况，会自动将 `nil` 请求体转换为空 Map：

```go
// scheduled_job_base.go 中的处理
var param map[string]interface{}
if r.Body != nil {
    json.NewDecoder(r.Body).Decode(&param)
}
if param == nil {
    param = make(map[string]interface{})
}
```

### Q3: 如何在业务逻辑中获取 RequestId？

`request_id` 会被自动注入到 `param` 字典中：

```go
func (c *MyCallback) RunJob(param map[string]interface{}) *Result {
    requestID := GetString(param, "request_id", "")
    // 使用 requestID 进行日志记录
    fmt.Printf("[%s] 任务开始\n", requestID)
}
```

### Q4: 返回结果中的 Data 字段应该如何组织？

`Data` 字段可以是任意结构，建议包含以下信息：

```go
type SyncResult struct {
    Summary   string                 `json:"summary"`
    Details   map[string]interface{} `json:"details"`
    Metrics   map[string]interface{} `json:"metrics"`
}

result := map[string]interface{}{
    "summary": "执行结果摘要",
    "details": map[string]interface{}{
        "processed": 100,
        "failed":    0,
    },
    "metrics": map[string]interface{}{
        "duration_ms": 1500,
        "memory_mb":   50,
    },
}
return Ok(result)
```

## 十、最佳实践

1. **日志规范**：所有日志必须带上 `[request_id]` 前缀
2. **异常处理**：使用 `defer recover()` 捕获 panic，记录错误日志，返回友好的错误信息
3. **性能监控**：记录执行耗时，便于性能分析
4. **幂等性**：确保回调接口支持幂等调用
5. **超时控制**：设置合理的超时时间
6. **数据验证**：对请求参数进行校验
