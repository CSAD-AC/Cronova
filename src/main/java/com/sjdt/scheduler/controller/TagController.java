package com.sjdt.scheduler.controller;

import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.service.ITagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 分页查询标签列表
     * @param page 页码
     * @param size 每页大小
     * @param name 标签名称模糊查询
     * @return 标签分页列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询标签列表", description = "根据页码、每页大小和标签名称模糊查询标签列表")
    public Result<?> getTagList(@RequestParam(defaultValue = "1") int page, 
                              @RequestParam(defaultValue = "10") int size, 
                              @RequestParam(required = false) String name) {
        return tagService.getTagList(page, size, name);
    }

    /**
     * 根据ID查询标签详情
     * @param id 标签ID
     * @return 标签详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询标签详情", description = "根据标签ID查询标签详情")
    public Result<Tag> getTagById(@PathVariable Integer id) {
        return tagService.getTagById(id);
    }

    /**
     * 创建标签
     * @param tag 标签信息
     * @return 创建结果
     */
    @PostMapping
    @Operation(summary = "创建标签", description = "创建新的标签")
    public Result<Tag> createTag(@RequestBody Tag tag) {
        return tagService.createTag(tag);
    }

    /**
     * 更新标签信息
     * @param id 标签ID
     * @param tag 更新信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新标签", description = "根据标签ID更新标签信息")
    public Result<Tag> updateTag(@PathVariable Integer id, @RequestBody Tag tag) {
        return tagService.updateTag(id, tag);
    }

    /**
     * 删除标签
     * @param id 标签ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签", description = "根据标签ID删除标签")
    public Result<Void> deleteTag(@PathVariable Integer id) {
        return tagService.deleteTag(id);
    }
}
