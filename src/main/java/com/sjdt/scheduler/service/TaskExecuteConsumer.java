package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

/**
 * RocketMQ任务执行消费者
 * 负责接收Worker发送的任务执行请求
 */
@Service
@RocketMQMessageListener(topic = "TASK_EXECUTE", consumerGroup = "WORKER_EXECUTOR_GROUP", selectorExpression = "PUBLIC_TASK || TARGET_TASK")
public class TaskExecuteConsumer implements RocketMQListener<TaskExecuteMessage> {

    /**
     * 接收并处理任务执行消息
     *
     * @param taskMessage 任务执行消息
     */
    @Override
    public void onMessage(TaskExecuteMessage taskMessage) {
        System.out.println("接收到任务执行消息：");
        System.out.println("任务ID：" + taskMessage.getTaskId());
        System.out.println("任务名称：" + taskMessage.getTaskName());
        System.out.println("任务类型：" + taskMessage.getTaskType());
        System.out.println("脚本类型：" + taskMessage.getScriptType());
        System.out.println("目标Worker：" + taskMessage.getTargetWorkerId());
        System.out.println("超时时间：" + taskMessage.getTimeoutSeconds() + "秒");
        System.out.println("脚本信息：" + taskMessage.getScript().getName());
        System.out.println("脚本路径：" + taskMessage.getScript().getPath());
        System.out.println("脚本标签：" + taskMessage.getScript().getTags());
        System.out.println("任务描述：" + taskMessage.getDescription());
        
        // TODO: 实际业务逻辑处理
        // 1. 验证Worker身份
        // 2. 检查Worker资源状态
        // 3. 执行任务
        // 4. 发送任务结果
    }
}