package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.domain.dto.CallbackJobRunQueryDTO;
import com.sjdt.scheduler.domain.po.CallbackJobRun;
import com.sjdt.scheduler.domain.vo.CallbackJobRunVO;

import java.util.List;
import java.util.Map;

/**
 * 回调执行记录Service接口
 */
public interface ICallbackJobRunService extends IService<CallbackJobRun> {

    /**
     * 根据ID查询执行记录详情（包含关联信息）
     *
     * @param id 执行记录ID
     * @return 执行记录视图对象
     */
    CallbackJobRunVO getDetailById(String id);

    /**
     * 根据任务ID查询执行记录列表
     *
     * @param taskId 任务ID
     * @return 执行记录列表
     */
    List<CallbackJobRunVO> getByTaskId(String taskId);

    /**
     * 根据配置ID查询执行记录列表
     *
     * @param configId 配置ID
     * @return 执行记录列表
     */
    List<CallbackJobRunVO> getByConfigId(String configId);


    /**
     * 根据request_id查询执行记录
     *
     * @param requestId 请求ID
     * @return 执行记录
     */
    CallbackJobRunVO getByRequestId(String requestId);

    /**
     * 分页查询执行记录
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    Page<CallbackJobRunVO> pageQuery(CallbackJobRunQueryDTO queryDTO);

    /**
     * 获取配置回调统计信息
     *
     * @param configId 配置ID
     * @return 统计信息Map
     */
    Map<String, Object> getConfigStatistics(String configId);

    /**
     * 获取全局回调统计信息
     *
     * @return 统计信息Map
     */
    Map<String, Object> getGlobalStatistics();

    /**
     * 手动重试回调
     *
     * @param runId 执行记录ID
     * @return 是否成功
     */
    boolean retry(String runId);

    /**
     * 删除执行记录
     *
     * @param id 执行记录ID
     * @return 是否成功
     */
    boolean deleteById(String id);

    /**
     * 根据任务ID删除执行记录
     *
     * @param taskId 任务ID
     * @return 删除的记录数
     */
    int deleteByTaskId(String taskId);

    /**
     * 查询测试回调记录
     *
     * @param taskId 任务ID（可选）
     * @param configId 配置ID（可选）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 测试回调分页记录
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.sjdt.scheduler.domain.vo.CallbackJobRunVO> getTestRuns(
        String taskId, String configId, int pageNum, int pageSize);
}
