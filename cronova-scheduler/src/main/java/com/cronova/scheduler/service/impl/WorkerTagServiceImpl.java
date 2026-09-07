package com.cronova.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.WorkerTag;
import com.cronova.scheduler.mapper.WorkerTagMapper;
import com.cronova.scheduler.service.IWorkerTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Worker标签关联服务实现类
 */
@Service
@Slf4j
public class WorkerTagServiceImpl extends ServiceImpl<WorkerTagMapper, WorkerTag> implements IWorkerTagService {
    
    @Override
    public Result<List<Integer>> getTagIdsByWorkerId(String workerId) {
        List<Integer> tagIds = this.baseMapper.selectTagIdsByWorkerId(workerId);
        return Result.success(tagIds);
    }
    
    @Override
    public Result<List<String>> getWorkerIdsByTagId(Integer tagId) {
        List<String> workerIds = this.baseMapper.selectWorkerIdsByTagId(tagId);
        return Result.success(workerIds);
    }
    
    @Override
    public Result<Void> addTagToWorker(String workerId, Integer tagId) {
        this.baseMapper.addTagToWorker(workerId, tagId);
        return Result.success();
    }
    
    @Override
    public Result<Void> batchAddTagsToWorker(String workerId, List<Integer> tagIds) {
        this.baseMapper.batchAddTagsToWorker(workerId, tagIds);
        return Result.success();
    }
    
    @Override
    public Result<Void> removeTagFromWorker(String workerId, Integer tagId) {
        this.baseMapper.removeTagFromWorker(workerId, tagId);
        return Result.success();
    }
    
    @Override
    public Result<Void> clearTagsFromWorker(String workerId) {
        this.baseMapper.clearTagsFromWorker(workerId);
        return Result.success();
    }
}
