# -------------- 基础镜像 --------------
FROM python:3.11.6-slim

# -------------- 工作目录 --------------
WORKDIR /app

# -------------- 环境变量 --------------
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:${PATH}

# -------------- 换阿里源（Debian Bookworm） --------------
RUN if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources && \
        sed -i 's|security.debian.org|mirrors.aliyun.com/debian-security|g' /etc/apt/sources.list.d/debian.sources; \
    else \
        sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list && \
        sed -i 's|security.debian.org|mirrors.aliyun.com/debian-security|g' /etc/apt/sources.list; \
    fi

# -------------- 安装系统构建依赖 + vi（用于调试） --------------
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        git \
        gcc \
        g++ \
        python3-dev \
        libpq-dev \
        curl \
        ca-certificates \
        vim-tiny && \
    rm -rf /var/lib/apt/lists/*

# -------------- 复制并安装 Python 依赖 --------------
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# -------------- 安装运行时系统依赖（JDK） --------------
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-17-jre-headless && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# -------------- 清理构建依赖（但保留 vi） --------------
# 注意：vim-tiny 是运行时保留项，不在此 purge 列表中
RUN apt-get purge -y --auto-remove \
        gcc \
        g++ \
        python3-dev \
        libpq-dev \
        git && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /root/.cache/pip

# -------------- 创建运行时目录（确保在COPY之前） --------------
RUN mkdir -p scripts_tmp

# -------------- 复制并安装本地工具类 --------------
COPY app/scripts_tmp/ ./scripts_tmp/
RUN pip install --no-cache-dir ./scripts_tmp -i https://pypi.tuna.tsinghua.edu.cn/simple

# -------------- 复制项目代码 --------------
COPY . .
# -------------- 默认启动命令 --------------
CMD ["python3", "main.py"]