package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.ScriptTag;

import java.util.List;

/**
 * Script标签关联服务接口
 */
public interface IScriptTagService extends IService<ScriptTag> {
    
    /**
     * 根据Script ID查询标签ID列表
     * @param scriptId Script ID
     * @return 标签ID列表
     */
    Result<List<Integer>> getTagIdsByScriptId(String scriptId);
    
    /**
     * 根据标签ID查询Script ID列表
     * @param tagId 标签ID
     * @return Script ID列表
     */
    Result<List<String>> getScriptIdsByTagId(Integer tagId);
    
    /**
     * 为Script添加标签
     * @param scriptId Script ID
     * @param tagId 标签ID
     * @return 操作结果
     */
    Result<Void> addTagToScript(String scriptId, Integer tagId);
    
    /**
     * 为Script批量添加标签
     * @param scriptId Script ID
     * @param tagIds 标签ID列表
     * @return 操作结果
     */
    Result<Void> batchAddTagsToScript(String scriptId, List<Integer> tagIds);
    
    /**
     * 移除Script的标签
     * @param scriptId Script ID
     * @param tagId 标签ID
     * @return 操作结果
     */
    Result<Void> removeTagFromScript(String scriptId, Integer tagId);
    
    /**
     * 清空Script的所有标签
     * @param scriptId Script ID
     * @return 操作结果
     */
    Result<Void> clearTagsFromScript(String scriptId);
}
