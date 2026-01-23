package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.domain.vo.ScriptVO;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.mapper.ScriptTagMapper;
import com.sjdt.scheduler.mapper.TagMapper;
import com.sjdt.scheduler.service.IScriptService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 脚本服务实现类
 */
@Slf4j
@Service
public class ScriptServiceImpl extends ServiceImpl<ScriptMapper, Script> implements IScriptService {

    @Value("${minio.bucket-name-scripts}")
    private String BUCKET_NAME;
    private static final String SCRIPT_FOLDER = "scripts/";

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private ScriptTagMapper scriptTagMapper;

    @Override
    public Script getById(Serializable id) {
        String cacheKey = RedisKey.SCRIPT_CACHE + ":" + id;
        try {
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从Redis获取脚本信息（缓存命中）| ScriptID: {}", id);
                return objectMapper.convertValue(cachedData, Script.class);
            }
        } catch (Exception e) {
            log.warn("从Redis加载脚本缓存失败 | ScriptID: {}", id, e);
        }

        log.info("脚本信息缓存未命中，从数据库加载并同步至缓存 | ScriptID: {}", id);
        Script script = super.getById(id);
        if (script != null) {
            // 填充标签信息
            List<Integer> tagIds = scriptTagMapper.selectTagIdsByScriptId(script.getId());
            if (tagIds != null && !tagIds.isEmpty()) {
                List<Tag> tags = tagMapper.selectBatchIds(tagIds);
                script.setTags(new java.util.HashSet<>(tags));
            }
            
            redisTemplate.opsForValue().set(cacheKey, script, 30, TimeUnit.MINUTES);
        }
        return script;
    }

    @Override
    public void clearCache(String scriptId) {
        log.info("手动清理脚本缓存 | ScriptID: {}", scriptId);
        redisTemplate.delete(RedisKey.SCRIPT_CACHE + ":" + scriptId);
    }

    @Override
    public List<Script> list() {
        // 获取基础脚本列表
        List<Script> scripts = super.list();
        
        // 为每个脚本填充标签信息
        for (Script script : scripts) {
            List<Integer> tagIds = scriptTagMapper.selectTagIdsByScriptId(script.getId());
            if (tagIds != null && !tagIds.isEmpty()) {
                List<Tag> tags = tagMapper.selectBatchIds(tagIds);
                script.setTags(new java.util.HashSet<>(tags));
            }
        }
        
        return scripts;
    }

    @Override
    public boolean updateById(Script entity) {
        log.info("脚本信息更新，清理缓存 | ScriptID: {}", entity.getId());
        redisTemplate.delete(RedisKey.SCRIPT_CACHE + ":" + entity.getId());
        return super.updateById(entity);
    }

    @Override
    public boolean removeById(Serializable id) {
        log.info("脚本删除，清理缓存 | ScriptID: {}", id);
        redisTemplate.delete(RedisKey.SCRIPT_CACHE + ":" + id);
        return super.removeById(id);
    }

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
            
            // 为新上传的脚本构建ScriptVO，包含空标签集合
            return ScriptVO.builder()
                    .id(script.getId())
                    .path(script.getPath())
                    .name(script.getName())
                    .tags(java.util.Set.of()) // 空标签集合
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