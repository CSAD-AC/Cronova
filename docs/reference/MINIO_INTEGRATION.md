# MinIO 集成说明

## 概述

本系统已集成MinIO对象存储服务，用于存储和管理任务执行日志文件。前端可以直接从MinIO下载日志文件，无需通过后端代理。

## 配置

### 环境变量

在项目根目录创建 `.env` 文件，配置以下变量：

```env
# MinIO配置
VITE_MINIO_URL=http://localhost:9000
VITE_MINIO_ACCESS_TOKEN=your-minio-access-token
VITE_MINIO_SECRET_TOKEN=your-minio-secret-token
```

### CORS配置

MinIO服务器需要配置CORS策略以允许前端访问：

```json
{
  "CORSRules": [
    {
      "AllowedOrigins": ["http://localhost:5173", "http://localhost:8080"],
      "AllowedMethods": ["GET", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"]
    }
  ]
}
```

## 使用方式

### 1. 获取日志文件信息

```typescript
import { taskRunApi } from '@/services'

const logFileInfo = await taskRunApi.getLogFileInfo(taskRunId)
console.log(logFileInfo)
// 输出: { bucket: "task-logs", objectName: "2026/01/22/task-run-id.log", fileName: "task_log_xxx.log" }
```

### 2. 直接从MinIO下载

```typescript
import { minioApi } from '@/services'

// 下载文件（返回Blob）
const blob = await minioApi.downloadFile(logFileInfo)

// 下载文本内容
const text = await minioApi.downloadTextFile(logFileInfo)
```

### 3. 检查文件是否存在

```typescript
const exists = await minioApi.fileExists(logFileInfo)
```

## API接口

### 后端新增接口

- `GET /api/task-run/{taskRunId}/log/info` - 获取日志文件信息
- `GET /api/task-run/{taskRunId}/log/download` - 保留原有接口（已标记废弃）

### 响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "bucket": "task-logs",
    "objectName": "2026/01/22/task-run-id.log",
    "fileName": "task_log_xxx.log",
    "signedUrl": "https://minio.example.com/..." // 可选字段
  }
}
```

## 优势

1. **性能优化**: 减少服务器带宽占用
2. **扩展性**: 支持大文件直接传输
3. **可靠性**: 利用MinIO的高可用性
4. **安全性**: 支持预签名URL和访问控制

## 注意事项

1. **CORS配置**: 确保MinIO服务器正确配置CORS策略
2. **访问权限**: 确保前端应用有访问MinIO的权限
3. **网络连通**: 确保前端可以访问MinIO服务器
4. **大文件处理**: 对于超大日志文件，建议使用分块下载
