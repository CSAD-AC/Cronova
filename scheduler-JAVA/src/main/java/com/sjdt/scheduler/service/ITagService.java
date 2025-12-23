package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Tag;

/**
 * 标签服务接口
 */
public interface ITagService extends IService<Tag> {
    
    /**
     * 分页查询标签列表
     * @param page 页码
     * @param size 每页大小
     * @param name 标签名称模糊查询
     * @return 标签分页列表
     */
    Result<IPage<Tag>> getTagList(int page, int size, String name);
    
    /**
     * 根据ID查询标签详情
     * @param id 标签ID
     * @return 标签详情
     */
    Result<Tag> getTagById(Integer id);
    
    /**
     * 创建标签
     * @param tag 标签信息
     * @return 创建结果
     */
    Result<Tag> createTag(Tag tag);
    
    /**
     * 更新标签信息
     * @param id 标签ID
     * @param tag 更新信息
     * @return 更新结果
     */
    Result<Tag> updateTag(Integer id, Tag tag);
    
    /**
     * 删除标签
     * @param id 标签ID
     * @return 删除结果
     */
    Result<Void> deleteTag(Integer id);
}
