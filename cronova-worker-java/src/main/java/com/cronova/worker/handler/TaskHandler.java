package com.cronova.worker.handler;

import com.cronova.worker.model.TaskExecuteMessage;

/**
 * 任务处理器接口（按脚本类型分类）
 */
public interface TaskHandler {

    /** 支持的脚本类型：python / java / go */
    String supportType();

    /** 处理任务（异步执行，提交后立即返回） */
    boolean handle(TaskExecuteMessage task);
}
