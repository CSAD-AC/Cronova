package com.cronova.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cronova.scheduler.domain.dto.CallbackJobRunQueryDTO;
import com.cronova.scheduler.domain.po.CallbackJobConfig;
import com.cronova.scheduler.domain.po.CallbackJobRun;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.vo.CallbackJobRunVO;
import com.cronova.scheduler.mapper.CallbackJobConfigMapper;
import com.cronova.scheduler.mapper.CallbackJobRunMapper;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.service.ICallbackJobRunService;
import com.cronova.scheduler.task.TaskCallbackExecutor;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回调执行记录Service实现
 */
@Slf4j
@Service
public class CallbackJobRunServiceImpl extends ServiceImpl<CallbackJobRunMapper, CallbackJobRun>
        implements ICallbackJobRunService {

    @Autowired
    private CallbackJobRunMapper callbackJobRunMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private TaskCallbackExecutor taskCallbackExecutor;

    @Value("${minio.bucket-name}")
    private String BUCKET_NAME;

    @Override
    public CallbackJobRunVO getDetailById(String id) {
        CallbackJobRun run = callbackJobRunMapper.selectById(id);
        if (run == null) {
            return null;
        }
        return convertToVO(run);
    }

    @Override
    public List<CallbackJobRunVO> getByTaskId(String taskId) {
        List<CallbackJobRun> runs = callbackJobRunMapper.selectList(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getTaskId, taskId)
                        .orderByDesc(CallbackJobRun::getCreateTime)
        );
        return runs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CallbackJobRunVO> getByConfigId(String configId) {
        List<CallbackJobRun> runs = callbackJobRunMapper.selectList(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getConfigId, configId)
                        .orderByDesc(CallbackJobRun::getCreateTime)
        );
        return runs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public CallbackJobRunVO getByRequestId(String requestId) {
        CallbackJobRun run = callbackJobRunMapper.selectOne(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getRequestId, requestId)
        );
        return run != null ? convertToVO(run) : null;
    }

    @Override
    public Page<CallbackJobRunVO> pageQuery(CallbackJobRunQueryDTO queryDTO) {
        Page<CallbackJobRun> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        LambdaQueryWrapper<CallbackJobRun> wrapper = buildQueryWrapper(queryDTO);

        String orderBy = queryDTO.getOrderBy();
        boolean isAsc = "asc".equals(queryDTO.getOrderDirection());
        if ("startTime".equals(orderBy)) {
            wrapper.orderBy(true, isAsc, CallbackJobRun::getStartTime);
        } else if ("createTime".equals(orderBy)) {
            wrapper.orderBy(true, isAsc, CallbackJobRun::getCreateTime);
        } else if ("durationMs".equals(orderBy)) {
            wrapper.orderBy(true, isAsc, CallbackJobRun::getDurationMs);
        } else {
            wrapper.orderByDesc(CallbackJobRun::getCreateTime);
        }

        Page<CallbackJobRun> resultPage = callbackJobRunMapper.selectPage(page, wrapper);

        Page<CallbackJobRunVO> voPage = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()));

        return voPage;
    }

    @Override
    public Map<String, Object> getConfigStatistics(String configId) {
        Map<String, Object> stats = new HashMap<>();

        Long total = callbackJobRunMapper.selectCount(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getConfigId, configId)
        );

        Long success = callbackJobRunMapper.selectCount(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getConfigId, configId)
                        .eq(CallbackJobRun::getStatus, "success")
        );

        Long failed = callbackJobRunMapper.selectCount(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getConfigId, configId)
                        .eq(CallbackJobRun::getStatus, "failed")
        );

        Long pending = callbackJobRunMapper.selectCount(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getConfigId, configId)
                        .eq(CallbackJobRun::getStatus, "pending")
        );

        Long running = callbackJobRunMapper.selectCount(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getConfigId, configId)
                        .eq(CallbackJobRun::getStatus, "running")
        );

        stats.put("configId", configId);
        stats.put("totalCount", total);
        stats.put("successCount", success);
        stats.put("failedCount", failed);
        stats.put("pendingCount", pending);
        stats.put("runningCount", running);
        stats.put("successRate", total > 0 ? (double) success / total * 100 : 0.0);

        return stats;
    }

    @Override
    public Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>();

        Long total = callbackJobRunMapper.selectCount(null);

        Long success = callbackJobRunMapper.selectCount(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getStatus, "success")
        );

        Long failed = callbackJobRunMapper.selectCount(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getStatus, "failed")
        );

        stats.put("totalCount", total);
        stats.put("successCount", success);
        stats.put("failedCount", failed);
        stats.put("successRate", total > 0 ? (double) success / total * 100 : 0.0);

        return stats;
    }

    @Override
    public boolean retry(String runId) {
        CallbackJobRun run = callbackJobRunMapper.selectById(runId);
        if (run == null) {
            log.warn("重试失败，执行记录不存在，runId: {}", runId);
            return false;
        }

        CallbackJobConfig config = callbackJobConfigMapper.selectById(run.getConfigId());
        if (config == null) {
            log.warn("重试失败，回调配置不存在，configId: {}", run.getConfigId());
            return false;
        }

        try {
            taskCallbackExecutor.executeSingleRequest(
                    config.getCallbackUrl(),
                    org.springframework.http.HttpMethod.valueOf(config.getCallbackMethod().toUpperCase()),
                    config.getSecretKey(),
                    null
            );

            run.setRetryIndex(run.getRetryIndex() != null ? run.getRetryIndex() + 1 : 1);
            callbackJobRunMapper.updateById(run);

            log.info("手动重试成功，runId: {}", runId);
            return true;
        } catch (Exception e) {
            log.error("手动重试失败，runId: {}", runId, e);
            return false;
        }
    }

    @Override
    public boolean deleteById(String id) {
        int result = callbackJobRunMapper.deleteById(id);
        return result > 0;
    }

    @Override
    public int deleteByTaskId(String taskId) {
        return callbackJobRunMapper.delete(
                new LambdaQueryWrapper<CallbackJobRun>()
                        .eq(CallbackJobRun::getTaskId, taskId)
        );
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<CallbackJobRun> buildQueryWrapper(CallbackJobRunQueryDTO queryDTO) {
        LambdaQueryWrapper<CallbackJobRun> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getTaskId() != null) {
            wrapper.eq(CallbackJobRun::getTaskId, queryDTO.getTaskId());
        }
        if (queryDTO.getConfigId() != null) {
            wrapper.eq(CallbackJobRun::getConfigId, queryDTO.getConfigId());
        }
        if (queryDTO.getRequestId() != null) {
            wrapper.eq(CallbackJobRun::getRequestId, queryDTO.getRequestId());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(CallbackJobRun::getStatus, queryDTO.getStatus());
        }
        if (Boolean.TRUE.equals(queryDTO.getOnlyFailed())) {
            wrapper.eq(CallbackJobRun::getStatus, "failed");
        }

        if (queryDTO.getStartTimeFrom() != null) {
            wrapper.ge(CallbackJobRun::getStartTime, queryDTO.getStartTimeFrom());
        }
        if (queryDTO.getStartTimeTo() != null) {
            wrapper.le(CallbackJobRun::getStartTime, queryDTO.getStartTimeTo());
        }
        if (queryDTO.getCreateTimeFrom() != null) {
            wrapper.ge(CallbackJobRun::getCreateTime, queryDTO.getCreateTimeFrom());
        }
        if (queryDTO.getCreateTimeTo() != null) {
            wrapper.le(CallbackJobRun::getCreateTime, queryDTO.getCreateTimeTo());
        }

        if (queryDTO.getDurationMin() != null) {
            wrapper.ge(CallbackJobRun::getDurationMs, queryDTO.getDurationMin());
        }
        if (queryDTO.getDurationMax() != null) {
            wrapper.le(CallbackJobRun::getDurationMs, queryDTO.getDurationMax());
        }

        return wrapper;
    }

    /**
     * 转换为视图对象
     */
    private CallbackJobRunVO convertToVO(CallbackJobRun run) {
        CallbackJobRunVO vo = new CallbackJobRunVO();
        BeanUtils.copyProperties(run, vo);

        if (run.getTaskId() != null) {
            Task task = taskMapper.selectById(run.getTaskId());
            if (task != null) {
                vo.setTaskName(task.getName());
            }
        }

        if (run.getConfigId() != null) {
            CallbackJobConfig config = callbackJobConfigMapper.selectById(run.getConfigId());
            if (config != null) {
                vo.setConfigName(config.getCallbackUrl());
            }
        }

        return vo;
    }
}
