# -------------- 基础镜像（JRE 21 + 任务运行时） --------------
FROM eclipse-temurin:21-jre-jammy

# -------------- 工作目录 --------------
WORKDIR /app

# -------------- 安装任务运行时依赖 --------------
# python3: 执行 Python 任务；util-linux: setsid（独立进程组，供暂停/恢复）
# java: 运行 .jar/.class 任务（JRE 已内置）；go 任务为静态/动态二进制，直接执行
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        curl \
        ca-certificates \
        util-linux && \
    rm -rf /var/lib/apt/lists/*

# -------------- 创建运行时目录 --------------
RUN mkdir -p scripts_tmp task_instances

# -------------- 复制并启动 --------------
COPY target/sjdt-worker.jar /app/sjdt-worker.jar

CMD ["java", "-jar", "/app/sjdt-worker.jar"]
