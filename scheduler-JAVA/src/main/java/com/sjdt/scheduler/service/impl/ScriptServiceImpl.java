package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.common.BizException;
import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.vo.ScriptVO;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.mapper.ScriptTagMapper;
import com.sjdt.scheduler.mapper.TagMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.service.IScriptService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

    @Autowired
    private TaskMapper taskMapper;

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
            String fullPath = BUCKET_NAME + "/" + objectName;
            Script script = Script.builder()
                    .name(originalFilename)
                    .path(fullPath)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            save(script);
            
            // 为新上传的脚本构建ScriptVO，包含空标签集合
            return ScriptVO.builder()
                    .id(script.getId())
                    .path(script.getPath())
                    .name(script.getName())
                    .isDirectory(false)
                    .parentId(script.getParentId())
                    .tags(java.util.Set.of()) // 空标签集合
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("上传脚本文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ScriptVO uploadDirectory(MultipartFile file) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
            }

            String folderId = UUID.randomUUID().toString();
            String folderPath = SCRIPT_FOLDER + folderId + "/";
            String fullPath = BUCKET_NAME + "/" + folderPath;

            String folderName = file.getOriginalFilename();
            if (folderName != null && folderName.toLowerCase().endsWith(".zip")) {
                folderName = folderName.substring(0, folderName.length() - 4);
            }
            Script rootDir = Script.builder()
                    .name(folderName)
                    .path(fullPath)
                    .isDirectory(true)
                    .parentId(null)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            save(rootDir);
            log.info("创建根目录Script记录: ID={}, name={}", rootDir.getId(), folderName);

            Map<String, Script> dirScriptMap = new HashMap<>();
            dirScriptMap.put("", rootDir);

            List<ZipEntry> allEntries = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    allEntries.add(entry);
                }
            }

            for (ZipEntry entry : allEntries) {
                if (entry.isDirectory()) {
                    String entryName = entry.getName();
                    if (entryName.endsWith("/")) {
                        entryName = entryName.substring(0, entryName.length() - 1);
                    }
                    if (entryName.contains("/")) {
                        String relativeDir = entryName.substring(entryName.indexOf('/') + 1);
                        getOrCreateParentDir(relativeDir, rootDir, folderPath, fullPath, dirScriptMap);
                    }
                } else {
                    String entryName = entry.getName();
                    String relativePath = entryName;
                    if (entryName.contains("/")) {
                        relativePath = entryName.substring(entryName.indexOf('/') + 1);
                    }
                    if (relativePath.isEmpty()) continue;

                    String parentDirPath = "";
                    if (relativePath.contains("/")) {
                        parentDirPath = relativePath.substring(0, relativePath.lastIndexOf('/'));
                    }

                    Script parentDir = getOrCreateParentDir(parentDirPath, rootDir, folderPath, fullPath, dirScriptMap);

                    String objectName = folderPath + relativePath;
                    byte[] content;
                    try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                        ZipEntry fileEntry;
                        while ((fileEntry = zis.getNextEntry()) != null) {
                            if (fileEntry.getName().equals(entryName)) {
                                content = zis.readAllBytes();
                                minioClient.putObject(
                                        PutObjectArgs.builder()
                                                .bucket(BUCKET_NAME)
                                                .object(objectName)
                                                .stream(new ByteArrayInputStream(content), content.length, -1)
                                                .contentType(getContentType(relativePath))
                                                .build()
                                );
                                break;
                            }
                        }
                    }

                    String fileName = relativePath.contains("/") 
                            ? relativePath.substring(relativePath.lastIndexOf('/') + 1) 
                            : relativePath;

                    Script fileScript = Script.builder()
                            .name(fileName)
                            .path(fullPath + relativePath)
                            .isDirectory(false)
                            .parentId(parentDir.getId())
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();
                    save(fileScript);

                    log.debug("创建文件Script记录: ID={}, name={}, parentId={}",
                            fileScript.getId(), fileName, parentDir.getId());
                }
            }

            log.info("文件夹上传完成，根目录ID: {}", rootDir.getId());

            return ScriptVO.builder()
                    .id(rootDir.getId())
                    .path(rootDir.getPath())
                    .name(rootDir.getName())
                    .isDirectory(true)
                    .parentId(rootDir.getParentId())
                    .tags(java.util.Set.of())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("上传文件夹失败: " + e.getMessage(), e);
        }
    }

    private Script getOrCreateParentDir(String dirPath, Script rootDir, 
                                       String folderPath, String fullPath,
                                       Map<String, Script> dirScriptMap) {
        if (dirPath == null || dirPath.isEmpty()) {
            return rootDir;
        }

        if (dirScriptMap.containsKey(dirPath)) {
            return dirScriptMap.get(dirPath);
        }

        String parentPath = "";
        String dirName = dirPath;
        int lastSlash = dirPath.lastIndexOf('/');
        if (lastSlash > 0) {
            parentPath = dirPath.substring(0, lastSlash);
            dirName = dirPath.substring(lastSlash + 1);
        }

        Script parentScript = getOrCreateParentDir(parentPath, rootDir, folderPath, fullPath, dirScriptMap);

        Script dirScript = Script.builder()
                .name(dirName)
                .path(fullPath + dirPath + "/")
                .isDirectory(true)
                .parentId(parentScript.getId())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        save(dirScript);
        log.debug("创建目录Script记录: ID={}, name={}, parentId={}", 
                dirScript.getId(), dirName, parentScript.getId());

        dirScriptMap.put(dirPath, dirScript);
        return dirScript;
    }

    /**
     * 根据文件扩展名获取Content-Type
     */
    private String getContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".java")) return "text/x-java-source";
        if (lower.endsWith(".go")) return "text/x-go-source";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        return "application/octet-stream";
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
            String path = script.getPath();
            String objectName = path;
            if (path.startsWith(BUCKET_NAME + "/")) {
                objectName = path.substring(BUCKET_NAME.length() + 1);
            }

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

    @Override
    public void downloadDirectory(String id, HttpServletResponse response) throws IOException {
        Script rootDir = getById(id);
        if (rootDir == null) {
            throw new IOException("脚本不存在");
        }

        if (!Boolean.TRUE.equals(rootDir.getIsDirectory())) {
            throw new IOException("该脚本不是文件夹类型，请使用下载脚本接口");
        }

        String folderName = rootDir.getName() + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + folderName + "\"");

        List<Script> allScripts = getAllChildScripts(id);
        log.info("开始下载文件夹 {}，共 {} 个文件", rootDir.getName(), allScripts.size());

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Script script : allScripts) {
                if (Boolean.TRUE.equals(script.getIsDirectory())) {
                    continue;
                }

                String entryName = getEntryName(rootDir, script);
                ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);

                String objectName = script.getPath();
                if (objectName.startsWith(BUCKET_NAME + "/")) {
                    objectName = objectName.substring(BUCKET_NAME.length() + 1);
                }

                try (java.io.InputStream is = minioClient.getObject(
                        io.minio.GetObjectArgs.builder()
                                .bucket(BUCKET_NAME)
                                .object(objectName)
                                .build()
                )) {
                    byte[] buffer = new byte[1024 * 1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                } catch (ServerException e) {
                    throw new RuntimeException(e);
                } catch (InsufficientDataException e) {
                    throw new RuntimeException(e);
                } catch (ErrorResponseException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                } catch (InvalidKeyException e) {
                    throw new RuntimeException(e);
                } catch (InvalidResponseException e) {
                    throw new RuntimeException(e);
                } catch (XmlParserException e) {
                    throw new RuntimeException(e);
                } catch (InternalException e) {
                    throw new RuntimeException(e);
                }

                zos.closeEntry();
            }
        }

        log.info("文件夹下载完成: {}", rootDir.getName());
    }

    private List<Script> getAllChildScripts(String parentId) {
        List<Script> result = new ArrayList<>();
        LambdaQueryWrapper<Script> queryWrapper = new LambdaQueryWrapper<Script>()
                .eq(Script::getParentId, parentId);
        List<Script> children = list(queryWrapper);

        for (Script child : children) {
            result.add(child);
            if (Boolean.TRUE.equals(child.getIsDirectory())) {
                result.addAll(getAllChildScripts(child.getId()));
            }
        }

        return result;
    }

    private String getEntryName(Script rootDir, Script script) {
        String rootPath = rootDir.getPath();
        if (rootPath.startsWith(BUCKET_NAME + "/")) {
            rootPath = rootPath.substring(BUCKET_NAME.length() + 1);
        }
        if (rootPath.endsWith("/")) {
            rootPath = rootPath.substring(0, rootPath.length() - 1);
        }

        String scriptPath = script.getPath();
        if (scriptPath.startsWith(BUCKET_NAME + "/")) {
            scriptPath = scriptPath.substring(BUCKET_NAME.length() + 1);
        }

        String entryName = scriptPath;
        if (rootPath.isEmpty()) {
            entryName = script.getName();
        } else if (scriptPath.startsWith(rootPath)) {
            entryName = scriptPath.substring(rootPath.length());
            if (entryName.startsWith("/")) {
                entryName = entryName.substring(1);
            }
        }

        return entryName;
    }

    @Override
    public boolean deleteScript(String id) {
        log.info("开始删除脚本 | ScriptID: {}", id);
        Script script = getById(id);
        if (script == null) {
            log.warn("脚本不存在，删除失败 | ScriptID: {}", id);
            return false;
        }

        // 1. 检查是否有非删除状态的任务正在使用该脚本或该脚本的父目录
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getScriptId, id)
                .ne(Task::getStatus, TaskStatus.deleted);
        Long taskCount = taskMapper.selectCount(queryWrapper);
        if (taskCount > 0) {
            log.warn("脚本正在被任务使用，无法删除 | ScriptID: {}, TaskCount: {}", id, taskCount);
            throw new BizException(400, "脚本正在被任务使用，无法删除");
        }

        // 2. 如果是目录类型，先删除所有子文件记录
        if (Boolean.TRUE.equals(script.getIsDirectory())) {
            LambdaQueryWrapper<Script> childQuery = new LambdaQueryWrapper<Script>()
                    .eq(Script::getParentId, id);
            List<Script> childScripts = list(childQuery);
            if (!childScripts.isEmpty()) {
                List<String> childIds = childScripts.stream().map(Script::getId).toList();
                // 检查子文件是否有被任务使用
                for (String childId : childIds) {
                    LambdaQueryWrapper<Task> childTaskQuery = new LambdaQueryWrapper<Task>()
                            .eq(Task::getScriptId, childId)
                            .ne(Task::getStatus, TaskStatus.deleted);
                    Long childTaskCount = taskMapper.selectCount(childTaskQuery);
                    if (childTaskCount > 0) {
                        log.warn("脚本的子文件正在被任务使用，无法删除 | ScriptID: {}, ChildID: {}", id, childId);
                        throw new BizException(400, "脚本的子文件正在被任务使用，无法删除");
                    }
                }
                // 删除所有子文件记录
                removeByIds(childIds);
                log.info("删除子文件记录 {} 个", childIds.size());
            }
        } else if (script.getParentId() != null) {
            // 如果是文件类型且有父目录，检查父目录是否还有其他子文件被使用
            // （这里简化处理，允许删除子文件）
        }

        // 3. 从数据库删除当前记录
        removeById(id);

        // 3. 从MinIO删除文件或文件夹
        try {
            String path = script.getPath();
            String objectName = path;
            if (path.startsWith(BUCKET_NAME + "/")) {
                objectName = path.substring(BUCKET_NAME.length() + 1);
            }

            if (Boolean.TRUE.equals(script.getIsDirectory())) {
                // 目录类型：列出并删除所有对象
                Iterable<Result<Item>> objects = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(BUCKET_NAME)
                                .prefix(objectName)
                                .recursive(true)
                                .build()
                );
                int deletedCount = 0;
                for (Result<Item> result : objects) {
                    try {
                        String objName = result.get().objectName();
                        minioClient.removeObject(
                                RemoveObjectArgs.builder()
                                        .bucket(BUCKET_NAME)
                                        .object(objName)
                                        .build()
                        );
                        deletedCount++;
                    } catch (Exception e) {
                        log.warn("删除MinIO对象失败: {}", e.getMessage());
                    }
                }
                log.info("从MinIO删除文件夹成功，共 {} 个对象 | Path: {}", deletedCount, objectName);
            } else {
                // 文件类型：删除单个文件
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(BUCKET_NAME)
                                .object(objectName)
                                .build()
                );
                log.info("从MinIO删除脚本文件成功 | Object: {}", objectName);
            }
        } catch (Exception e) {
            log.error("从MinIO删除脚本失败 | ScriptID: {}, Path: {}", id, script.getPath(), e);
            // 数据库已经删除了，MinIO删除失败仅记录日志
        }

        return true;
    }

    @Override
    public boolean updateScriptContent(String id, String content) {
        log.info("开始更新脚本内容 | ScriptID: {}", id);
        Script script = getById(id);
        if (script == null) {
            log.warn("脚本不存在，更新失败 | ScriptID: {}", id);
            return false;
        }

        try {
            String path = script.getPath();
            String objectName = path;
            if (path.startsWith(BUCKET_NAME + "/")) {
                objectName = path.substring(BUCKET_NAME.length() + 1);
            }

            // 更新MinIO中的文件内容
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(contentBytes), contentBytes.length, -1)
                            .contentType("text/plain")
                            .build()
            );

            // 更新数据库更新时间
            script.setUpdateTime(LocalDateTime.now());
            updateById(script);
            
            log.info("脚本内容更新成功 | ScriptID: {}, Object: {}", id, objectName);
            return true;
        } catch (Exception e) {
            log.error("更新脚本内容失败 | ScriptID: {}", id, e);
            throw new RuntimeException("更新脚本内容失败: " + e.getMessage());
        }
    }
}