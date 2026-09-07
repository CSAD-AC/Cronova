package com.cronova.scheduler.service;

import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.enums.WorkerStatus;
import com.cronova.scheduler.domain.po.Tag;
import com.cronova.scheduler.domain.po.Worker;
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
    private com.cronova.scheduler.mapper.WorkerMapper workerMapper;
    
    @Test
    void testGetTagIdsByWorkerId() {
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId();
        
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .build();
        Result<Tag> createTagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createTagResult.getCode());
        
        Integer tagId = createTagResult.getData().getId();
        
        workerTagService.addTagToWorker(workerId, tagId);
        
        Result<List<Integer>> result = workerTagService.getTagIdsByWorkerId(workerId);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(200, result.getCode());
        Assertions.assertNotNull(result.getData());
        Assertions.assertTrue(result.getData().contains(tagId));
    }
    
    @Test
    void testBatchAddTagsToWorker() {
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId();
        
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签1描述")
                .build();
        Result<Tag> createTag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, createTag1Result.getCode());
        
        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签2描述")
                .build();
        Result<Tag> createTag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, createTag2Result.getCode());
        
        List<Integer> tagIds = List.of(createTag1Result.getData().getId(), createTag2Result.getData().getId());
        
        Result<Void> batchAddResult = workerTagService.batchAddTagsToWorker(workerId, tagIds);
        
        Assertions.assertNotNull(batchAddResult);
        Assertions.assertEquals(200, batchAddResult.getCode());
        
        Result<List<Integer>> getTagIdsResult = workerTagService.getTagIdsByWorkerId(workerId);
        Assertions.assertNotNull(getTagIdsResult);
        Assertions.assertEquals(200, getTagIdsResult.getCode());
        Assertions.assertTrue(getTagIdsResult.getData().containsAll(tagIds));
        Assertions.assertEquals(2, getTagIdsResult.getData().size());
    }
    
    @Test
    void testRemoveTagFromWorker() {
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId();
        
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签")
                .build();
        Result<Tag> createTagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createTagResult.getCode());
        
        Integer tagId = createTagResult.getData().getId();
        
        workerTagService.addTagToWorker(workerId, tagId);
        
        Result<Void> removeResult = workerTagService.removeTagFromWorker(workerId, tagId);
        
        Assertions.assertNotNull(removeResult);
        Assertions.assertEquals(200, removeResult.getCode());
        
        Result<List<Integer>> getTagIdsResult = workerTagService.getTagIdsByWorkerId(workerId);
        Assertions.assertNotNull(getTagIdsResult);
        Assertions.assertEquals(200, getTagIdsResult.getCode());
        Assertions.assertFalse(getTagIdsResult.getData().contains(tagId));
    }
    
    @Test
    void testClearTagsFromWorker() {
        Worker worker = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker);
        
        String workerId = worker.getId();
        
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签1描述")
                .build();
        Result<Tag> createTag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, createTag1Result.getCode());
        
        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签2描述")
                .build();
        Result<Tag> createTag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, createTag2Result.getCode());
        
        List<Integer> tagIds = List.of(createTag1Result.getData().getId(), createTag2Result.getData().getId());
        
        workerTagService.batchAddTagsToWorker(workerId, tagIds);
        
        Result<Void> clearResult = workerTagService.clearTagsFromWorker(workerId);
        
        Assertions.assertNotNull(clearResult);
        Assertions.assertEquals(200, clearResult.getCode());
        
        Result<List<Integer>> getTagIdsResult = workerTagService.getTagIdsByWorkerId(workerId);
        Assertions.assertNotNull(getTagIdsResult);
        Assertions.assertEquals(200, getTagIdsResult.getCode());
        Assertions.assertTrue(getTagIdsResult.getData().isEmpty());
    }
    
    @Test
    void testGetWorkerIdsByTagId() {
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签")
                .build();
        Result<Tag> createTagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createTagResult.getCode());
        
        Integer tagId = createTagResult.getData().getId();
        
        Worker worker1 = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker1);
        
        Worker worker2 = Worker.builder()
                .host("localhost2")
                .ip("127.0.0.2")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(0.0)
                .memUsage(0.0)
                .runningTasks(0)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        workerMapper.insert(worker2);
        
        String workerId1 = worker1.getId();
        String workerId2 = worker2.getId();
        
        workerTagService.addTagToWorker(workerId1, tagId);
        workerTagService.addTagToWorker(workerId2, tagId);
        
        Result<List<String>> result = workerTagService.getWorkerIdsByTagId(tagId);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(200, result.getCode());
        Assertions.assertNotNull(result.getData());
        Assertions.assertTrue(result.getData().containsAll(List.of(workerId1, workerId2)));
        Assertions.assertEquals(2, result.getData().size());
    }
}
