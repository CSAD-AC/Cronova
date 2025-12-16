package com.sjdt.scheduler.domain.enums;

import com.sjdt.scheduler.exception.InvalidEnumValueException;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum TaskType {
    ONCE("once", "一次性任务"),
    CRON("cron", "定时任务");

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
    public static TaskType of(String value) {
        for (TaskType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new InvalidEnumValueException();
    }
}