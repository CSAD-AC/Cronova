package com.sjdt.scheduler.controller;

import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.dto.WorkerUpdateDTO;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.vo.WorkerVO;
import com.sjdt.scheduler.service.IWorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Worker 前端控制器
 * 提供前端管理 Worker 的接口
 */
@RestController
@RequestMapping("/api/frontend/worker")
@Tag(name = "Worker管理", description = "Worker执行器的前端管理接口")
@Slf4j
public class WorkerFrontController {

    private final IWorkerService workerService;
    private final RedisTemplate<String, Object> redisTemplate;

    public WorkerFrontController(IWorkerService workerService, RedisTemplate<String, Object> redisTemplate) {
        this.workerService = workerService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取所有 Worker 信息
     * @return Worker 列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取Worker列表", description = "获取所有Worker的详细信息，实时从Redis合并最新状态")
    public Result<List<WorkerVO>> getWorkerList() {
        log.info("开始查询所有Worker列表，并同步实时状态");
        List<WorkerVO> workerVOs = workerService.list()
                .stream()
                .map(worker -> {
                    WorkerVO workerVO = new WorkerVO();
                    BeanUtils.copyProperties(worker, workerVO);
                    
                    // 从 Redis 合并实时状态
                    String heartbeatKey = RedisKey.getWorkerHeartbeatKey(worker.getId());
                    String infoKey = RedisKey.getWorkerInfoKey(worker.getId());
                    
                    // 1. 实时在线状态
                    boolean isAlive = Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey));
                    workerVO.setStatus(isAlive ? WorkerStatus.online.name() : WorkerStatus.offline.name());
                    
                    // 2. 实时负载信息
                    Map<Object, Object> info = redisTemplate.opsForHash().entries(infoKey);
                    if (info != null && !info.isEmpty()) {
                        if (info.containsKey("cpuUsage")) {
                            workerVO.setCpuUsage((Double) info.get("cpuUsage"));
                        }
                        if (info.containsKey("memUsage")) {
                            workerVO.setMemUsage((Double) info.get("memUsage"));
                        }
                        if (info.containsKey("runningTasks")) {
                            workerVO.setRunningTasks((Integer) info.get("runningTasks"));
                        }
                        if (info.containsKey("lastHeartbeat")) {
                            try {
                                String lastHeartbeatStr = (String) info.get("lastHeartbeat");
                                workerVO.setLastHeartbeat(LocalDateTime.parse(lastHeartbeatStr));
                            } catch (Exception e) {
                                log.warn("解析Redis中的心跳时间失败 | WorkerID: {}", worker.getId());
                            }
                        }
                    }
                    
                    return workerVO;
                })
                .collect(Collectors.toList());
        log.info("查询Worker列表完成，共{}条记录", workerVOs.size());
        return Result.success(workerVOs);
    }

    /**
     * 更新指定 ID 的 Worker 主机名
     * @param id Worker ID
     * @param updateDTO 更新请求 DTO
     * @return 更新结果
     */
    @PutMapping("/{id}/host")
    @Operation(summary = "更新Worker主机名", description = "根据Worker ID更新指定Worker的主机名")
    public Result<Void> updateWorkerHost(@PathVariable String id, @RequestBody WorkerUpdateDTO updateDTO) {
        log.info("开始更新Worker主机名，WorkerID: {}, 新主机名: {}", id, updateDTO.getHost());
        workerService.updateHostById(id, updateDTO.getHost());
        log.info("Worker主机名更新完成，WorkerID: {}", id);
        return Result.success();
    }

    /**
     * 删除指定 ID 的 Worker
     * @param id Worker ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除Worker", description = "根据Worker ID删除指定Worker，并同步删除关联的AKSK和任务")
    public Result<Void> deleteWorker(@PathVariable String id) {
        log.info("开始删除Worker，WorkerID: {}", id);
        workerService.deleteWorker(id);
        log.info("Worker删除完成，WorkerID: {}", id);
        return Result.success();
    }

}