# Java 回调任务对接文档

## 一、概述

本文档指导 Java 项目如何对接任务调度器的回调功能。当任务执行完成后，调度器会向配置的回调 URL 发送 HTTP 请求，接收方需要：

1. **校验请求头**：验证 `X-Secret` 密钥和 `X-Request-Id` 请求 ID
2. **执行业务逻辑**：执行定时任务的实际逻辑
3. **返回结果**：使用统一的 `Result` 对象返回执行结果

## 二、请求规范

### 2.1 请求方法

回调接口统一使用 **POST** 方法。

### 2.2 请求头说明

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Request-Id` | 是 | 请求唯一标识，用于日志追踪和请求关联 |
| `X-Secret` | 是 | 密钥，用于身份验证，需与回调配置中的密钥一致 |

**请求头示例**：
```http
POST /api/callback/job HTTP/1.1
Host: your-service.com
X-Request-Id: abc123def456
X-Secret: your-secret-key
```

### 2.3 请求体说明

回调接口**不需要请求体**，请求体为空。

## 三、健康检查接口

### 3.1 接口说明

健康检查接口用于验证回调服务是否正常运行，验证请求头（X-Secret 和 X-Request-Id）。

**接口约定**：
- **路径格式**：`<回调接口路径>/health`（强制约定）
- **示例**：
  - 回调接口：`/api/callback/data-sync/job`
  - 健康接口：`/api/callback/data-sync/health`
- **请求方法**：GET
- **功能**：仅验证 X-Secret 和 X-Request-Id 请求头，不执行业务逻辑
- **要求**：健康接口和回调接口必须使用相同的 BaseUrl

### 3.2 请求示例

```http
GET /api/callback/data-sync/health HTTP/1.1
Host: your-service.com
X-Request-Id: abc123def456
X-Secret: your-secret-key
```

### 3.3 响应示例

**成功**：
```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "status": "UP",
        "timestamp": "2024-01-19T10:30:00",
        "endpoint": "数据同步服务",
        "message": "服务正常"
    },
    "costMillis": 5,
    "requestId": "abc123def456"
}
```

**失败（密钥错误）**：
```json
{
    "code": 401,
    "msg": "密钥验证失败",
    "data": null,
    "requestId": "abc123def456"
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

### 3.4 data 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | string | 状态，固定为 "UP" |
| `timestamp` | string | 时间戳，格式：yyyy-MM-dd'T'HH:mm:ss |
| `endpoint` | string | 模块名称，如"数据同步服务"、"报表生成服务" |
| `message` | string | 状态描述，固定为 "服务正常" |

## 四、响应规范

### 4.1 响应格式

所有回调接口必须返回统一的 JSON 格式，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 状态码，200 表示成功，其他值表示失败 |
| `msg` | String | 状态消息 |
| `data` | Object | 详细的执行结果，可包含任意结构 |
| `costMillis` | long | 执行耗时（毫秒），可选 |
| `requestId` | String | 请求 ID，用于日志追踪，可选 |

**成功响应示例**：
```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "recordsProcessed": 100,
        "durationMs": 1500,
        "status": "completed"
    },
    "costMillis": 1523,
    "requestId": "abc123def456"
}
```

**失败响应示例**：
```json
{
    "code": 500,
    "msg": "执行失败: 数据库连接超时",
    "data": null,
    "costMillis": 5000,
    "requestId": "abc123def456"
}
```

## 四、快速集成

### 4.1 引入基类

将 `ScheduledJobBase.java` 复制到你的项目中，该类提供了完整的回调处理框架。

### 4.2 创建回调控制器

创建控制器类继承 `ScheduledJobBase`，并实现两个抽象方法：

```java
package com.example.controller;

import com.example.base.ScheduledJobBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 数据同步回调控制器
 *
 * 【接口说明】
 * 1. 回调接口：POST /api/callback/data-sync
 * 2. 健康接口：GET  /api/callback/data-sync/health
 */
@Slf4j
@RestController
@RequestMapping("/api/callback/data-sync")
public class DataSyncCallback extends ScheduledJobBase {

    @PostMapping
    public Result<?> handle(@RequestBody(required = false) Map<String, Object> param,
                           HttpServletRequest request) {
        return super.handle(request, param);
    }

    @GetMapping("/health")
    public Result<?> healthCheck(HttpServletRequest request) {
        return super.healthCheck(request);
    }

    @Override
    protected String secretKey() {
        return "your-secret-key-here";
    }

    @Override
    protected Result<?> runJob(Map<String, Object> param) throws Exception {
        String requestId = getRequestIdFromThreadLocal();
        log.info("[{}] 开始执行数据同步任务，参数: {}", requestId, param);

        try {
            Map<String, Object> result = executeSyncLogic(param);
            log.info("[{}] 数据同步任务执行成功，处理记录数: {}",
                     requestId, result.get("recordsProcessed"));
            return Result.ok(result);

        } catch (Exception e) {
            log.error("[{}] 数据同步任务执行失败: {}", requestId, e.getMessage(), e);
            return Result.fail(500, "执行失败: " + e.getMessage());
        }
    }

    private Map<String, Object> executeSyncLogic(Map<String, Object> param) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("recordsProcessed", 100);
        result.put("durationMs", 1500L);
        result.put("status", "completed");
        return result;
    }

    private String getRequestIdFromThreadLocal() {
        return "";
    }
}
```

### 4.3 Result 类实现

## 七、日志规范

### 5.1 为什么要记录 RequestId

当出现问题需要排查时，通过 `X-Request-Id` 可以快速定位一次请求的所有日志。建议在所有日志中都带上 `requestId`。

### 5.2 日志示例

```java
@Slf4j
@RestController
@RequestMapping("/api/callback")
public class ExampleCallback extends ScheduledJobBase {

    @PostMapping("/job")
    public Result<?> handle(@RequestBody(required = false) Map<String, Object> param, 
                           HttpServletRequest request) {
        return super.handle(request, param);
    }

    @Override
    protected Result<?> runJob(Map<String, Object> param) throws Exception {
        String requestId = getCurrentRequestId();
        long startTime = System.currentTimeMillis();

        // 【规范】日志开头带上 [requestId]
        log.info("[{}] 任务开始执行，参数: {}", requestId, param);

        try {
            // 业务逻辑
            Object result = doWork(param);

            long cost = System.currentTimeMillis() - startTime;
            log.info("[{}] 任务执行成功，耗时: {}ms，结果: {}", requestId, cost, result);

            return Result.ok(result).costMillis(cost).requestId(requestId);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("[{}] 任务执行失败，耗时: {}ms，错误: {}", 
                      requestId, cost, e.getMessage(), e);

            return Result.fail(500, "执行失败: " + e.getMessage())
                        .costMillis(cost)
                        .requestId(requestId);
        }
    }

    private String getCurrentRequestId() {
        // 从请求上下文获取
        return "";
    }

    private Object doWork(Map<String, Object> param) {
        return null;
    }
}
```

### 5.3 日志格式建议

```
[2024-01-19 10:30:00.123] [INFO ] [abc123def456] - 任务开始执行
[2024-01-19 10:30:01.456] [INFO ] [abc123def456] - 任务执行成功，耗时: 1333ms
[2024-01-19 10:30:02.000] [ERROR] [abc123def456] - 任务执行失败: 数据格式错误
```

## 六、完整示例

### 6.1 完整的回调控制器示例

```java
package com.example.controller;

import com.example.base.ScheduledJobBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;

/**
 * 数据同步回调控制器
 * 
 * 【功能说明】
 * 1. 提供 POST 接口接收调度器的回调请求
 * 2. 校验 X-Secret 密钥
 * 3. 执行数据同步业务逻辑
 * 4. 返回执行结果
 */
@Slf4j
@RestController
@RequestMapping("/api/callback/data-sync")
@AllArgsConstructor
public class DataSyncCallback extends ScheduledJobBase {

    private final DataSyncService dataSyncService;

    /**
     * 回调入口
     * 
     * @param param 请求参数（可选）
     * @param request HTTP 请求
     * @return 执行结果
     */
    @PostMapping("/job")
    public Result<?> handle(@RequestBody(required = false) Map<String, Object> param, 
                           HttpServletRequest request) {
        return super.handle(request, param);
    }

    /**
     * 返回密钥
     * 
     * 【注意】此密钥需要与调度器回调配置中的密钥保持一致
     */
    @Override
    protected String secretKey() {
        return "data-sync-secret-key-2026";
    }

    /**
     * 执行业务逻辑
     */
    @Override
    protected Result<?> runJob(Map<String, Object> param) throws Exception {
        String requestId = getCurrentRequestId();
        long startTime = System.currentTimeMillis();

        log.info("[{}] 开始执行数据同步任务，参数: {}", requestId, param);

        try {
            // 执行同步逻辑
            SyncResult result = dataSyncService.sync(param);

            long cost = System.currentTimeMillis() - startTime;
            log.info("[{}] 数据同步完成，处理记录: {}，耗时: {}ms", 
                     requestId, result.getRecordsProcessed(), cost);

            return Result.ok(result).costMillis(cost).requestId(requestId);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("[{}] 数据同步失败，耗时: {}ms，错误: {}", 
                      requestId, cost, e.getMessage(), e);

            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            errorData.put("phase", "sync");

            return Result.fail(500, "数据同步失败: " + e.getMessage())
                        .data(errorData)
                        .costMillis(cost)
                        .requestId(requestId);
        }
    }

    private String getCurrentRequestId() {
        // 从请求上下文中获取，例如从 ThreadLocal 或 MDC 中获取
        return "";
    }
}

/**
 * 同步结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class SyncResult {
    private int recordsProcessed;
    private long durationMs;
    private String status;
    private String message;
}

/**
 * 业务服务
 */
@Service
class DataSyncService {
    public SyncResult sync(Map<String, Object> param) {
        // 业务逻辑实现
        return new SyncResult(100, 1500L, "completed", "同步成功");
    }
}
```

### 6.2 配置说明

在调度器管理界面配置回调时，需要填写以下信息：

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| 回调URL | 回调接口地址 | `https://your-service.com/api/callback/data-sync/job` |
| 回调方法 | HTTP 方法 | `POST` |
| 密钥 | X-Secret 请求头的值 | `data-sync-secret-key-2026` |
| 超时时间 | 请求超时时间（秒） | `30` |
| 重试次数 | 失败后重试次数 | `3` |
| 重试间隔 | 重试间隔时间（秒） | `60` |

## 七、常见问题

### Q1: 密钥验证失败怎么办？

1. 检查调度器配置中的密钥与代码中 `secretKey()` 返回的密钥是否一致
2. 检查请求头是否正确传递了 `X-Secret`
3. 检查密钥是否包含特殊字符，如有需要 URL 编码

### Q2: 如何处理请求体为 null 的情况？

基类已经处理了这种情况，会将 `null` 请求体转换为空 Map：

```java
// ScheduledJobBase 中的处理
Result<?> result = runJob(param == null ? Map.of() : param);
```

### Q3: 如何在业务逻辑中获取 RequestId？

可以通过以下方式获取：

1. **从请求对象获取**：
```java
String requestId = request.getHeader("X-Request-Id");
```

2. **从 ThreadLocal/MDC 获取**（需要自行实现拦截器）

### Q4: 返回结果中的 data 字段应该如何组织？

`data` 字段可以是任意 JSON 结构，建议包含以下信息：

```json
{
    "data": {
        "summary": "执行结果摘要",
        "details": {
            // 详细的执行数据
        },
        "metrics": {
            // 性能指标
        }
    }
}
```

## 八、最佳实践

1. **日志规范**：所有日志必须带上 `[requestId]` 前缀
2. **异常处理**：捕获所有异常，记录错误日志，返回友好的错误信息
3. **性能监控**：记录执行耗时，便于性能分析
4. **幂等性**：确保回调接口支持幂等调用（同一 requestId 多次调用结果一致）
5. **超时控制**：设置合理的超时时间，避免长时间阻塞
6. **数据验证**：对请求参数进行校验，拒绝不合法的请求
