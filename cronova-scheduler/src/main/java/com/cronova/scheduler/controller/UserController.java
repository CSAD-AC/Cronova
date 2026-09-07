package com.cronova.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.dto.UserUpdateDTO;
import com.cronova.scheduler.domain.po.User;
import com.cronova.scheduler.domain.vo.UserVO;
import com.cronova.scheduler.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户的增删改查及角色分配接口")
@Slf4j
@SaCheckRole("admin")
public class UserController {

    private final IUserService userService;

    /**
     * 分页查询用户列表
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param username 用户名模糊查询，可选
     * @return 用户分页列表
     */
    @GetMapping
    @Operation(summary = "分页查询用户列表", description = "根据条件分页查询用户列表")
    public Result<IPage<UserVO>> getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        log.info("收到查询用户列表请求, page={}, size={}, username={}", page, size, username);
        return userService.getUserList(page, size, username);
    }

    /**
     * 根据ID查询用户详情
     * @param id 用户ID
     * @return 用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询用户详情", description = "根据用户ID查询用户详细信息")
    public Result<UserVO> getUserById(@PathVariable String id) {
        log.info("收到查询用户详情请求, id={}", id);
        return userService.getUserById(id);
    }

    /**
     * 创建用户
     * @param user 用户信息
     * @return 创建结果
     */
    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户")
    public Result<UserVO> createUser(@RequestBody User user) {
        log.info("收到创建用户请求, username={}", user.getUsername());
        return userService.createUser(user);
    }

    /**
     * 更新用户信息
     * @param id 用户ID
     * @param userUpdateDTO 更新信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户信息", description = "根据用户ID更新用户信息")
    public Result<UserVO> updateUser(@PathVariable String id, @RequestBody UserUpdateDTO userUpdateDTO) {
        log.info("收到更新用户请求, id={}", id);
        return userService.updateUser(id, userUpdateDTO);
    }

    /**
     * 删除用户
     * @param id 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据用户ID删除用户")
    public Result<Void> deleteUser(@PathVariable String id) {
        log.info("收到删除用户请求, id={}", id);
        return userService.deleteUser(id);
    }

    /**
     * 为用户分配角色
     * @param id 用户ID
     * @param roleIds 角色ID列表
     * @return 分配结果
     */
    @PostMapping("/{id}/roles")
    @Operation(summary = "为用户分配角色", description = "为指定用户分配角色")
    public Result<Void> assignRoles(@PathVariable String id, @RequestBody List<Integer> roleIds) {
        log.info("收到为用户分配角色请求, userId={}, roleIds={}", id, roleIds);
        return userService.assignRoles(id, roleIds);
    }

    /**
     * 获取用户的角色列表
     * @param id 用户ID
     * @return 角色ID列表
     */
    @GetMapping("/{id}/roles")
    @Operation(summary = "获取用户的角色列表", description = "获取指定用户的角色ID列表")
    public Result<List<Integer>> getUserRoles(@PathVariable String id) {
        log.info("收到获取用户角色请求, userId={}", id);
        return userService.getUserRoles(id);
    }
}