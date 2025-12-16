package com.sjdt.scheduler.domain.enums;

import com.sjdt.scheduler.exception.InvalidEnumValueException;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum TaskStatus {
    ACTIVE("active", "活跃"),
    PAUSED("paused", "暂停"),
    DELETED("deleted", "已删除");

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
    public static TaskStatus of(String value) {
        for (TaskStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new InvalidEnumValueException();
    }
}