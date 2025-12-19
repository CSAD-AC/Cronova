package com.sjdt.scheduler.service;

import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.domain.po.Worker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * WorkerTagService测试类
 */
@SpringBootTest
@Transactional
@Rollback
class WorkerTagServiceTest {
    
    @Autowired
    private IWorkerTagService workerTagService;
    
    @Autowired
    private ITagService tagService;
    
    @Autowired
    private com.sjdt.scheduler.mapper.WorkerMapper workerMapper; // 添加WorkerMapper依赖
    
    @Test
    void testGetTagIdsByWorkerId() {
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status("online")
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId(); // 使用真实生成的workerId
        
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签14-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        Result<Tag> createTagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createTagResult.getCode());
        
        Integer tagId = createTagResult.getData().getId();
        
        // 为Worker添加标签
        workerTagService.addTagToWorker(workerId, tagId);
        
        // 测试获取Worker的标签ID列表
        Result<List<Integer>> result = workerTagService.getTagIdsByWorkerId(workerId);
        
        // 验证查询结果
        Assertions.assertNotNull(result);
        Assertions.assertEquals(200, result.getCode());
        Assertions.assertNotNull(result.getData());
        Assertions.assertTrue(result.getData().contains(tagId));
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testBatchAddTagsToWorker() {
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status("online")
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId(); // 使用真实生成的workerId
        
        // 创建测试标签1
        Tag tag1 = Tag.builder()
                .name("测试标签15-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        Result<Tag> createTag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, createTag1Result.getCode());
        
        // 创建测试标签2
        Tag tag2 = Tag.builder()
                .name("测试标签16-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        Result<Tag> createTag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, createTag2Result.getCode());
        
        List<Integer> tagIds = List.of(createTag1Result.getData().getId(), createTag2Result.getData().getId());
        
        // 测试为Worker批量添加标签
        Result<Void> batchAddResult = workerTagService.batchAddTagsToWorker(workerId, tagIds);
        
        // 验证批量添加结果
        Assertions.assertNotNull(batchAddResult);
        Assertions.assertEquals(200, batchAddResult.getCode());
        
        // 验证添加的标签
        Result<List<Integer>> getTagIdsResult = workerTagService.getTagIdsByWorkerId(workerId);
        Assertions.assertNotNull(getTagIdsResult);
        Assertions.assertEquals(200, getTagIdsResult.getCode());
        Assertions.assertTrue(getTagIdsResult.getData().containsAll(tagIds));
        Assertions.assertEquals(2, getTagIdsResult.getData().size());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testRemoveTagFromWorker() {
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status("online")
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId(); // 使用真实生成的workerId
        
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签17-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        Result<Tag> createTagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createTagResult.getCode());
        
        Integer tagId = createTagResult.getData().getId();
        
        // 为Worker添加标签
        workerTagService.addTagToWorker(workerId, tagId);
        
        // 测试移除Worker的标签
        Result<Void> removeResult = workerTagService.removeTagFromWorker(workerId, tagId);
        
        // 验证移除结果
        Assertions.assertNotNull(removeResult);
        Assertions.assertEquals(200, removeResult.getCode());
        
        // 验证标签已被移除
        Result<List<Integer>> getTagIdsResult = workerTagService.getTagIdsByWorkerId(workerId);
        Assertions.assertNotNull(getTagIdsResult);
        Assertions.assertEquals(200, getTagIdsResult.getCode());
        Assertions.assertFalse(getTagIdsResult.getData().contains(tagId));
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testClearTagsFromWorker() {
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status("online")
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId(); // 使用真实生成的workerId
        
        // 创建测试标签1
        Tag tag1 = Tag.builder()
                .name("测试标签18-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        Result<Tag> createTag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, createTag1Result.getCode());
        
        // 创建测试标签2
        Tag tag2 = Tag.builder()
                .name("测试标签19-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        Result<Tag> createTag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, createTag2Result.getCode());
        
        List<Integer> tagIds = List.of(createTag1Result.getData().getId(), createTag2Result.getData().getId());
        
        // 为Worker批量添加标签
        workerTagService.batchAddTagsToWorker(workerId, tagIds);
        
        // 测试清空Worker的所有标签
        Result<Void> clearResult = workerTagService.clearTagsFromWorker(workerId);
        
        // 验证清空结果
        Assertions.assertNotNull(clearResult);
        Assertions.assertEquals(200, clearResult.getCode());
        
        // 验证所有标签已被清空
        Result<List<Integer>> getTagIdsResult = workerTagService.getTagIdsByWorkerId(workerId);
        Assertions.assertNotNull(getTagIdsResult);
        Assertions.assertEquals(200, getTagIdsResult.getCode());
        Assertions.assertTrue(getTagIdsResult.getData().isEmpty());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testGetWorkerIdsByTagId() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签20-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        Result<Tag> createTagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createTagResult.getCode());
        
        Integer tagId = createTagResult.getData().getId();
        
        // 创建测试Worker1
        Worker worker1 = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status("online")
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker1);
        
        // 创建测试Worker2
        Worker worker2 = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status("online")
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker2);
        
        String workerId1 = worker1.getId(); // 使用真实生成的workerId
        String workerId2 = worker2.getId(); // 使用真实生成的workerId
        
        // 为多个Worker添加相同标签
        workerTagService.addTagToWorker(workerId1, tagId);
        workerTagService.addTagToWorker(workerId2, tagId);
        
        // 测试获取拥有该标签的Worker ID列表
        Result<List<String>> result = workerTagService.getWorkerIdsByTagId(tagId);
        
        // 验证查询结果
        Assertions.assertNotNull(result);
        Assertions.assertEquals(200, result.getCode());
        Assertions.assertNotNull(result.getData());
        Assertions.assertTrue(result.getData().containsAll(List.of(workerId1, workerId2)));
        Assertions.assertEquals(2, result.getData().size());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
}