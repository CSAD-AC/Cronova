import io
import os
from minio import Minio
from minio.error import S3Error
from uuid import uuid4
from app.config import Config


def get_minio_client() -> Minio:
    """
    获取MinIO客户端实例
    :return: Minio客户端对象
    :raises Exception: 客户端创建失败时抛出
    """
    try:
        return Minio(
            # 改用Config中的配置，环境变量作为兜底（可选）
            Config.MINIO_ENDPOINT,  # 核心：用Config的地址
            access_key=Config.MINIO_ACCESS_KEY,
            secret_key=Config.MINIO_SECRET_KEY,
            secure=Config.MINIO_SECURE  # 也从Config读取，更灵活
        )
    except Exception as e:
        raise Exception(f"MinIO客户端初始化失败：{str(e)}")


def minio_upload_file(bucket_name: str, file_name: str, file_content: io.BytesIO, file_length: int) -> None:
    """
    上传文件到MinIO
    :param bucket_name: 存储桶名称
    :param file_name: 文件名
    :param file_content: 文件内容（字节流）
    :param file_length: 文件大小（字节）
    :raises Exception: 上传失败时抛出
    """
    minio_client = get_minio_client()
    try:
        # 检查并创建存储桶
        if not minio_client.bucket_exists(bucket_name):
            minio_client.make_bucket(bucket_name)
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
    """
    从MinIO获取文件对象
    :param bucket_name: 存储桶名称
    :param file_name: 文件名
    :return: 内存中的文件对象（BytesIO）
    :raises Exception: 获取失败时抛出
    """
    minio_client = get_minio_client()
    try:
        response = minio_client.get_object(bucket_name, file_name)
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
