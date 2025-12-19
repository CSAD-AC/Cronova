package com.sjdt.scheduler.common;

import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
public enum ErrorCodeEnum {
    // 定义系统级错误
    SYSTEM_ERROR(500, "系统内部错误，请联系管理员"),
    VALIDATION_ERROR(400, "参数校验失败"),
    METHOD_NOT_ALLOWED(405, "请求方法不被允许"),

    // 定义业务错误
    INVALID_ENUM_VALUE(1001, "枚举值非法"),
    USER_IS_EXISTS(1002, "用户已经存在"),
    USER_NOT_FOUND(1003, "用户不存在"),
    TAG_NOT_FOUND(1004, "标签不存在"),

    // 文件相关错误 2001-2099
    FILE_UPLOAD_ERROR(2001, "文件上传失败"),
    FILE_NAME_EMPTY(2002, "文件名为空"),
    FILE_MD5_ERROR(2003, "文件MD5计算失败"),
    FILE_DOWNLOAD_ERROR(2004, "文件下载失败"),
    FILE_URL_INVALID(2005, "文件URL无效或格式错误");


    private final int code;
    private final String message;

    ErrorCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
