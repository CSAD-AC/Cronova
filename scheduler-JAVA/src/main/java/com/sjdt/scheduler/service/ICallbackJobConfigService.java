package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;

import java.util.List;

/**
 * 回调任务配置Service接口
 */
public interface ICallbackJobConfigService extends IService<CallbackJobConfig> {

    /**
     * 根据任务ID查询回调配置
     *
     * @param taskId 任务ID
     * @return 回调任务配置列表
     */
    List<CallbackJobConfig> getByTaskId(String taskId);

    /**
     * 根据任务ID删除回调配置
     *
     * @param taskId 任务ID
     * @return 删除的记录数
     */
    int deleteByTaskId(String taskId);
}
