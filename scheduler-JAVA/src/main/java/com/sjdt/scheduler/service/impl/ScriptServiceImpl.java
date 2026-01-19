package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.vo.ScriptVO;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.service.IScriptService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 脚本服务实现类
 */
@Service
public class ScriptServiceImpl extends ServiceImpl<ScriptMapper, Script> implements IScriptService {

    @Value("${minio.bucket-name-scripts}")
    private String BUCKET_NAME;
    private static final String SCRIPT_FOLDER = "scripts/";

    @Autowired
    private MinioClient minioClient;

    @Override
    public ScriptVO uploadScript(MultipartFile file) {
        try {
            // 检查MinIO中是否存在bucket，不存在则创建
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            // 获取文件扩展名
            String extension = "";
            int lastDotIndex = 0;
            if (originalFilename != null) {
                lastDotIndex = originalFilename.lastIndexOf('.');
            }
            if (lastDotIndex != -1) {
                if (originalFilename != null) {
                    extension = originalFilename.substring(lastDotIndex + 1);
                }
            }
            String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
            String objectName = SCRIPT_FOLDER + uniqueFilename;

            // 上传文件到MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 创建Script对象并保存到数据库
            Script script = Script.builder()
                    .name(originalFilename)
                    .path(objectName)
                    .build();

            save(script);
            return ScriptVO.builder()
                    .id(script.getId())
                    .path(script.getPath())
                    .name(script.getName())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("上传脚本文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void downloadScript(String id, HttpServletResponse response) throws IOException {
        try {
            // 根据ID从数据库中获取脚本信息
            Script script = getById(id);
            if (script == null) {
                throw new RuntimeException("脚本不存在");
            }

            // 从MinIO中获取文件
            String objectName = script.getPath();
            try (java.io.InputStream inputStream = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .build()
            )) {
                // 设置响应头
                response.setContentType("application/octet-stream");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + script.getName() + "\"");

                // 将文件写入响应流
                try (ServletOutputStream outputStream = response.getOutputStream()) {
                    byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                }
            }
        } catch (Exception e) {
            throw new IOException("下载脚本文件失败: " + e.getMessage(), e);
        }
    }
}