package com.sjdt.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("script_tags")
public class ScriptTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Script ID */
    @TableField(value = "script_id")
    private String scriptId;

    /** 标签ID */
    @TableField(value = "tag_id")
    private Integer tagId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
