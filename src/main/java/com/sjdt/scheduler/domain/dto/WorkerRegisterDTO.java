package com.sjdt.scheduler.domain.dto;

import com.sjdt.scheduler.domain.po.Tag;
import lombok.Data;

import java.util.Set;

/**
 * Worker 注册请求 DTO
 */
@Data
public class WorkerRegisterDTO {

    /** 主机名 */
    private String host;

    /** 内网 IP 地址 */
    private String ip;

    /** 标签 */
    private Set<Tag> tags;

}