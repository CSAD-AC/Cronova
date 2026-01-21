package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.AKSK;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.mapper.AKSKMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.WorkerMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * WorkerServiceImpl测试类
 */
@SpringBootTest
@Transactional
@Rollback
class WorkerServiceImplTest {

    @Autowired
    private IWorkerService workerService;

    @Autowired
    private IAKSKService akskService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private WorkerMapper workerMapper;

    @Autowired
    private AKSKMapper akskMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Test
    void testUpdateHostById() {
        LocalDateTime startTime = LocalDateTime.now();
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("test-host-initial")
                .ip("192.168.1.100")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(10.0)
                .memUsage(20.0)
                .runningTasks(1)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(startTime)
                .updateTime(LocalDateTime.now())
                .build();
        workerService.save(worker);

        // 更新Worker主机名
        String newHost = "test-host-updated";
        workerService.updateHostById(worker.getId(), newHost);

        // 验证更新结果
        Worker updatedWorker = workerService.getById(worker.getId());
        Assertions.assertNotNull(updatedWorker);
        Assertions.assertEquals(newHost, updatedWorker.getHost());
        Assertions.assertEquals(worker.getIp(), updatedWorker.getIp());
        Assertions.assertNotNull(updatedWorker.getUpdateTime());
    }

    @Test
    void testDeleteWorker() {
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("test-host-delete")
                .ip("192.168.1.200")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(15.0)
                .memUsage(25.0)
                .runningTasks(2)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        workerService.save(worker);

        // 为该Worker生成AKSK
        AKSK aksk = akskService.generateAKSK(worker.getId());

        // 创建关联到该Worker的任务
        Task task = Task.builder()
                .name("Test Task Delete Worker")
                .description("Test Task for Delete Worker")
                .executeType(ExecuteType.script)
                .scriptType(com.sjdt.scheduler.domain.enums.ScriptType.java)
                .scriptId("test-script-id")
                .taskType(TaskType.once)
                .enabled(true)
                .timeoutSeconds(300)
                .targetWorker(worker.getId())
                .status(TaskStatus.active)
                .createdBy("test-user")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskService.save(task);

        // 删除Worker
        workerService.deleteWorker(worker.getId());

        // 验证Worker已被删除
        Worker deletedWorker = workerService.getById(worker.getId());
        Assertions.assertNull(deletedWorker);

        // 验证关联的AKSK已被删除
        AKSK deletedAksk = akskService.getById(aksk.getId());
        Assertions.assertNull(deletedAksk);

        // 验证关联的任务的targetWorker已被设置为null
        Task updatedTask = taskService.getById(task.getId());
        Assertions.assertNotNull(updatedTask);
        Assertions.assertNull(updatedTask.getTargetWorker());
    }

    @Test
    void testGetByHostOrIp() {
        // 创建测试Worker
        String host = "test-host-search";
        String ip = "192.168.1.300";
        Worker worker = Worker.builder()
                .host(host)
                .ip(ip)
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(20.0)
                .memUsage(30.0)
                .runningTasks(3)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        workerService.save(worker);

        // 测试根据主机名查询
        Worker foundByHost = workerService.getByHostOrIp(host, null);
        Assertions.assertNotNull(foundByHost);
        Assertions.assertEquals(worker.getId(), foundByHost.getId());

        // 测试根据IP查询
        Worker foundByIp = workerService.getByHostOrIp(null, ip);
        Assertions.assertNotNull(foundByIp);
        Assertions.assertEquals(worker.getId(), foundByIp.getId());

        // 测试根据主机名和IP查询
        Worker foundByBoth = workerService.getByHostOrIp(host, ip);
        Assertions.assertNotNull(foundByBoth);
        Assertions.assertEquals(worker.getId(), foundByBoth.getId());

        // 测试查询不存在的主机名和IP
        Worker notFound = workerService.getByHostOrIp("non-existent-host", "192.168.1.999");
        Assertions.assertNull(notFound);
    }

    @Test
    void testUpdateHeartbeat() {
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("test-host-heartbeat")
                .ip("192.168.1.400")
                .status(WorkerStatus.valueOf(WorkerStatus.offline.getValue()))
                .cpuUsage(25.0)
                .memUsage(35.0)
                .runningTasks(4)
                .lastHeartbeat(LocalDateTime.now().minusHours(1))
                .createTime(LocalDateTime.now().minusDays(1))
                .updateTime(LocalDateTime.now().minusDays(1))
                .build();
        workerService.save(worker);

        // 更新心跳
        workerService.updateHeartbeat(worker.getId());

        // 验证心跳更新结果
        Worker updatedWorker = workerService.getById(worker.getId());
        Assertions.assertNotNull(updatedWorker);
        Assertions.assertEquals(WorkerStatus.online.getValue(), updatedWorker.getStatus().getValue());
        Assertions.assertNotNull(updatedWorker.getLastHeartbeat());
        Assertions.assertTrue(updatedWorker.getLastHeartbeat().isAfter(worker.getLastHeartbeat()));
    }

    @Test
    void testMarkOffline() {
        // 创建测试Worker
        Worker worker = Worker.builder()
                .host("test-host-offline")
                .ip("192.168.1.500")
                .status(WorkerStatus.valueOf(WorkerStatus.online.getValue()))
                .cpuUsage(30.0)
                .memUsage(40.0)
                .runningTasks(5)
                .lastHeartbeat(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        workerService.save(worker);

        // 标记为离线
        workerService.markOffline(worker.getId());

        // 验证离线标记结果
        Worker offlineWorker = workerService.getById(worker.getId());
        Assertions.assertNotNull(offlineWorker);
        Assertions.assertEquals(WorkerStatus.offline.getValue(), offlineWorker.getStatus().getValue());
    }
}