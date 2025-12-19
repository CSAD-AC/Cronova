package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.po.WorkerTag;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Worker标签关联Mapper接口
 */
@Mapper
public interface WorkerTagMapper extends BaseMapper<WorkerTag> {
    
    /**
     * 根据Worker ID查询标签ID列表
     * @param workerId Worker ID
     * @return 标签ID列表
     */
    default List<Integer> selectTagIdsByWorkerId(String workerId) {
        LambdaQueryWrapper<WorkerTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WorkerTag::getWorkerId, workerId);
        queryWrapper.select(WorkerTag::getTagId);
        return this.selectObjs(queryWrapper).stream()
                .map(obj -> (Integer) obj)
                .toList();
    }
    
    /**
     * 根据标签ID查询Worker ID列表
     * @param tagId 标签ID
     * @return Worker ID列表
     */
    default List<String> selectWorkerIdsByTagId(Integer tagId) {
        LambdaQueryWrapper<WorkerTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WorkerTag::getTagId, tagId);
        queryWrapper.select(WorkerTag::getWorkerId);
        return this.selectObjs(queryWrapper).stream()
                .map(obj -> (String) obj)
                .toList();
    }
    
    /**
     * 为Worker添加标签
     * @param workerId Worker ID
     * @param tagId 标签ID
     * @return 影响行数
     */
    default Integer addTagToWorker(String workerId, Integer tagId) {
        WorkerTag workerTag = new WorkerTag();
        workerTag.setWorkerId(workerId);
        workerTag.setTagId(tagId);
        workerTag.setCreateTime(java.time.LocalDateTime.now());
        return this.insert(workerTag);
    }
    
    /**
     * 为Worker批量添加标签
     * @param workerId Worker ID
     * @param tagIds 标签ID列表
     * @return 影响行数
     */
    default Integer batchAddTagsToWorker(String workerId, List<Integer> tagIds) {
        int count = 0;
        LocalDateTime now = java.time.LocalDateTime.now();
        for (Integer tagId : tagIds) {
            WorkerTag workerTag = new WorkerTag();
            workerTag.setWorkerId(workerId);
            workerTag.setTagId(tagId);
            workerTag.setCreateTime(now);
            count += this.insert(workerTag);
        }
        return count;
    }
    
    /**
     * 移除Worker的标签
     * @param workerId Worker ID
     * @param tagId 标签ID
     * @return 影响行数
     */
    default Integer removeTagFromWorker(String workerId, Integer tagId) {
        LambdaQueryWrapper<WorkerTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WorkerTag::getWorkerId, workerId);
        queryWrapper.eq(WorkerTag::getTagId, tagId);
        return this.delete(queryWrapper);
    }
    
    /**
     * 清空Worker的所有标签
     * @param workerId Worker ID
     * @return 影响行数
     */
    default Integer clearTagsFromWorker(String workerId) {
        LambdaQueryWrapper<WorkerTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WorkerTag::getWorkerId, workerId);
        return this.delete(queryWrapper);
    }
}