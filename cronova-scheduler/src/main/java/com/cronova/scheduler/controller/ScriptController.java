package com.cronova.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.Script;
import com.cronova.scheduler.domain.vo.ScriptVO;
import com.cronova.scheduler.service.IScriptService;
import com.cronova.scheduler.service.IScriptTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 脚本控制器
 * 用于处理脚本的上传、下载等操作
 */
@RestController
@RequestMapping("/api/script")
@RequiredArgsConstructor
@Tag(name = "脚本管理", description = "脚本的上传、下载等操作")
public class ScriptController {

    private final IScriptService scriptService;
    private final IScriptTagService scriptTagService;

    /**
     * 根据脚本ID查询脚本详情
     * @param id 脚本ID
     * @return 脚本详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询脚本详情", description = "根据脚本ID查询脚本的详细信息")
    public Result<Script> getScriptById(@PathVariable String id) {
        Script script = scriptService.getById(id);
        if (script == null) {
            return Result.error(404, "Script not found");
        }
        return Result.success(script);
    }

    /**
     * 获取所有脚本列表
     * @return 脚本列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取脚本列表", description = "获取所有脚本的列表")
    public Result<List<ScriptVO>> listScripts() {
        List<Script> scripts = scriptService.list();
        List<ScriptVO> scriptVOs = scripts.stream()
                .map(script -> ScriptVO.builder()
                        .id(script.getId())
                        .name(script.getName())
                        .path(script.getPath())
                        .isDirectory(script.getIsDirectory())
                        .mainFile(script.getMainFile())
                        .parentId(script.getParentId())
                        .rootPath(getRootPath(script))
                        .tags(script.getTags())
                        .build())
                .toList();
        return Result.success(scriptVOs);
    }
    
    /**
     * 计算脚本的根目录路径
     * @param script 脚本对象
     * @return 根目录路径
     */
    private String getRootPath(Script script) {
        // 如果是目录类型，根目录就是自己的路径
        if (Boolean.TRUE.equals(script.getIsDirectory())) {
            return script.getPath();
        }
        
        // 如果有父目录ID，需要查询父目录的路径
        if (script.getParentId() != null) {
            Script parentScript = scriptService.getById(script.getParentId());
            if (parentScript != null) {
                return parentScript.getPath();
            }
        }
        
        // 否则返回脚本所在的目录路径（去掉文件名部分）
        String scriptPath = script.getPath();
        int lastSlash = scriptPath.lastIndexOf('/');
        if (lastSlash > 0) {
            return scriptPath.substring(0, lastSlash);
        }
        
        return "/";
    }

    /**
     * 上传脚本文件
     * @param file 脚本文件
     * @return 上传结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckRole("admin")
    @Operation(summary = "上传脚本文件", description = "上传脚本文件到MinIO")
    public Result<ScriptVO> uploadScript(@RequestPart("file") MultipartFile file) {
        return Result.success(scriptService.uploadScript(file));
    }

    /**
     * 上传文件夹（ZIP压缩包）
     * @param file ZIP压缩包文件
     * @return 上传结果
     */
    @PostMapping(value = "/upload-directory", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckRole("admin")
    @Operation(summary = "上传文件夹", description = "上传ZIP压缩包到MinIO，解压后保持目录结构")
    public Result<ScriptVO> uploadDirectory(@RequestPart("file") MultipartFile file) {
        return Result.success(scriptService.uploadDirectory(file));
    }

    /**
     * 下载脚本文件
     * @param id 脚本ID
     * @param response 响应对象
     * @throws IOException IO异常
     */
    @GetMapping("/download/{id}")
    @Operation(summary = "下载脚本文件", description = "根据脚本ID下载脚本文件")
    public void downloadScript(@PathVariable String id, HttpServletResponse response) throws IOException {
        scriptService.downloadScript(id, response);
    }

    /**
     * 下载文件夹（ZIP压缩包）
     * @param id 脚本ID（文件夹类型）
     * @param response 响应对象
     * @throws IOException IO异常
     */
    @GetMapping("/download-directory/{id}")
    @Operation(summary = "下载文件夹", description = "根据脚本ID下载文件夹（ZIP压缩包）")
    public void downloadDirectory(@PathVariable String id, HttpServletResponse response) throws IOException {
        scriptService.downloadDirectory(id, response);
    }

    /**
     * 为脚本添加标签
     * @param scriptId 脚本ID
     * @param tagId 标签ID
     * @return 操作结果
     */
    @PostMapping("/add")
    @SaCheckRole("admin")
    @Operation(summary = "为脚本添加标签", description = "为指定脚本添加单个标签")
    public Result<Void> addTagToScript(@RequestParam String scriptId, @RequestParam Integer tagId) {
        return scriptTagService.addTagToScript(scriptId, tagId);
    }

    /**
     * 为脚本批量添加标签
     * @param scriptId 脚本ID
     * @param tagIds 标签ID列表
     * @return 操作结果
     */
    @PostMapping("/adds")
    @SaCheckRole("admin")
    @Operation(summary = "为脚本批量添加标签", description = "为指定脚本批量添加标签")
    public Result<Void> batchAddTagsToScript(@RequestParam String scriptId, @RequestBody List<Integer> tagIds) {
        return scriptTagService.batchAddTagsToScript(scriptId, tagIds);
    }

    /**
     * 从脚本移除标签
     * @param scriptId 脚本ID
     * @param tagId 标签ID
     * @return 操作结果
     */
    @DeleteMapping("/remove")
    @SaCheckRole("admin")
    @Operation(summary = "从脚本移除标签", description = "从指定脚本移除单个标签")
    public Result<Void> removeTagFromScript(@RequestParam String scriptId, @RequestParam Integer tagId) {
        return scriptTagService.removeTagFromScript(scriptId, tagId);
    }

    /**
     * 清空脚本的所有标签
     * @param scriptId 脚本ID
     * @return 操作结果
     */
    @DeleteMapping("/clear")
    @SaCheckRole("admin")
    @Operation(summary = "清空脚本标签", description = "清空指定脚本的所有标签")
    public Result<Void> clearTagsFromScript(@RequestParam String scriptId) {
        return scriptTagService.clearTagsFromScript(scriptId);
    }

    /**
     * 根据脚本ID查询标签ID列表
     * @param scriptId 脚本ID
     * @return 标签ID列表
     */
    @GetMapping("/tags")
    @Operation(summary = "查询脚本标签ID列表", description = "根据脚本ID查询关联的标签ID列表")
    public Result<List<Integer>> getTagIdsByScriptId(@RequestParam String scriptId) {
        return scriptTagService.getTagIdsByScriptId(scriptId);
    }

    /**
     * 删除脚本
     * @param id 脚本ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @SaCheckRole("admin")
    @Operation(summary = "删除脚本", description = "根据脚本ID删除脚本及其MinIO文件")
    public Result<Void> deleteScript(@PathVariable String id) {
        if (scriptService.deleteScript(id)) {
            return Result.success();
        }
        return Result.error(500, "删除失败");
    }

    /**
     * 更新脚本内容
     * @param id 脚本ID
     * @param content 脚本内容
     * @return 操作结果
     */
    @PutMapping(value = "/{id}", consumes = MediaType.TEXT_PLAIN_VALUE)
    @SaCheckRole("admin")
    @Operation(summary = "更新脚本内容", description = "更新指定脚本在MinIO中的内容")
    public Result<Void> updateScriptContent(@PathVariable String id, @RequestBody String content) {
        if (scriptService.updateScriptContent(id, content)) {
            return Result.success();
        }
        return Result.error(500, "更新失败");
    }
}
