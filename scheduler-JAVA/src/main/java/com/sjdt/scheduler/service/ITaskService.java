package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.domain.dto.TaskCreateDTO;
import com.sjdt.scheduler.domain.dto.TaskUpdateDTO;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.vo.TaskVO;

import java.util.List;

/**
 * Task Service 接口
 * 提供 Task 任务的管理和操作
 */
public interface ITaskService extends IService<Task> {

    /**
     * 创建任务
     *
     * @param taskCreateDTO 任务创建DTO
     * @return 创建的任务ID
     */
    String createTask(TaskCreateDTO taskCreateDTO);

    /**
     * 更新任务及其配置
     *
     * @param taskUpdateDTO 任务更新DTO
     */
    void updateTask(TaskUpdateDTO taskUpdateDTO);

    /**
     * 获取任务详情VO（包含回调配置）
     *
     * @param id 任务ID
     * @return 任务VO
     */
    TaskVO getTaskVO(String id);

    /**
     * 获取任务列表VO
     *
     * @return 任务VO列表
     */
    List<TaskVO> listTaskVOs();

    /**
     * 获取所有活跃的定时任务
     * @return 活跃定时任务列表
     */
    List<Task> listActiveCronTasks();

    /**
     * 根据 Worker ID 更新任务的 target_worker 为 null
     * @param workerId Worker ID
     */
    void updateTargetWorkerToNullByWorkerId(String workerId);

    /**
     * 发布任务
     * 根据任务类型（公共/定向）发送到RocketMQ
     *
     * @param taskId 任务ID
     */
    void publishTask(String taskId);

    /**
     * 发布立即执行的任务
     * 用于一次性任务的立即执行
     *
     * @param task 任务对象
     */
    void publishImmediateTask(Task task);
}