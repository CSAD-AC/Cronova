package com.sjdt.scheduler.controller;

import com.sjdt.scheduler.common.ErrorCodeEnum;
import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.dto.WorkerUpdateDTO;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.domain.po.WorkerTag;
import com.sjdt.scheduler.domain.vo.WorkerVO;
import com.sjdt.scheduler.service.IWorkerService;
import com.sjdt.scheduler.service.IWorkerTagService;
import com.sjdt.scheduler.service.ITagService;
import com.sjdt.scheduler.domain.po.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Worker 前端控制器
 * 提供前端管理 Worker 的接口
 */
@RestController
@RequestMapping("/api/frontend/worker")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Worker管理", description = "Worker执行器的前端管理接口")
@Slf4j
public class WorkerFrontController {

    private final IWorkerService workerService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final IWorkerTagService workerTagService;
    private final ITagService tagService;

    public WorkerFrontController(IWorkerService workerService, RedisTemplate<String, Object> redisTemplate, 
                                IWorkerTagService workerTagService, ITagService tagService) {
        this.workerService = workerService;
        this.redisTemplate = redisTemplate;
        this.workerTagService = workerTagService;
        this.tagService = tagService;
    }

    /**
     * 获取所有 Worker 信息
     * @return Worker 列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取Worker列表", description = "获取所有Worker的详细信息，实时从Redis合并最新状态")
    public Result<List<WorkerVO>> getWorkerList() {
        log.info("开始查询所有Worker列表，并同步实时状态");
        
        // 获取所有Worker标签映射
        Map<String, Set<Tag>> workerTagsMap = getAllWorkerTagsMap();
        
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
                    
                    // 3. 设置标签信息
                    workerVO.setTags(workerTagsMap.get(worker.getId()));
                    
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

    /**
     * 获取指定 ID 的 Worker 详情信息
     * @param id Worker ID
     * @return Worker 详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取Worker详情", description = "根据Worker ID获取指定Worker的详细信息，包括标签，实时从Redis合并最新状态")
    public Result<WorkerVO> getWorkerDetail(@PathVariable String id) {
        log.info("开始查询Worker详情，WorkerID: {}", id);
        
        // 从数据库获取基本信息
        Worker worker = workerService.getById(id);
        if (worker == null) {
            log.warn("Worker不存在，WorkerID: {}", id);
            return Result.error(ErrorCodeEnum.WORKER_NOT_FOUND);
        }
        
        WorkerVO workerVO = new WorkerVO();
        BeanUtils.copyProperties(worker, workerVO);
        
        // 从 Redis 合并实时状态
        String heartbeatKey = RedisKey.getWorkerHeartbeatKey(id);
        String infoKey = RedisKey.getWorkerInfoKey(id);
        
        // 1. 实时在线状态
        boolean isAlive = redisTemplate.hasKey(heartbeatKey);
        workerVO.setStatus(isAlive ? WorkerStatus.online.name() : WorkerStatus.offline.name());
        
        // 2. 实时负载信息
        Map<Object, Object> info = redisTemplate.opsForHash().entries(infoKey);
        if (!info.isEmpty()) {
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
                    log.warn("解析Redis中的心跳时间失败 | WorkerID: {}", id);
                }
            }
        }
        
        // 3. 获取Worker的标签信息
        try {
            Result<List<Integer>> tagIdsResult = workerTagService.getTagIdsByWorkerId(id);
            if (tagIdsResult != null && tagIdsResult.getData() != null) {
                List<Integer> tagIds = tagIdsResult.getData();
                if (!tagIds.isEmpty()) {
                    Set<Tag> tags = new HashSet<>();
                    for (Integer tagId : tagIds) {
                        Result<Tag> tagResult = tagService.getTagById(tagId);
                        if (tagResult != null && tagResult.getData() != null) {
                            tags.add(tagResult.getData());
                        }
                    }
                    workerVO.setTags(tags);
                }
            }
            log.info("成功获取Worker标签信息，WorkerID: {}, 标签数量: {}", id, 
                    workerVO.getTags() != null ? workerVO.getTags().size() : 0);
        } catch (Exception e) {
            log.warn("获取Worker标签信息失败，WorkerID: {}, 错误: {}", id, e.getMessage());
            // 标签获取失败不影响主体功能
        }
        
        log.info("查询Worker详情完成，WorkerID: {}, 状态: {}", id, workerVO.getStatus());
        return Result.success(workerVO);
    }
    
    /**
     * 获取所有Worker的标签映射
     * @return Map<WorkerId, Set<Tag>>
     */
    private Map<String, Set<Tag>> getAllWorkerTagsMap() {
        try {
            // 获取所有Worker标签关联
            List<WorkerTag> workerTags = workerTagService.list();
            if (workerTags == null || workerTags.isEmpty()) {
                return Map.of();
            }
            
            // 获取所有标签ID
            Set<Integer> tagIds = workerTags.stream()
                    .map(WorkerTag::getTagId)
                    .collect(Collectors.toSet());
            
            // 批量获取标签信息
            Map<Integer, Tag> tagMap = tagService.listByIds(tagIds).stream()
                    .collect(Collectors.toMap(Tag::getId, t -> t));
            
            // 构建WorkerID到标签的映射
            Map<String, Set<Tag>> workerTagsMap = workerTags.stream()
                    .collect(Collectors.groupingBy(
                            WorkerTag::getWorkerId,
                            Collectors.mapping(
                                    wt -> tagMap.get(wt.getTagId()),
                                    Collectors.toSet()
                            )
                    ));
            
            // 移除null值
            workerTagsMap.values().forEach(tags -> tags.removeIf(t -> t == null));
            
            return workerTagsMap;
        } catch (Exception e) {
            log.warn("获取所有Worker标签映射失败，错误: {}", e.getMessage());
            return Map.of();
        }
    }

}