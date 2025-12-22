package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.po.ScriptTag;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Script标签关联Mapper接口
 */
@Mapper
public interface ScriptTagMapper extends BaseMapper<ScriptTag> {
    
    /**
     * 根据Script ID查询标签ID列表
     * @param scriptId Script ID
     * @return 标签ID列表
     */
    default List<Integer> selectTagIdsByScriptId(String scriptId) {
        LambdaQueryWrapper<ScriptTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ScriptTag::getScriptId, scriptId);
        queryWrapper.select(ScriptTag::getTagId);
        return this.selectObjs(queryWrapper).stream()
                .map(obj -> (Integer) obj)
                .toList();
    }
    
    /**
     * 根据标签ID查询Script ID列表
     * @param tagId 标签ID
     * @return Script ID列表
     */
    default List<String> selectScriptIdsByTagId(Integer tagId) {
        LambdaQueryWrapper<ScriptTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ScriptTag::getTagId, tagId);
        queryWrapper.select(ScriptTag::getScriptId);
        return this.selectObjs(queryWrapper).stream()
                .map(obj -> (String) obj)
                .toList();
    }
    
    /**
     * 为Script添加标签
     * @param scriptId Script ID
     * @param tagId 标签ID
     * @return 影响行数
     */
    default Integer addTagToScript(String scriptId, Integer tagId) {
        ScriptTag scriptTag = new ScriptTag();
        scriptTag.setScriptId(scriptId);
        scriptTag.setTagId(tagId);
        scriptTag.setCreateTime(java.time.LocalDateTime.now());
        return this.insert(scriptTag);
    }
    
    /**
     * 为Script批量添加标签
     * @param scriptId Script ID
     * @param tagIds 标签ID列表
     * @return 影响行数
     */
    default Integer batchAddTagsToScript(String scriptId, List<Integer> tagIds) {
        int count = 0;
        LocalDateTime now = java.time.LocalDateTime.now();
        for (Integer tagId : tagIds) {
            ScriptTag scriptTag = new ScriptTag();
            scriptTag.setScriptId(scriptId);
            scriptTag.setTagId(tagId);
            scriptTag.setCreateTime(now);
            count += this.insert(scriptTag);
        }
        return count;
    }
    
    /**
     * 移除Script的标签
     * @param scriptId Script ID
     * @param tagId 标签ID
     * @return 影响行数
     */
    default Integer removeTagFromScript(String scriptId, Integer tagId) {
        LambdaQueryWrapper<ScriptTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ScriptTag::getScriptId, scriptId);
        queryWrapper.eq(ScriptTag::getTagId, tagId);
        return this.delete(queryWrapper);
    }
    
    /**
     * 清空Script的所有标签
     * @param scriptId Script ID
     * @return 影响行数
     */
    default Integer clearTagsFromScript(String scriptId) {
        LambdaQueryWrapper<ScriptTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ScriptTag::getScriptId, scriptId);
        return this.delete(queryWrapper);
    }
}
