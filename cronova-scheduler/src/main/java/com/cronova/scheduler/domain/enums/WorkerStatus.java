package com.cronova.scheduler.domain.enums;

import com.cronova.scheduler.exception.InvalidEnumValueException;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum WorkerStatus {
    online("online", "在线"),
    offline("offline", "离线"),
    busy("busy", "忙碌");

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
    public static WorkerStatus of(String value) {
        for (WorkerStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new InvalidEnumValueException();
    }
}