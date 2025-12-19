package com.sjdt.scheduler.mapper;

import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.WorkerTag;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.domain.po.Worker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * WorkerTagMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class WorkerTagMapperTest {
    
    @Autowired
    private WorkerTagMapper workerTagMapper;
    
    @Autowired
    private TagMapper tagMapper;
    
    @Autowired
    private WorkerMapper workerMapper;
    
    @Test
    void testAddTagToWorker() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签5-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag);
        
        // 创建测试Worker
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
        
        // 为Worker添加标签
        workerTagMapper.addTagToWorker(workerId, tag.getId());
        
        // 查询Worker的标签
        List<Integer> tagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        // 验证查询结果
        Assertions.assertNotNull(tagIds);
        Assertions.assertTrue(tagIds.contains(tag.getId()));
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testBatchAddTagsToWorker() {
        // 创建测试Worker
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
        
        // 创建测试标签1
        Tag tag1 = Tag.builder()
                .name("测试标签6-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag1);
        
        // 创建测试标签2
        Tag tag2 = Tag.builder()
                .name("测试标签7-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag2);
        
        List<Integer> tagIds = List.of(tag1.getId(), tag2.getId());
        
        // 为Worker批量添加标签
        workerTagMapper.batchAddTagsToWorker(workerId, tagIds);
        
        // 查询Worker的标签
        List<Integer> foundTagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        // 验证查询结果
        Assertions.assertNotNull(foundTagIds);
        Assertions.assertEquals(2, foundTagIds.size());
        Assertions.assertTrue(foundTagIds.containsAll(tagIds));
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testRemoveTagFromWorker() {
        // 创建测试Worker
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
        
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签8-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag);
        
        // 为Worker添加标签
        workerTagMapper.addTagToWorker(workerId, tag.getId());
        
        // 移除Worker的标签
        workerTagMapper.removeTagFromWorker(workerId, tag.getId());
        
        // 查询Worker的标签
        List<Integer> tagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        // 验证查询结果
        Assertions.assertNotNull(tagIds);
        Assertions.assertFalse(tagIds.contains(tag.getId()));

    }
    
    @Test
    void testClearTagsFromWorker() {
        // 创建测试Worker
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
        
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签9-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag);
        
        // 为Worker添加标签
        workerTagMapper.addTagToWorker(workerId, tag.getId());
        
        // 清空Worker的所有标签
        workerTagMapper.clearTagsFromWorker(workerId);
        
        // 查询Worker的标签
        List<Integer> tagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        // 验证查询结果
        Assertions.assertNotNull(tagIds);
        Assertions.assertTrue(tagIds.isEmpty());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testSelectWorkerIdsByTagId() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签10-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag);
        
        // 创建测试Worker1
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
        
        // 创建测试Worker2
        Worker worker2 = Worker.builder()
                .host("localhost")
                .ip("127.0.0.1")
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
        
        // 为多个Worker添加标签
        workerTagMapper.addTagToWorker(workerId1, tag.getId());
        workerTagMapper.addTagToWorker(workerId2, tag.getId());
        
        // 查询拥有该标签的Worker
        List<String> workerIds = workerTagMapper.selectWorkerIdsByTagId(tag.getId());
        
        // 验证查询结果
        Assertions.assertNotNull(workerIds);
        Assertions.assertEquals(2, workerIds.size());
        Assertions.assertTrue(workerIds.containsAll(List.of(workerId1, workerId2)));
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
}