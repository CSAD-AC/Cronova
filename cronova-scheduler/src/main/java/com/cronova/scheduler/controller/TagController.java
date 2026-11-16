package com.cronova.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.Script;
import com.cronova.scheduler.domain.po.Tag;
import com.cronova.scheduler.domain.po.Worker;
import com.cronova.scheduler.service.ITagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 * 用于处理标签的增删改查操作
 */
@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理", description = "标签的增删改查操作")
public class TagController {

    private final ITagService tagService;

    @GetMapping("/list")
    @Operation(summary = "分页查询标签列表", description = "根据页码、每页大小和标签名称模糊查询标签列表")
    public Result<?> getTagList(@RequestParam(defaultValue = "1") int page, 
                              @RequestParam(defaultValue = "10") int size, 
                              @RequestParam(required = false) String name) {
        return tagService.getTagList(page, size, name);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询标签详情", description = "根据标签ID查询标签详情")
    public Result<Tag> getTagById(@PathVariable Integer id) {
        return tagService.getTagById(id);
    }

    @PostMapping
    @SaCheckRole("admin")
    @Operation(summary = "创建标签", description = "创建新的标签")
    public Result<Tag> createTag(@RequestBody Tag tag) {
        return tagService.createTag(tag);
    }

    @PutMapping("/{id}")
    @SaCheckRole("admin")
    @Operation(summary = "更新标签", description = "根据标签ID更新标签信息")
    public Result<Tag> updateTag(@PathVariable Integer id, @RequestBody Tag tag) {
        return tagService.updateTag(id, tag);
    }

    @DeleteMapping("/{id}")
    @SaCheckRole("admin")
    @Operation(summary = "删除标签", description = "根据标签ID删除标签")
    public Result<Void> deleteTag(@PathVariable Integer id) {
        return tagService.deleteTag(id);
    }

    @GetMapping("/{id}/scripts")
    @Operation(summary = "获取标签关联的脚本列表", description = "根据标签ID查询关联的脚本列表")
    public Result<List<Script>> getTagScripts(@PathVariable Integer id) {
        return tagService.getTagScripts(id);
    }

    @GetMapping("/{id}/workers")
    @Operation(summary = "获取标签关联的执行器列表", description = "根据标签ID查询关联的执行器列表")
    public Result<List<Worker>> getTagWorkers(@PathVariable Integer id) {
        return tagService.getTagWorkers(id);
    }

    @PostMapping("/{id}/scripts/{scriptId}")
    @SaCheckRole("admin")
    @Operation(summary = "关联脚本", description = "将脚本关联到标签")
    public Result<Void> bindScript(@PathVariable Integer id, @PathVariable String scriptId) {
        return tagService.bindScript(id, scriptId);
    }

    @DeleteMapping("/{id}/scripts/{scriptId}")
    @SaCheckRole("admin")
    @Operation(summary = "取消关联脚本", description = "取消标签与脚本的关联")
    public Result<Void> unbindScript(@PathVariable Integer id, @PathVariable String scriptId) {
        return tagService.unbindScript(id, scriptId);
    }

    @PostMapping("/{id}/workers/{workerId}")
    @SaCheckRole("admin")
    @Operation(summary = "关联执行器", description = "将执行器关联到标签")
    public Result<Void> bindWorker(@PathVariable Integer id, @PathVariable String workerId) {
        return tagService.bindWorker(id, workerId);
    }

    @DeleteMapping("/{id}/workers/{workerId}")
    @SaCheckRole("admin")
    @Operation(summary = "取消关联执行器", description = "取消标签与执行器的关联")
    public Result<Void> unbindWorker(@PathVariable Integer id, @PathVariable String workerId) {
        return tagService.unbindWorker(id, workerId);
    }
}
