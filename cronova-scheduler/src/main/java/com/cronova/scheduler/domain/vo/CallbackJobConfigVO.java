package com.cronova.scheduler.domain.vo;

import com.cronova.scheduler.domain.po.CallbackJobConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

/**
 * 回调任务配置VO
 */
@Data
@Schema(description = "回调任务配置VO")
public class CallbackJobConfigVO {

    /** 配置ID */
    @Schema(description = "配置ID")
    private String id;

    /** 关联任务ID */
    @Schema(description = "关联任务ID")
    private String taskId;

    /** 回调URL */
    @Schema(description = "回调URL")
    private String callbackUrl;

    /** 回调方法：GET/POST/PUT/DELETE */
    @Schema(description = "回调方法")
    private String callbackMethod;

    /** 认证秘钥（用于 X-Secret 请求头） */
    @JsonIgnore
    private String secretKey;

    /** 是否已配置密钥 */
    @Schema(description = "是否已配置密钥")
    private Boolean hasSecretKey;

    /** 上次回调时间 */
    @Schema(description = "上次回调时间")
    private LocalDateTime lastCallbackTime;

    /** 上次回调状态：success/failed/timeout */
    @Schema(description = "上次回调状态")
    private String lastCallbackStatus;

    /** 总回调次数 */
    @Schema(description = "总回调次数")
    private Integer totalCallbackCount;

    /** 成功次数 */
    @Schema(description = "成功次数")
    private Integer successCount;

    /** 失败次数 */
    @Schema(description = "失败次数")
    private Integer failedCount;

    /** 创建人 */
    @Schema(description = "创建人")
    private String createdBy;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    public static CallbackJobConfigVO from(CallbackJobConfig config) {
        CallbackJobConfigVO vo = new CallbackJobConfigVO();
        BeanUtils.copyProperties(config, vo);
        vo.setHasSecretKey(config.getSecretKey() != null && !config.getSecretKey().isBlank());
        return vo;
    }
}
