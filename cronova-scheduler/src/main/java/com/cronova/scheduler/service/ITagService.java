package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.Script;
import com.cronova.scheduler.domain.po.Tag;
import com.cronova.scheduler.domain.po.Worker;

import java.util.List;

/**
 * 标签服务接口
 */
public interface ITagService extends IService<Tag> {
    
    Result<IPage<Tag>> getTagList(int page, int size, String name);
    
    Result<Tag> getTagById(Integer id);
    
    Result<Tag> createTag(Tag tag);
    
    Result<Tag> updateTag(Integer id, Tag tag);
    
    Result<Void> deleteTag(Integer id);

    Result<List<Script>> getTagScripts(Integer tagId);

    Result<List<Worker>> getTagWorkers(Integer tagId);

    Result<Void> bindScript(Integer tagId, String scriptId);

    Result<Void> unbindScript(Integer tagId, String scriptId);

    Result<Void> bindWorker(Integer tagId, String workerId);

    Result<Void> unbindWorker(Integer tagId, String workerId);
}
