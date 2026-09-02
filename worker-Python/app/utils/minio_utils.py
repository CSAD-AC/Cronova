import io
import os
from minio import Minio
from minio.error import S3Error
from uuid import uuid4
from app.config import Config


def get_minio_client() -> Minio:
    """获取MinIO客户端实例"""
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


def minio_upload_file(bucket_name: str, file_name: str, file_content: io.BytesIO, file_length: int) -> None:
    """上传文件到MinIO"""
    minio_client = get_minio_client()
    try:
        # 检查并创建存储桶
        if not minio_client.bucket_exists(bucket_name):
            try:
                minio_client.make_bucket(bucket_name)
            except S3Error as e:
                # 忽略桶已存在的错误，继续执行
                if e.code != 'BucketAlreadyOwnedByYou':
                    raise
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
