package com.sjdt.scheduler.domain.enums;

import com.sjdt.scheduler.exception.InvalidEnumValueException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务执行类型枚举
 */
@Getter
@AllArgsConstructor
public enum ExecuteType {

    script("script", "脚本执行"),
    callback("callback", "HTTP回调");

    private final String value;
    private final String description;

    /**
     * 根据 value 反查枚举，找不到抛业务错误 InvalidEnumValueException
     */
    public static ExecuteType of(String value) {
        for (ExecuteType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new InvalidEnumValueException();
    }
}
