package com.sjdt.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("scripts")
public class Script implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 脚本名称 */
    private String name;

    /** 脚本路径 */
    private String path;

    /** 是否为目录类型（1=是，0=否） */
    @TableField(value = "is_directory")
    private Boolean isDirectory;

    /** 目录类型时的主入口文件路径 */
    @TableField(value = "main_file")
    private String mainFile;

    /** 父脚本ID，用于构建层级关系 */
    @TableField(value = "parent_id")
    private String parentId;

    /** 标签 */
    @TableField(exist = false)
    private Set<Tag> tags;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private LocalDateTime updateTime;
}
