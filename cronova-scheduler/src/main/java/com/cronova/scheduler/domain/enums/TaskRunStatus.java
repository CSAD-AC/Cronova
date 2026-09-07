package com.cronova.scheduler.domain.enums;

import com.cronova.scheduler.exception.InvalidEnumValueException;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum TaskRunStatus {

    pending("pending", "等待中"),
    running("running", "运行中"),
    paused("paused", "暂停"),
    success("success", "成功"),
    failed("failed", "失败"),
    timeout("timeout", "超时"),
    queued("queued", "排队中"),
    skipped("skipped", "被阻塞丢弃");

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