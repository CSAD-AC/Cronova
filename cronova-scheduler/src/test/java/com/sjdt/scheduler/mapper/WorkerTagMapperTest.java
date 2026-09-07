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
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .build();
        tagMapper.insert(tag);
        
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
        
        workerTagMapper.addTagToWorker(workerId, tag.getId());
        
        List<Integer> tagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        Assertions.assertNotNull(tagIds);
        Assertions.assertTrue(tagIds.contains(tag.getId()));
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
        tagMapper.insert(tag1);
        
        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签2描述")
                .build();
        tagMapper.insert(tag2);
        
        List<Integer> tagIds = List.of(tag1.getId(), tag2.getId());
        
        workerTagMapper.batchAddTagsToWorker(workerId, tagIds);
        
        List<Integer> foundTagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        Assertions.assertNotNull(foundTagIds);
        Assertions.assertEquals(2, foundTagIds.size());
        Assertions.assertTrue(foundTagIds.containsAll(tagIds));
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
        tagMapper.insert(tag);
        
        workerTagMapper.addTagToWorker(workerId, tag.getId());
        
        workerTagMapper.removeTagFromWorker(workerId, tag.getId());
        
        List<Integer> tagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        Assertions.assertNotNull(tagIds);
        Assertions.assertFalse(tagIds.contains(tag.getId()));
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
        
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签")
                .build();
        tagMapper.insert(tag);
        
        workerTagMapper.addTagToWorker(workerId, tag.getId());
        
        workerTagMapper.clearTagsFromWorker(workerId);
        
        List<Integer> tagIds = workerTagMapper.selectTagIdsByWorkerId(workerId);
        
        Assertions.assertNotNull(tagIds);
        Assertions.assertTrue(tagIds.isEmpty());
    }
    
    @Test
    void testSelectWorkerIdsByTagId() {
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签")
                .build();
        tagMapper.insert(tag);
        
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
        
        workerTagMapper.addTagToWorker(workerId1, tag.getId());
        workerTagMapper.addTagToWorker(workerId2, tag.getId());
        
        List<String> workerIds = workerTagMapper.selectWorkerIdsByTagId(tag.getId());
        
        Assertions.assertNotNull(workerIds);
        Assertions.assertEquals(2, workerIds.size());
        Assertions.assertTrue(workerIds.containsAll(List.of(workerId1, workerId2)));
    }
}
