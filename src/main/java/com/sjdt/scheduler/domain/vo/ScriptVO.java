package com.sjdt.scheduler.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScriptVO {
    /** 脚本ID */
    private String id;

    /** 脚本名称 */
    private String name;

    /** 脚本路径 */
    private String path;
}
