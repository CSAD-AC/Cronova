package com.sjdt.scheduler.controller;

import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.vo.ScriptVO;
import com.sjdt.scheduler.service.IScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
}
