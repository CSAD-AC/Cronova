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

    /** 是否为目录类型（1=是，0=否） */
    private Boolean isDirectory;

    /** 目录类型时的主入口文件路径 */
    private String mainFile;

    /** 父脚本ID，用于构建层级关系 */
    private String parentId;

    /** 标签 */
    private Set<Tag> tags;
}
