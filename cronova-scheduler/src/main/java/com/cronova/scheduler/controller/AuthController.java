package com.cronova.scheduler.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.dto.UserLoginDTO;
import com.cronova.scheduler.domain.dto.UserRegisterDTO;
import com.cronova.scheduler.domain.vo.LoginResponseVO;
import com.cronova.scheduler.domain.vo.UserVO;
import com.cronova.scheduler.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录、注册等认证相关接口")
@Slf4j
public class AuthController {

    private final IUserService userService;

    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 注册结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用户注册接口")
    public Result<UserVO> register(@RequestBody UserRegisterDTO registerDTO) {
        log.info("收到用户注册请求,username={}", registerDTO.getUsername());
        return userService.register(registerDTO);
    }

    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return 登录结果，包含用户信息和token
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录接口")
    public Result<LoginResponseVO> login(@RequestBody UserLoginDTO loginDTO) {
        log.info("收到用户登录请求,username={}", loginDTO.getUsername());
        return userService.login(loginDTO);
    }

    /**
     * 用户登出
     * @return 登出结果
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出接口")

    public Result<?> logout() {
        log.info("用户退出登录成功");
        StpUtil.logout();
        return Result.success("登出成功");
    }
}