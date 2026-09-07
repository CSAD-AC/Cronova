package com.cronova.scheduler.domain.vo;

import lombok.Data;

/**
 * 日志偏移读取结果（增量拉取协议）。
 * <p>
 * 前端用 offset 定位，每次只拉 [offset, offset+limit) 字节；nextOffset 作为下次请求的
 * offset，直到 finalized=true 停止轮询。
 */
@Data
public class LogOffsetResult {
    /** 本次返回的日志内容（UTF-8，保证不截断多字节字符） */
    private String content;
    /** 本次读取起始偏移（回显） */
    private long offset;
    /** 下次请求应使用的偏移（= offset + content 字节数） */
    private long nextOffset;
    /** 日志对象当前总字节数 */
    private long size;
    /** 偏移失效标记：>0 表示对象已被重建/截断，前端应从 0 重读 */
    private long truncatedHeadBytes;
    /** 任务是否已终止（终止后前端停止轮询） */
    private boolean finalized;
}
