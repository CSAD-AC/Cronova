package com.sjdt.scheduler.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.sjdt.scheduler.common.BizException;
import com.sjdt.scheduler.common.ErrorCodeEnum;
import com.sjdt.scheduler.common.Result;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashSet;
import java.util.Set;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常
     * @param e 业务异常
     * @return ResponseEntity 包装了HTTP状态和错误信息
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBizException(BizException e) {
        log.error("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        // 尝试从异常本身获取HTTP状态，例如 @ResponseStatus(HttpStatus.NOT_FOUND)
        ResponseStatus responseStatus = e.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = (responseStatus != null) ? responseStatus.value() : HttpStatus.BAD_REQUEST;

        return new ResponseEntity<>(Result.error(e.getCode(), e.getMessage()), status);
    }

    /**
     * 处理Sa-Token未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<?>> handleNotLoginException(NotLoginException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("用户未登录: {}", e.getMessage());

        return new ResponseEntity<>(Result.error(401, "您尚未登录，请先登录"), HttpStatus.UNAUTHORIZED);
    }

    /**
     * 处理Sa-Token无角色异常
     */
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Result<?>> handleNotRoleException(NotRoleException e) {
        log.warn("用户角色权限不足: {}", e.getMessage());
        return new ResponseEntity<>(Result.error(403, "您的角色权限不足，无法访问"), HttpStatus.FORBIDDEN);
    }

    /**
     * 处理Sa-Token无权限异常
     */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<?>> handleNotPermissionException(NotPermissionException e) {
        log.warn("用户操作权限不足: {}", e.getMessage());
        return new ResponseEntity<>(Result.error(403, "您的操作权限不足，无法访问"), HttpStatus.FORBIDDEN);
    }

    /**
     * 处理参数校验异常 (JSR303)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数校验失败: {}", message);
        // 对于参数校验失败，我们使用 VALIDATION_ERROR 枚举
        Result<?> errorResult = Result.error(ErrorCodeEnum.VALIDATION_ERROR.getCode(), message);
        return new ResponseEntity<>(errorResult, HttpStatus.BAD_REQUEST);
    }


    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数验证失败: {}", e.getMessage());
        Result<?> errorResult = Result.error(ErrorCodeEnum.VALIDATION_ERROR.getCode(), e.getMessage());
        return new ResponseEntity<>(errorResult, HttpStatus.BAD_REQUEST);
    }

    /**
    * 处理请求方式异常
    */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return new ResponseEntity<>(Result.error(ErrorCodeEnum.METHOD_NOT_ALLOWED), HttpStatus.METHOD_NOT_ALLOWED);
    }
    /**
     * 处理其他所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleAllUncaughtException(Exception e) {
        log.error("系统发生未知异常", e);
        return new ResponseEntity<>(Result.error(ErrorCodeEnum.SYSTEM_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
