package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.WorkerTag;

import java.util.List;

/**
 * Worker标签关联服务接口
 */
public interface IWorkerTagService extends IService<WorkerTag> {
    
    /**
     * 根据Worker ID查询标签ID列表
     * @param workerId Worker ID
     * @return 标签ID列表
     */
    Result<List<Integer>> getTagIdsByWorkerId(String workerId);
    
    /**
     * 根据标签ID查询Worker ID列表
     * @param tagId 标签ID
     * @return Worker ID列表
     */
    Result<List<String>> getWorkerIdsByTagId(Integer tagId);
    
    /**
     * 为Worker添加标签
     * @param workerId Worker ID
     * @param tagId 标签ID
     * @return 操作结果
     */
    Result<Void> addTagToWorker(String workerId, Integer tagId);
    
    /**
     * 为Worker批量添加标签
     * @param workerId Worker ID
     * @param tagIds 标签ID列表
     * @return 操作结果
     */
    Result<Void> batchAddTagsToWorker(String workerId, List<Integer> tagIds);
    
    /**
     * 移除Worker的标签
     * @param workerId Worker ID
     * @param tagId 标签ID
     * @return 操作结果
     */
    Result<Void> removeTagFromWorker(String workerId, Integer tagId);
    
    /**
     * 清空Worker的所有标签
     * @param workerId Worker ID
     * @return 操作结果
     */
    Result<Void> clearTagsFromWorker(String workerId);
}
