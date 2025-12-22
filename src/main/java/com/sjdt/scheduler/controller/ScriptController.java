package com.sjdt.scheduler.controller;

import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.vo.ScriptVO;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.IScriptTagService;
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
     * 上传脚本文件
     * @param file 脚本文件
     * @return 上传结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传脚本文件", description = "上传脚本文件到MinIO")
    public Result<ScriptVO> uploadScript(@RequestPart("file") MultipartFile file) {
        return Result.success(scriptService.uploadScript(file));
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
     * 为脚本添加标签
     * @param scriptId 脚本ID
     * @param tagId 标签ID
     * @return 操作结果
     */
    @PostMapping("/add")
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
}
