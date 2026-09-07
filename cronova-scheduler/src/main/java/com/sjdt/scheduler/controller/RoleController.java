package com.sjdt.scheduler.controller;

import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Role;
import com.sjdt.scheduler.service.IRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 角色控制器
 * 用于处理角色的查询操作
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色的查询操作")
@Slf4j
public class RoleController {

    private final IRoleService roleService;

    /**
     * 分页查询角色列表
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param name 角色名称模糊查询
     * @param sortBy 排序字段，支持id/name/createTime
     * @param sortDirection 排序方向，asc或desc，默认desc
     * @return 角色分页列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询角色列表", description = "根据条件分页查询角色列表，支持按名称过滤和排序")
    public Result<?> getRoleList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "createTime") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDirection) {
        log.info("收到查询角色列表请求, page={}, size={}, name={}, sortBy={}, sortDirection={}", 
                page, size, name, sortBy, sortDirection);
        return roleService.getRoleList(page, size, name, sortBy, sortDirection);
    }
}
