package com.sjdt.scheduler.domain.enums;

import com.sjdt.scheduler.exception.InvalidEnumValueException;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum TaskRunStatus {

    PENDING("pending", "等待中"),
    RUNNING("running", "运行中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败"),
    TIMEOUT("timeout", "超时");

    /**
     * 存在数据库/对外传输的字面值
     */
    private final String value;

    /**
     * 中文描述
     */
    private final String description;

    /**
     * 根据 value 反查枚举，找不到抛业务错误 InvalidEnumValueException
     */
    public static TaskRunStatus of(String value) {
        for (TaskRunStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new InvalidEnumValueException();
    }
}