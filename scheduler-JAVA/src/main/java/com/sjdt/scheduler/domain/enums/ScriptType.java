package com.sjdt.scheduler.domain.enums;

import com.sjdt.scheduler.exception.InvalidEnumValueException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 脚本类型枚举
 * 表示任务的脚本类型
 */
@Getter
@AllArgsConstructor
public enum ScriptType {
    java("java", "Java 脚本"),
    python("python", "Python 脚本"),
    go("go", "Go 脚本");

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
    public static ScriptType of(String value) {
        for (ScriptType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new InvalidEnumValueException();
    }
}