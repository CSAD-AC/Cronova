# 使用 Python 3.11.6 基础镜像
FROM python:3.11.6-slim

# 设置工作目录
WORKDIR /app

# 设置环境变量
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH

# === 关键：切换 Debian apt 源（使用阿里云加速） ===
# 适配 Debian Bookworm (Python 3.11 默认基础镜像)
RUN if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources; \
    else \
        sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list && \
        sed -i 's/security.debian.org/mirrors.aliyun.com\/debian-security/g' /etc/apt/sources.list; \
    fi

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
    gcc \
    python3-dev \
    curl \
    ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件（相对于构建上下文 ..）
COPY requirements.txt .

# 安装 Python 依赖
RUN pip install --no-cache-dir -r requirements.txt \
    -i https://pypi.tuna.tsinghua.edu.cn/simple

# 复制项目代码（相对于构建上下文 ..）
COPY . .

# 创建必要的目录
RUN mkdir -p logs task_instances

# 启动程序
CMD ["python3", "main.py"]
