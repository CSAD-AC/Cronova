package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.ScriptTag;
import com.sjdt.scheduler.mapper.ScriptTagMapper;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.IScriptTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Script标签关联服务实现类
 */
@Service
@Slf4j
public class ScriptTagServiceImpl extends ServiceImpl<ScriptTagMapper, ScriptTag> implements IScriptTagService {
    
    @Autowired
    @Lazy
    private IScriptService scriptService;
    
    @Override
    public Result<List<Integer>> getTagIdsByScriptId(String scriptId) {
        List<Integer> tagIds = this.baseMapper.selectTagIdsByScriptId(scriptId);
        return Result.success(tagIds);
    }
    
    @Override
    public Result<List<String>> getScriptIdsByTagId(Integer tagId) {
        List<String> scriptIds = this.baseMapper.selectScriptIdsByTagId(tagId);
        return Result.success(scriptIds);
    }
    
    @Override
    public Result<Void> addTagToScript(String scriptId, Integer tagId) {
        this.baseMapper.addTagToScript(scriptId, tagId);
        scriptService.clearCache(scriptId);
        return Result.success();
    }
    
    @Override
    public Result<Void> batchAddTagsToScript(String scriptId, List<Integer> tagIds) {
        this.baseMapper.batchAddTagsToScript(scriptId, tagIds);
        scriptService.clearCache(scriptId);
        return Result.success();
    }
    
    @Override
    public Result<Void> removeTagFromScript(String scriptId, Integer tagId) {
        this.baseMapper.removeTagFromScript(scriptId, tagId);
        scriptService.clearCache(scriptId);
        return Result.success();
    }
    
    @Override
    public Result<Void> clearTagsFromScript(String scriptId) {
        this.baseMapper.clearTagsFromScript(scriptId);
        scriptService.clearCache(scriptId);
        return Result.success();
    }
}
