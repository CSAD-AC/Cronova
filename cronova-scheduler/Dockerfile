# -------------- 基础镜像（JRE 21） --------------
# 调度器为 Web 应用，仅需 JRE；脚本运行时依赖（python3/setsid）在执行器侧，无需安装
FROM eclipse-temurin:21-jre-jammy

# -------------- 工作目录 --------------
WORKDIR /app

# -------------- 时区（应用代码已设置 UTC，此处同步系统时区避免日志歧义） --------------
ENV TZ=UTC

# -------------- 复制并启动 --------------
COPY target/cronova-scheduler-0.0.1-SNAPSHOT.jar /app/scheduler.jar

EXPOSE 8085

# 通过环境变量注入 MySQL/Redis/RocketMQ/MinIO 连接信息（见 application-dev.yml 占位符）
CMD ["java", "-jar", "/app/scheduler.jar"]
