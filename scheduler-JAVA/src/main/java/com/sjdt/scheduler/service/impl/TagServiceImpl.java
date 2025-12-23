package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.common.ErrorCodeEnum;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.mapper.TagMapper;
import com.sjdt.scheduler.service.ITagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 标签服务实现类
 */
@Service
@Slf4j
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements ITagService {
    
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
        existingTag.setOperator(tag.getOperator());
        existingTag.setVersion(tag.getVersion());
        
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
}
