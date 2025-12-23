package com.sjdt.scheduler.domain.vo;

import com.sjdt.scheduler.domain.po.Tag;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class ScriptVO {
    /** 脚本ID */
    private String id;

    /** 脚本名称 */
    private String name;

    /** 脚本路径 */
    private String path;

    /** 标签 */
    private Set<Tag> tags;
}
