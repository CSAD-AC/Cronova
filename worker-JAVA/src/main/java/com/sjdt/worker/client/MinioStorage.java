package com.sjdt.worker.client;

import com.sjdt.worker.config.WorkerProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MinIO 对象存储客户端
 * <p>
 * 对应 Python 执行器的 minio_utils：上传 / 下载单文件 / 递归下载目录。
 */
@Slf4j
@Component
public class MinioStorage {

    private final WorkerProperties properties;
    private final MinioClient client;

    public MinioStorage(WorkerProperties properties) {
        this.properties = properties;
        this.client = MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                .build();
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            try {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 存储桶已创建，bucket={}", bucket);
            } catch (Exception e) {
                log.warn("MinIO 创建存储桶失败（可能已存在），bucket={}, err={}", bucket, e.getMessage());
            }
        }
    }

    /** 上传字节内容 */
    public void upload(String bucket, String objectName, byte[] content) {
        try {
            ensureBucket(bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO 上传失败, bucket=" + bucket + ", obj=" + objectName, e);
        }
    }

    /** 读取单文件为字节数组 */
    public byte[] download(String bucket, String objectName) {
        try {
            try (InputStream in = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build())) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO 下载失败, bucket=" + bucket + ", obj=" + objectName, e);
        }
    }

    /**
     * 递归下载目录到本地
     *
     * @param bucket   存储桶
     * @param prefix   MinIO 前缀（如 scripts/xxx/）
     * @param localDir 本地目标目录
     * @return 下载文件数
     */
    public int downloadDirectory(String bucket, String prefix, Path localDir) {
        int count = 0;
        try {
            ensureBucket(bucket);
            Files.createDirectories(localDir);
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                if (objectName.endsWith("/")) {
                    continue;
                }
                String relative = objectName.substring(prefix.length());
                Path localFile = localDir.resolve(relative).normalize();
                Files.createDirectories(localFile.getParent());
                byte[] content = download(bucket, objectName);
                Files.write(localFile, content);
                count++;
            }
            log.info("MinIO 目录下载完成，prefix={}, localDir={}, count={}", prefix, localDir, count);
            return count;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 目录下载失败, bucket=" + bucket + ", prefix=" + prefix, e);
        }
    }

    /** 列出目录下的对象名（用于调试） */
    public List<String> listObjects(String bucket, String prefix) {
        List<String> names = new ArrayList<>();
        try {
            for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build())) {
                names.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.warn("MinIO 列出对象失败, bucket={}, prefix={}, err={}", bucket, prefix, e.getMessage());
        }
        return names;
    }
}
