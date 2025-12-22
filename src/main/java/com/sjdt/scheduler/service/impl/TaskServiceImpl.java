package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.service.TaskProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Task Service 实现类
 * 提供 Task 任务的管理和操作的具体实现
 */
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

    /** 任务生产者服务 */
    private final TaskProducerService taskProducerService;

    /** 脚本服务 */
    private final IScriptService scriptService;

    /** 脚本Mapper */
    private final ScriptMapper scriptMapper;

    @Autowired
    public TaskServiceImpl(TaskProducerService taskProducerService, 
                          IScriptService scriptService, 
                          ScriptMapper scriptMapper) {
        this.taskProducerService = taskProducerService;
        this.scriptService = scriptService;
        this.scriptMapper = scriptMapper;
    }

    /**
     * 根据 Worker ID 更新任务的 target_worker 为 null
     * @param workerId Worker ID
     */
    @Override
    public void updateTargetWorkerToNullByWorkerId(String workerId) {
        this.lambdaUpdate()
                .eq(Task::getTargetWorker, workerId)
                .set(Task::getTargetWorker, null)
                .update();
    }

    /**
     * 发布任务
     * 根据任务类型（公共/定向）发送到RocketMQ
     *
     * @param taskId 任务ID
     */
    @Override
    public void publishTask(String taskId) {
        // 查询任务信息
        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found with id: " + taskId);
        }

        // 发布立即执行的任务
        publishImmediateTask(task);
    }

    /**
     * 发布立即执行的任务
     * 用于一次性任务的立即执行
     *
     * @param task 任务对象
     */
    @Override
    public void publishImmediateTask(Task task) {
        // 查询脚本信息
        Script script = scriptMapper.selectById(task.getScriptId());
        if (script == null) {
            throw new IllegalArgumentException("Script not found with id: " + task.getScriptId());
        }

        // 构建脚本信息
        TaskExecuteMessage.ScriptInfo scriptInfo = TaskExecuteMessage.ScriptInfo.builder()
                .id(script.getId())
                .name(script.getName())
                .path(script.getPath())
                .tags(script.getTags())
                .build();

        // 构建任务执行消息
        TaskExecuteMessage message = TaskExecuteMessage.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .description(task.getDescription())
                .scriptType(task.getType().name())
                .script(scriptInfo)
                .taskType(task.getTaskType().name())
                .cronExpr(task.getCronExpr())
                .targetWorkerId(task.getTargetWorker())
                .timeoutSeconds(task.getTimeoutSeconds())
                .build();

        // 发送任务消息
        taskProducerService.sendTask(message);
    }
}