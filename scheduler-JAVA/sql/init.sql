DROP DATABASE IF EXISTS scheduler;
CREATE DATABASE IF NOT EXISTS `scheduler`;

USE scheduler;

-- 用户表
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
                         `id` VARCHAR(36) PRIMARY KEY COMMENT '主键ID (UUID)',
                         `nickname` VARCHAR(100) NOT NULL COMMENT '昵称',
                         `username` VARCHAR(100) NOT NULL COMMENT '用户名',
                         `password` VARCHAR(80) NOT NULL COMMENT '密码',
                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

INSERT INTO users(`id`, `nickname`, `username`, `password`) VALUE
    (
    '0f810981d4a65db463520f23a2730566',
    '测试用户',
    'username',
    '$2a$10$stHRjNSmiK4qV9mNQtgHiuobO4wmaVeHzzDv/2G7Fy04kmovqRUcC'
);

-- 角色表
DROP TABLE IF EXISTS roles;
CREATE TABLE `roles` (
                         `id` INTEGER AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                         `name` VARCHAR(100) NOT NULL COMMENT '角色名称',
                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

INSERT INTO `roles` (`id`, `name`) VALUES
                                       (1, 'admin'),
                                       (2, 'user');

-- 用户角色表
DROP TABLE IF EXISTS user_roles;
CREATE TABLE `user_roles` (
                              `id` INTEGER AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                              `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
                              `role_id` INTEGER NOT NULL COMMENT '角色ID',
                              FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
                              FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色表';

INSERT INTO user_roles(`user_id`, `role_id`) VALUES
                                                 ('0f810981d4a65db463520f23a2730566', 1),
                                                 ('0f810981d4a65db463520f23a2730566', 2);

-- 脚本表
DROP TABLE IF EXISTS scripts;
CREATE TABLE `scripts` (
                           `id` VARCHAR(36) PRIMARY KEY COMMENT '主键ID (UUID)',
                           `name` VARCHAR(100) NOT NULL COMMENT '脚本名称',
                           `path` VARCHAR(255) NOT NULL COMMENT '脚本路径',
                           `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '脚本表';

-- 标签表
DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
                      id              INTEGER AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                      name            VARCHAR(100) NOT NULL COMMENT '依赖包名称, 如: requests',
                      operator        VARCHAR(10) COMMENT '比较操作符: >, >=, ==, <, <=',
                      version         VARCHAR(50) COMMENT '版本号: 1.5.7',
                      create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      UNIQUE KEY unique_tag (name, operator, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表（用于记录Python依赖包信息）';

-- 执行器节点表
DROP TABLE IF EXISTS workers;
CREATE TABLE workers (
                         id              VARCHAR(36) PRIMARY KEY COMMENT '任务 ID(UUID)',
                         host            VARCHAR(64) NOT NULL COMMENT '主机名',
                         ip              VARCHAR(32) NOT NULL COMMENT '内网 IP',
                         status          VARCHAR(16) NOT NULL COMMENT '状态: online/offline/busy',
                         cpu_usage       DECIMAL(5,2) COMMENT 'CPU 使用率 %',
                         mem_usage       DECIMAL(5,2) COMMENT '内存使用率 %',
                         running_tasks   INT DEFAULT 0 COMMENT '当前运行任务数',
                         last_heartbeat  TIMESTAMP COMMENT '最近一次心跳时间',
                         create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Worker 执行器节点表';

-- AKSK 认证表
DROP TABLE IF EXISTS aksks;
CREATE TABLE aksks (
                       id              VARCHAR(36) PRIMARY KEY COMMENT '主键UUID',
                       access_key      VARCHAR(128) NOT NULL UNIQUE COMMENT 'Access Key',
                       secret_key      VARCHAR(128) NOT NULL COMMENT 'Secret Key',
                       worker_id       VARCHAR(36) NOT NULL COMMENT '关联的 Worker ID',
                       status          VARCHAR(16) NOT NULL COMMENT '状态：active/inactive',
                       create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AKSK 认证表（用于 Worker 执行器的 HTTP 通信鉴权）';

-- Worker标签关联表
DROP TABLE IF EXISTS worker_tags;
CREATE TABLE worker_tags (
                             id              INTEGER AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                             worker_id       VARCHAR(36) NOT NULL COMMENT 'Worker ID',
                             tag_id          INTEGER NOT NULL COMMENT '标签ID',
                             create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE,
                             FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE,
                             UNIQUE KEY unique_worker_tag (worker_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Worker标签关联表';

-- script标签关联表
DROP TABLE IF EXISTS script_tags;
CREATE TABLE script_tags (
                             id              INTEGER AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                             script_id       VARCHAR(36) NOT NULL COMMENT 'script ID',
                             tag_id          INTEGER NOT NULL COMMENT '标签ID',
                             create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
                             FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE,
                             UNIQUE KEY unique_script_tag (script_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='script标签关联表';

-- 任务定义表
DROP TABLE IF EXISTS tasks;
CREATE TABLE tasks (
                       id              VARCHAR(36) PRIMARY KEY COMMENT '任务 ID(UUID)',
                       name            VARCHAR(128) NOT NULL COMMENT '任务名称',
                       description      VARCHAR(256) COMMENT '任务描述',
                       execute_type     VARCHAR(16) NOT NULL COMMENT '执行类型: script(脚本执行) / callback(HTTP回调)',
                       script_type      VARCHAR(16) COMMENT '脚本类型: java / python / go（script类型必填）',
                       script_id        VARCHAR(36) COMMENT '关联 script.id（script类型必填）',
                       task_type       VARCHAR(16) NOT NULL COMMENT '任务调度类型: once / cron',
                       cron_expr       VARCHAR(64) COMMENT 'Cron 表达式，如: 0 0 * * *',
                       enabled         BOOLEAN DEFAULT TRUE COMMENT '是否启用',
                       timeout_seconds INT DEFAULT 180 COMMENT '超时时间（秒）',
                       target_worker   VARCHAR(64) COMMENT '指定 Worker（为空表示自动调度）',
                       status          VARCHAR(16) NOT NULL COMMENT '任务状态: active/paused/deleted',
                       created_by      VARCHAR(64) COMMENT '创建人',
                       create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                       INDEX idx_execute_type (execute_type),
                       INDEX idx_task_type (task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务定义表（支持脚本执行和HTTP回调）';

-- 任务运行实例表
DROP TABLE IF EXISTS task_run;
CREATE TABLE task_run (
                          id              VARCHAR(36) PRIMARY KEY COMMENT '任务运行实例 ID(UUID)',
                          task_id         VARCHAR(36) NOT NULL COMMENT '关联 task.task_id',
                          worker_id       VARCHAR(36) COMMENT '实际执行的 Worker',
                          status          VARCHAR(16) NOT NULL COMMENT '状态: pending/running/success/failed/timeout',
                          exit_code       INT COMMENT '进程退出码',
                          start_time      TIMESTAMP COMMENT '开始时间',
                          end_time        TIMESTAMP COMMENT '结束时间',
                          duration_ms     BIGINT COMMENT '执行耗时（毫秒）',
                          error_message   TEXT COMMENT '失败原因',
                          retry_num       INT DEFAULT 0 COMMENT '当前重试次数',
                          create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行实例表（一次运行一条记录）';

-- 任务日志表
DROP TABLE IF EXISTS task_log;
CREATE TABLE task_log (
                          id              VARCHAR(36) PRIMARY KEY COMMENT '任务日志 ID(UUID)',
                          run_id          VARCHAR(64) NOT NULL COMMENT '关联 task_run.id',
                          stdout_path     VARCHAR(256) COMMENT 'stdout 日志 MinIO 路径',
                          stderr_path     VARCHAR(256) COMMENT 'stderr 日志 MinIO 路径',
                          create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务日志表（仅存 MinIO 路径）';

-- =====================================================
-- 模块二：HTTP回调任务管理
-- =====================================================

-- HTTP回调任务配置表
DROP TABLE IF EXISTS callback_job_configs;
CREATE TABLE callback_job_configs (
    id                      VARCHAR(36) PRIMARY KEY COMMENT '配置ID（UUID）',
    task_id                 VARCHAR(36) NOT NULL COMMENT '关联tasks.id',

    -- 回调配置
    callback_url            VARCHAR(512) NOT NULL COMMENT '回调URL',
    callback_method         VARCHAR(8) DEFAULT 'POST' COMMENT '回调方法：GET/POST/PUT/DELETE',

    -- 认证配置（请求头 X-Secret）
    secret_key              VARCHAR(64) NOT NULL COMMENT '认证秘钥，用于 X-Secret 请求头',

    -- 状态统计
    last_callback_time     DATETIME COMMENT '上次回调时间',
    last_callback_status   VARCHAR(16) COMMENT '上次回调状态：success/failed/timeout',
    total_callback_count   INT DEFAULT 0 COMMENT '总回调次数',
    success_count          INT DEFAULT 0 COMMENT '成功次数',
    failed_count           INT DEFAULT 0 COMMENT '失败次数',

    -- 审计字段
    created_by              VARCHAR(64) COMMENT '创建人',
    create_time             DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    INDEX idx_task_id (task_id),
    INDEX idx_callback_url (callback_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP回调任务配置表';

-- HTTP回调执行记录表
DROP TABLE IF EXISTS callback_job_runs;
CREATE TABLE callback_job_runs (
    id                      VARCHAR(36) PRIMARY KEY COMMENT '执行记录ID（UUID）',
    task_id                 VARCHAR(36) NOT NULL COMMENT '关联tasks.id',
    config_id               VARCHAR(36) NOT NULL COMMENT '关联callback_job_configs.id',
    task_run_id             VARCHAR(36) COMMENT '关联task_run.id',
    request_id              VARCHAR(64) DEFAULT (UUID()) COMMENT '请求ID（X-Request-Id），用于请求追踪，数据库自动生成',

    -- 执行状态
    status                  VARCHAR(16) NOT NULL COMMENT '执行状态：pending/running/success/failed/timeout',

    -- 请求元数据（存储在数据库）
    request_url             VARCHAR(512) COMMENT '实际请求URL',
    request_method          VARCHAR(8) COMMENT '实际请求方法',

    -- 完整请求/响应数据（存储在MinIO）
    request_path            VARCHAR(256) COMMENT '请求数据MinIO路径（包含headers和body）',
    response_path           VARCHAR(256) COMMENT '响应数据MinIO路径（包含statusCode、headers和body）',

    -- 响应元数据（存储在数据库）
    response_status_code    INT COMMENT '响应状态码',
    response_time_ms        INT COMMENT '响应时间（毫秒）',

    -- 执行信息
    retry_index             INT DEFAULT 0 COMMENT '重试次数索引（0表示首次执行）',
    start_time              DATETIME COMMENT '开始时间',
    end_time                DATETIME COMMENT '结束时间',
    duration_ms             INT COMMENT '执行耗时（毫秒）',
    error_message           TEXT COMMENT '错误信息',

    -- 审计字段
    create_time             DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (config_id) REFERENCES callback_job_configs(id) ON DELETE CASCADE,
    FOREIGN KEY (task_run_id) REFERENCES task_run(id) ON DELETE SET NULL,

    INDEX idx_task_id (task_id),
    INDEX idx_config_id (config_id),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time),
    INDEX idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP回调执行记录表';



