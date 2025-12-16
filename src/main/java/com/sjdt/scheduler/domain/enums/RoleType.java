package com.sjdt.scheduler.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoleType {
    ADMIN("admin", 1),
    USER("user", 2);

    private final String name;
    private final Integer id;
}
