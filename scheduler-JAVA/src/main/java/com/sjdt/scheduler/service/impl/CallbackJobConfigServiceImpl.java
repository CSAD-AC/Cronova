package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.service.ICallbackJobConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回调任务配置Service实现类
 */
@Slf4j
@Service
public class CallbackJobConfigServiceImpl extends ServiceImpl<CallbackJobConfigMapper, CallbackJobConfig> implements ICallbackJobConfigService {

    /**
     * 根据任务ID查询回调配置
     *
     * @param taskId 任务ID
     * @return 回调任务配置列表
     */
    @Override
    public List<CallbackJobConfig> getByTaskId(String taskId) {
        log.info("根据任务ID查询回调配置，任务ID：{}", taskId);
        return this.lambdaQuery()
                .eq(CallbackJobConfig::getTaskId, taskId)
                .list();
    }

    /**
     * 根据任务ID删除回调配置
     *
     * @param taskId 任务ID
     * @return 删除的记录数
     */
    @Override
    public int deleteByTaskId(String taskId) {
        log.info("根据任务ID删除回调配置，任务ID：{}", taskId);
        LambdaQueryWrapper<CallbackJobConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CallbackJobConfig::getTaskId, taskId);
        return this.baseMapper.delete(queryWrapper);
    }
}
