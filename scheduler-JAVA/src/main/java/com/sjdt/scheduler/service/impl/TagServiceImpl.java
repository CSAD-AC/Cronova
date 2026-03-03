package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.common.ErrorCodeEnum;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.ScriptTag;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.domain.po.WorkerTag;
import com.sjdt.scheduler.domain.message.UpdateTagsRequest;
import com.sjdt.scheduler.mapper.ScriptTagMapper;
import com.sjdt.scheduler.mapper.TagMapper;
import com.sjdt.scheduler.mapper.WorkerTagMapper;
import com.sjdt.scheduler.service.ITagService;
import com.sjdt.scheduler.service.TaskControlProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements ITagService {

    @Lazy
    private final ScriptTagMapper scriptTagMapper;

    @Lazy
    private final WorkerTagMapper workerTagMapper;

    @Lazy
    private final TaskControlProducerService taskControlProducerService;
    
    @Override
    public Result<IPage<Tag>> getTagList(int page, int size, String name) {
        // 构建查询条件
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Tag::getName, name);
        }
        // 按创建时间倒序排序
        queryWrapper.orderByDesc(Tag::getCreateTime);
        
        // 分页查询
        IPage<Tag> tagPage = this.page(new Page<>(page, size), queryWrapper);
        
        // 填充关联数量
        tagPage.getRecords().forEach(tag -> {
            Long scriptCount = scriptTagMapper.selectCount(
                    new LambdaQueryWrapper<ScriptTag>().eq(ScriptTag::getTagId, tag.getId())
            );
            Long workerCount = workerTagMapper.selectCount(
                    new LambdaQueryWrapper<WorkerTag>().eq(WorkerTag::getTagId, tag.getId())
            );
            tag.setScriptCount(scriptCount.intValue());
            tag.setWorkerCount(workerCount.intValue());
        });
        
        return Result.success(tagPage);
    }
    
    @Override
    public Result<Tag> getTagById(Integer id) {
        // 根据ID查询标签
        Tag tag = this.getById(id);
        if (tag == null) {
            return Result.error(ErrorCodeEnum.TAG_NOT_FOUND);
        }
        
        return Result.success(tag);
    }
    
    @Override
    public Result<Tag> createTag(Tag tag) {
        // 设置创建时间
        tag.setCreateTime(LocalDateTime.now());
        
        // 保存标签
        this.save(tag);
        
        return Result.success(tag);
    }
    
    @Override
    public Result<Tag> updateTag(Integer id, Tag tag) {
        // 根据ID查询标签
        Tag existingTag = this.getById(id);
        if (existingTag == null) {
            return Result.error(ErrorCodeEnum.TAG_NOT_FOUND);
        }
        
        // 更新标签信息
        existingTag.setName(tag.getName());
        existingTag.setDescription(tag.getDescription());
        
        // 保存更新
        this.updateById(existingTag);
        
        return Result.success(existingTag);
    }
    
    @Override
    public Result<Void> deleteTag(Integer id) {
        // 根据ID查询标签
        Tag tag = this.getById(id);
        if (tag == null) {
            return Result.error(ErrorCodeEnum.TAG_NOT_FOUND);
        }
        
        // 删除标签
        this.removeById(id);
        
        return Result.success();
    }

    @Override
    public Result<List<Script>> getTagScripts(Integer tagId) {
        Tag tag = this.getById(tagId);
        if (tag == null) {
            return Result.error(ErrorCodeEnum.TAG_NOT_FOUND);
        }

        List<Script> scripts = scriptTagMapper.selectScriptsByTagId(tagId);
        return Result.success(scripts);
    }

    @Override
    public Result<List<Worker>> getTagWorkers(Integer tagId) {
        Tag tag = this.getById(tagId);
        if (tag == null) {
            return Result.error(ErrorCodeEnum.TAG_NOT_FOUND);
        }

        List<Worker> workers = workerTagMapper.selectWorkersByTagId(tagId);
        return Result.success(workers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> bindScript(Integer tagId, String scriptId) {
        Tag tag = this.getById(tagId);
        if (tag == null) {
            return Result.error(ErrorCodeEnum.TAG_NOT_FOUND);
        }

        // 检查是否已关联
        Long count = scriptTagMapper.selectCount(
                new LambdaQueryWrapper<ScriptTag>()
                        .eq(ScriptTag::getTagId, tagId)
                        .eq(ScriptTag::getScriptId, scriptId)
        );

        if (count > 0) {
            return Result.success();
        }

        ScriptTag scriptTag = new ScriptTag();
        scriptTag.setTagId(tagId);
        scriptTag.setScriptId(scriptId);
        scriptTag.setCreateTime(LocalDateTime.now());
        scriptTagMapper.insert(scriptTag);

        log.info("关联脚本成功，tagId: {}, scriptId: {}", tagId, scriptId);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbindScript(Integer tagId, String scriptId) {
        scriptTagMapper.delete(
                new LambdaQueryWrapper<ScriptTag>()
                        .eq(ScriptTag::getTagId, tagId)
                        .eq(ScriptTag::getScriptId, scriptId)
        );

        log.info("取消关联脚本成功，tagId: {}, scriptId: {}", tagId, scriptId);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> bindWorker(Integer tagId, String workerId) {
        Tag tag = this.getById(tagId);
        if (tag == null) {
            return Result.error(ErrorCodeEnum.TAG_NOT_FOUND);
        }

        // 检查是否已关联
        Long count = workerTagMapper.selectCount(
                new LambdaQueryWrapper<WorkerTag>()
                        .eq(WorkerTag::getTagId, tagId)
                        .eq(WorkerTag::getWorkerId, workerId)
        );

        if (count > 0) {
            return Result.success();
        }

        WorkerTag workerTag = new WorkerTag();
        workerTag.setTagId(tagId);
        workerTag.setWorkerId(workerId);
        workerTag.setCreateTime(LocalDateTime.now());
        workerTagMapper.insert(workerTag);

        log.info("关联执行器成功，tagId: {}, workerId: {}", tagId, workerId);
        
        // 发送标签更新消息到Worker
        sendTagUpdateMessage(workerId);
        
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbindWorker(Integer tagId, String workerId) {
        workerTagMapper.delete(
                new LambdaQueryWrapper<WorkerTag>()
                        .eq(WorkerTag::getTagId, tagId)
                        .eq(WorkerTag::getWorkerId, workerId)
        );

        log.info("取消关联执行器成功，tagId: {}, workerId: {}", tagId, workerId);
        
        // 发送标签更新消息到Worker
        sendTagUpdateMessage(workerId);
        
        return Result.success();
    }
    
    /**
     * 发送标签更新消息到Worker
     */
    private void sendTagUpdateMessage(String workerId) {
        try {
            // 获取Worker的所有标签
            List<WorkerTag> workerTags = workerTagMapper.selectList(
                new LambdaQueryWrapper<WorkerTag>()
                    .eq(WorkerTag::getWorkerId, workerId)
            );
            
            List<String> tagNames = workerTags.stream()
                .map(wt -> {
                    Tag t = this.getById(wt.getTagId());
                    return t != null ? t.getName() : null;
                })
                .filter(name -> name != null)
                .collect(Collectors.toList());
            
            // 发送MQ消息
            UpdateTagsRequest request = new UpdateTagsRequest();
            request.setWorkerId(workerId);
            request.setTags(tagNames);
            taskControlProducerService.sendUpdateTags(request);
            
            log.info("已发送标签更新消息到Worker | workerId: {}, tags: {}", workerId, tagNames);
        } catch (Exception e) {
            log.error("发送标签更新消息失败 | workerId: {}, error: {}", workerId, e.getMessage());
        }
    }
}
