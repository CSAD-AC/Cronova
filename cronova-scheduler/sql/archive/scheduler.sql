-- MySQL dump 10.13  Distrib 8.0.42, for Win64 (x86_64)
--
-- Host: localhost    Database: scheduler
-- ------------------------------------------------------
-- Server version	8.0.42

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `aksks`
--

DROP TABLE IF EXISTS `aksks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `aksks` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键UUID',
  `access_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Access Key',
  `secret_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Secret Key',
  `worker_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联的 Worker ID',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：active/inactive',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `access_key` (`access_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AKSK 认证表（用于 Worker 执行器的 HTTP 通信鉴权）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aksks`
--

LOCK TABLES `aksks` WRITE;
/*!40000 ALTER TABLE `aksks` DISABLE KEYS */;
INSERT INTO `aksks` VALUES ('e72300e5f10ec7c3af89206c7a7a44ed','REDACTED','REDACTED','74cb00be2c93290d8eeb17eeed7f3809','active','2026-03-01 22:35:50','2026-03-01 22:35:50');
/*!40000 ALTER TABLE `aksks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `callback_job_configs`
--

DROP TABLE IF EXISTS `callback_job_configs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callback_job_configs` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置ID（UUID）',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联tasks.id',
  `callback_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回调URL',
  `callback_method` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT 'POST' COMMENT '回调方法：GET/POST/PUT/DELETE',
  `secret_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '认证秘钥，用于 X-Secret 请求头',
  `last_callback_time` datetime DEFAULT NULL COMMENT '上次回调时间',
  `last_callback_status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上次回调状态：success/failed/timeout',
  `total_callback_count` int DEFAULT '0' COMMENT '总回调次数',
  `success_count` int DEFAULT '0' COMMENT '成功次数',
  `failed_count` int DEFAULT '0' COMMENT '失败次数',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_callback_url` (`callback_url`),
  CONSTRAINT `callback_job_configs_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP回调任务配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `callback_job_configs`
--

LOCK TABLES `callback_job_configs` WRITE;
/*!40000 ALTER TABLE `callback_job_configs` DISABLE KEYS */;
/*!40000 ALTER TABLE `callback_job_configs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `callback_job_runs`
--

DROP TABLE IF EXISTS `callback_job_runs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callback_job_runs` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行记录ID（UUID）',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联tasks.id',
  `config_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联callback_job_configs.id',
  `task_run_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联task_run.id',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT (uuid()) COMMENT '请求ID（X-Request-Id），用于请求追踪，数据库自动生成',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行状态：pending/running/success/failed/timeout',
  `request_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际请求URL',
  `request_method` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际请求方法',
  `request_path` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求数据MinIO路径（包含headers和body）',
  `response_path` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '响应数据MinIO路径（包含statusCode、headers和body）',
  `response_status_code` int DEFAULT NULL COMMENT '响应状态码',
  `response_time_ms` int DEFAULT NULL COMMENT '响应时间（毫秒）',
  `retry_index` int DEFAULT '0' COMMENT '重试次数索引（0表示首次执行）',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` int DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `task_run_id` (`task_run_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_request_id` (`request_id`),
  CONSTRAINT `callback_job_runs_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `callback_job_runs_ibfk_2` FOREIGN KEY (`config_id`) REFERENCES `callback_job_configs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `callback_job_runs_ibfk_3` FOREIGN KEY (`task_run_id`) REFERENCES `task_run` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP回调执行记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `callback_job_runs`
--

LOCK TABLES `callback_job_runs` WRITE;
/*!40000 ALTER TABLE `callback_job_runs` DISABLE KEYS */;
/*!40000 ALTER TABLE `callback_job_runs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (1,'admin','2026-03-02 14:28:56'),(2,'user','2026-03-02 14:28:56');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `script_tags`
--

DROP TABLE IF EXISTS `script_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `script_tags` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `script_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'script ID',
  `tag_id` int NOT NULL COMMENT '标签ID',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_script_tag` (`script_id`,`tag_id`),
  KEY `tag_id` (`tag_id`),
  CONSTRAINT `script_tags_ibfk_1` FOREIGN KEY (`script_id`) REFERENCES `scripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `script_tags_ibfk_2` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='script标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `script_tags`
--

LOCK TABLES `script_tags` WRITE;
/*!40000 ALTER TABLE `script_tags` DISABLE KEYS */;
/*!40000 ALTER TABLE `script_tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scripts`
--

DROP TABLE IF EXISTS `scripts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scripts` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID (UUID)',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '脚本名称',
  `path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '脚本路径',
  `is_directory` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为目录类型（1=是，0=否）',
  `main_file` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目录类型时的主入口文件路径',
  `parent_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父脚本ID，用于构建层级关系',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scripts`
--

LOCK TABLES `scripts` WRITE;
/*!40000 ALTER TABLE `scripts` DISABLE KEYS */;
INSERT INTO `scripts` VALUES ('005d3c6684c957078e66748b5001afeb','main.py','task-engine/scripts/76d49f5f-e2b0-4a3a-8938-e9c1e4247101/main.py',0,NULL,'26b60f9bf737a189804e0e7284276b9f','2026-03-02 06:35:33','2026-03-02 06:35:33'),('26b60f9bf737a189804e0e7284276b9f','test1','task-engine/scripts/76d49f5f-e2b0-4a3a-8938-e9c1e4247101/',1,NULL,NULL,'2026-03-02 06:35:33','2026-03-02 06:35:33'),('c4a37fd513081b2f97cd504eba4a70ea','test.py','task-engine/scripts/76d49f5f-e2b0-4a3a-8938-e9c1e4247101/test.py',0,NULL,'26b60f9bf737a189804e0e7284276b9f','2026-03-02 06:35:33','2026-03-02 06:35:33');
/*!40000 ALTER TABLE `scripts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tags`
--

DROP TABLE IF EXISTS `tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tags` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称，如: V1, V2, FastAPI,',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签描述',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表（用于Docker镜像标签和任务匹配）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tags`
--

LOCK TABLES `tags` WRITE;
/*!40000 ALTER TABLE `tags` DISABLE KEYS */;
INSERT INTO `tags` VALUES (34,'V1','Worker注册时自动创建','2026-03-01 22:35:50'),(35,'Python3.11','Worker注册时自动创建','2026-03-01 22:35:50');
/*!40000 ALTER TABLE `tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_log`
--

DROP TABLE IF EXISTS `task_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_log` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务日志 ID(UUID)',
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联 task_run.id',
  `stdout_path` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'stdout 日志 MinIO 路径',
  `stderr_path` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'stderr 日志 MinIO 路径',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务日志表（仅存 MinIO 路径）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_log`
--

LOCK TABLES `task_log` WRITE;
/*!40000 ALTER TABLE `task_log` DISABLE KEYS */;
INSERT INTO `task_log` VALUES ('32acc271221858d936529e605fc62003','560c57e694af577d81b84e6f2a6fdea0','task-engine/task-logs/d83d5ba5716a8282d439883132a37bc3/560c57e694af577d81b84e6f2a6fdea0.log','task-engine/task-logs/d83d5ba5716a8282d439883132a37bc3/560c57e694af577d81b84e6f2a6fdea0.log','2026-03-01 22:36:36');
/*!40000 ALTER TABLE `task_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_run`
--

DROP TABLE IF EXISTS `task_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_run` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务运行实例 ID(UUID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联 task.task_id',
  `worker_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际执行的 Worker',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态: pending/running/success/failed/timeout',
  `exit_code` int DEFAULT NULL COMMENT '进程退出码',
  `start_time` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '失败原因',
  `retry_num` int DEFAULT '0' COMMENT '当前重试次数',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_heartbeat_time` timestamp NULL DEFAULT NULL COMMENT '上次心跳/状态上报时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行实例表（一次运行一条记录）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_run`
--

LOCK TABLES `task_run` WRITE;
/*!40000 ALTER TABLE `task_run` DISABLE KEYS */;
INSERT INTO `task_run` VALUES ('560c57e694af577d81b84e6f2a6fdea0','d83d5ba5716a8282d439883132a37bc3','74cb00be2c93290d8eeb17eeed7f3809','success',0,'2026-03-01 22:36:35','2026-03-01 22:36:36',230,NULL,0,'2026-03-01 22:36:35','2026-03-01 22:36:36'),('aebaf11add0ce33c8a7cca3d91235036','test-task-id','74cb00be2c93290d8eeb17eeed7f3809','failed',1,NULL,'2026-03-01 22:35:51',101,'不支持的Java文件格式：.py',0,'2026-03-02 06:35:50','2026-03-01 22:35:51');
/*!40000 ALTER TABLE `task_run` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tasks`
--

DROP TABLE IF EXISTS `tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tasks` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务 ID(UUID)',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `description` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务描述',
  `execute_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行类型: script(脚本执行) / callback(HTTP回调)',
  `script_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '脚本类型: java / python / go（script类型必填）',
  `script_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联 script.id（script类型必填）',
  `task_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务调度类型: once / cron',
  `cron_expr` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Cron 表达式，如: 0 0 * * *',
  `timeout_seconds` int DEFAULT '180' COMMENT '超时时间（秒）',
  `retry_count` int DEFAULT '3' COMMENT '重试次数',
  `retry_interval_seconds` int DEFAULT '60' COMMENT '重试间隔时间',
  `target_worker` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '指定 Worker（为空表示自动调度）',
  `required_tags` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务需要的标签（JSON格式，如: ["V1","FastAPI"]），用于自动匹配Worker',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态: active/paused/deleted',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_execute_type` (`execute_type`),
  KEY `idx_task_type` (`task_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务定义表（支持脚本执行和HTTP回调）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tasks`
--

LOCK TABLES `tasks` WRITE;
/*!40000 ALTER TABLE `tasks` DISABLE KEYS */;
INSERT INTO `tasks` VALUES ('d83d5ba5716a8282d439883132a37bc3','测试','测试任务','script','python','005d3c6684c957078e66748b5001afeb','once',NULL,180,3,60,NULL,NULL,'active','system','2026-03-01 22:36:33','2026-03-01 22:36:33');
/*!40000 ALTER TABLE `tasks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_roles`
--

DROP TABLE IF EXISTS `user_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_roles` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `role_id` int NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `user_roles_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `user_roles_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_roles`
--

LOCK TABLES `user_roles` WRITE;
/*!40000 ALTER TABLE `user_roles` DISABLE KEYS */;
INSERT INTO `user_roles` VALUES (1,'0f810981d4a65db463520f23a2730566',1),(2,'0f810981d4a65db463520f23a2730566',2);
/*!40000 ALTER TABLE `user_roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID (UUID)',
  `nickname` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '昵称',
  `username` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('0f810981d4a65db463520f23a2730566','测试用户','username','$2a$10$stHRjNSmiK4qV9mNQtgHiuobO4wmaVeHzzDv/2G7Fy04kmovqRUcC','2026-03-02 14:28:56','2026-03-02 14:28:56');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `worker_tags`
--

DROP TABLE IF EXISTS `worker_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `worker_tags` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `worker_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Worker ID',
  `tag_id` int NOT NULL COMMENT '标签ID',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_worker_tag` (`worker_id`,`tag_id`),
  KEY `tag_id` (`tag_id`),
  CONSTRAINT `worker_tags_ibfk_1` FOREIGN KEY (`worker_id`) REFERENCES `workers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `worker_tags_ibfk_2` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Worker标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `worker_tags`
--

LOCK TABLES `worker_tags` WRITE;
/*!40000 ALTER TABLE `worker_tags` DISABLE KEYS */;
INSERT INTO `worker_tags` VALUES (16,'74cb00be2c93290d8eeb17eeed7f3809',34,'2026-03-01 22:35:50'),(17,'74cb00be2c93290d8eeb17eeed7f3809',35,'2026-03-01 22:35:50');
/*!40000 ALTER TABLE `worker_tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `workers`
--

DROP TABLE IF EXISTS `workers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workers` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务 ID(UUID)',
  `host` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主机名',
  `ip` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内网 IP',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态: online/offline/busy',
  `cpu_usage` decimal(5,2) DEFAULT NULL COMMENT 'CPU 使用率 %',
  `mem_usage` decimal(5,2) DEFAULT NULL COMMENT '内存使用率 %',
  `running_tasks` int DEFAULT '0' COMMENT '当前运行任务数',
  `last_heartbeat` timestamp NULL DEFAULT NULL COMMENT '最近一次心跳时间',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Worker 执行器节点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `workers`
--

LOCK TABLES `workers` WRITE;
/*!40000 ALTER TABLE `workers` DISABLE KEYS */;
INSERT INTO `workers` VALUES ('74cb00be2c93290d8eeb17eeed7f3809','docker-desktop','192.168.65.3','online',0.60,5.50,0,'2026-03-01 22:35:50','2026-03-01 22:35:50','2026-03-01 22:37:50');
/*!40000 ALTER TABLE `workers` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-02 14:37:58
