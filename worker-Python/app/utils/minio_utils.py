import io
import os
import threading
from minio import Minio
from minio.api import ComposeSource, CopySource
from minio.error import S3Error
from uuid import uuid4
from app.config import Config

# MinIO 客户端与已确认存储桶的模块级单例（避免高频上传时反复建连/探测桶）
_client = None
_client_lock = threading.Lock()
_known_buckets = set()
_bucket_lock = threading.Lock()


def _build_client() -> Minio:
    try:
        return Minio(
            # 全部使用 Config 配置（环境变量作为兜底）
            Config.MINIO_ENDPOINT,
            access_key=Config.MINIO_ACCESS_KEY,
            secret_key=Config.MINIO_SECRET_KEY,
            secure=Config.MINIO_SECURE
        )
    except Exception as e:
        raise Exception(f"MinIO客户端初始化失败：{str(e)}")


def get_minio_client() -> Minio:
    """获取MinIO客户端实例（进程级单例，线程安全）"""
    global _client
    if _client is None:
        with _client_lock:
            if _client is None:
                _client = _build_client()
    return _client


def _ensure_bucket(minio_client: Minio, bucket_name: str) -> None:
    """确认存储桶存在（每个桶只在进程生命周期内探测/创建一次）"""
    if bucket_name in _known_buckets:
        return
    with _bucket_lock:
        if bucket_name in _known_buckets:
            return
        try:
            if not minio_client.bucket_exists(bucket_name):
                minio_client.make_bucket(bucket_name)
        except S3Error as e:
            # 忽略桶已存在的错误，继续执行
            if e.code != 'BucketAlreadyOwnedByYou':
                raise
        _known_buckets.add(bucket_name)


def minio_upload_file(bucket_name: str, file_name: str, file_content: io.BytesIO, file_length: int) -> None:
    """上传文件到MinIO"""
    minio_client = get_minio_client()
    try:
        # 确认并创建存储桶（进程内一次）
        _ensure_bucket(minio_client, bucket_name)
        # 上传文件
        minio_client.put_object(
            bucket_name=bucket_name,
            object_name=file_name,
            data=file_content,
            length=file_length
        )
    except S3Error as e:
        raise Exception(f"MinIO文件上传失败：{str(e)}")
    except Exception as e:
        raise Exception(f"文件上传异常：{str(e)}")


def append_log(bucket_name: str, object_name: str, data: bytes, existing_size: int) -> int:
    """增量追加日志字节到对象，返回追加后的最终对象大小。

    机制（Python / Java 两端一致，传输量 O(N)）：
    1. 新增字节上传到 {object}.tail（覆盖写，只含本次增量）；
    2. 合并到最终对象 {object}：
       - 最终对象不存在 → tail copy 为最终对象；
       - 最终对象 < 5MB → 读回拼接覆盖（早期小对象，成本可忽略）；
       - 最终对象 ≥ 5MB → Compose([object, tail] -> 临时对象) 再 copy 回，
         规避“compose 目标==源”的边界问题，全程服务器端操作、不重传数据。
    调用方维护已上传偏移（返回值即新偏移）。失败向上抛出，调用方负责保留本地文件。
    """
    minio_client = get_minio_client()
    _ensure_bucket(minio_client, bucket_name)
    tail = object_name + ".tail"
    # 1. 上传本次增量到 tail（覆盖写）
    minio_client.put_object(bucket_name, tail, io.BytesIO(data), len(data))
    # 2. 合并到最终对象
    if existing_size <= 0:
        # 首次：tail 即最终对象
        minio_client.copy_object(bucket_name, object_name, CopySource(bucket_name, tail))
    elif existing_size < MIN_COMPOSE_SOURCE_SIZE:
        # 早期（<5MB）：读回拼接覆盖（数据量小，成本可忽略）
        resp = minio_client.get_object(bucket_name, object_name)
        try:
            existing = resp.read()
        finally:
            resp.close()
            resp.release_conn()
        combined = existing + data
        minio_client.put_object(bucket_name, object_name, io.BytesIO(combined), len(combined))
    else:
        # 已 ≥5MB：compose 到临时对象再 copy 回（S3 要求除最后一段外各源 ≥5MB，tail 是末段，合法）
        tmp = object_name + ".cmp"
        minio_client.compose_object(
            bucket_name, tmp,
            [ComposeSource(bucket_name, object_name), ComposeSource(bucket_name, tail)],
        )
        minio_client.copy_object(bucket_name, object_name, CopySource(bucket_name, tmp))
        minio_client.remove_object(bucket_name, tmp)
    return existing_size + len(data)


# S3 ComposeObject 要求除最后一段外各源 ≥ 5MB
MIN_COMPOSE_SOURCE_SIZE = 5 * 1024 * 1024


def get_minio_file_object(bucket_name: str, file_name: str) -> io.BytesIO:
    """从MinIO获取文件对象"""
    minio_client = get_minio_client()
    try:
        # 统一路径解析逻辑
        actual_bucket = bucket_name
        actual_object = file_name

        # 如果 file_name 包含斜杠，且第一部分是 Config.MINIO_BUCKET_NAME，则进行剥离
        if "/" in file_name:
            parts = file_name.split("/", 1)
            # 如果第一部分匹配配置的桶名，则更新
            if parts[0] == Config.MINIO_BUCKET_NAME:
                actual_bucket = parts[0]
                actual_object = parts[1]
            elif parts[0] == bucket_name:
                # 兼容传入 bucket_name 且 file_name 也带 bucket 的情况
                actual_bucket = parts[0]
                actual_object = parts[1]

        response = minio_client.get_object(actual_bucket, actual_object)
        # 转换为BytesIO对象便于后续处理
        file_object = io.BytesIO(response.read())
        response.close()
        response.release_conn()
        return file_object
    except S3Error as e:
        raise Exception(f"MinIO文件获取失败：{str(e)}")
    except Exception as e:
        raise Exception(f"文件获取异常：{str(e)}")


def get_uuid() -> str:
    """生成UUID字符串"""
    return str(uuid4())


def download_directory(bucket_name: str, prefix: str, local_dir: str) -> int:
    """从MinIO下载整个文件夹到本地"""
    minio_client = get_minio_client()
    file_count = 0
    
    try:
        # 确保本地目录存在
        os.makedirs(local_dir, exist_ok=True)
        
        # 列出所有对象
        objects = minio_client.list_objects(bucket_name, prefix, recursive=True)
        
        for obj in objects:
            if obj.object_name.endswith('/'):
                continue
            
            # 获取文件对象
            response = minio_client.get_object(bucket_name, obj.object_name)
            
            # 计算本地文件路径（保留相对目录结构）
            relative_path = obj.object_name[len(prefix):]
            local_file_path = os.path.join(local_dir, relative_path)
            
            # 创建子目录
            os.makedirs(os.path.dirname(local_file_path), exist_ok=True)
            
            # 写入文件
            with open(local_file_path, 'wb') as f:
                for data in response.stream(8192):
                    f.write(data)
            
            response.close()
            response.release_conn()
            file_count += 1
        
        return file_count
    except S3Error as e:
        raise Exception(f"MinIO文件夹下载失败：{str(e)}")
    except Exception as e:
        raise Exception(f"文件夹下载异常：{str(e)}")
