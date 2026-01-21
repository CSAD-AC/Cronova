package com.sjdt.scheduler.task;

import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.mapper.WorkerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Worker心跳检查定时任务
 * 定期检查Redis中的Worker存活键，若不存在则在数据库中标记为离线
 */
@Slf4j
@Component
public class WorkerHeartbeatCheckTask {

    @Autowired
    private WorkerMapper workerMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 每30秒检查一次Worker状态
     */
    @Scheduled(fixedRate = 30000)
    public void checkWorkerHeartbeat() {
        log.debug("开始执行Worker存活状态检查（Redis模式）");
        
        try {
            // 仅查询数据库中在线的Worker，降低数据库负载
            List<Worker> onlineWorkers = workerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Worker>()
                    .eq(Worker::getStatus, WorkerStatus.online)
            );

            for (Worker worker : onlineWorkers) {
                String heartbeatKey = RedisKey.getWorkerHeartbeatKey(worker.getId());
                Boolean isAlive = redisTemplate.hasKey(heartbeatKey);

                if (Boolean.FALSE.equals(isAlive)) {
                    handleWorkerOffline(worker);
                } else {
                    // 若在线，尝试从 Redis 同步负载信息到数据库
                    syncWorkerInfoFromRedis(worker);
                }
            }
        } catch (Exception e) {
            log.error("执行Worker心跳检查任务异常", e);
        }
    }

    /**
     * 处理Worker离线逻辑
     */
    private void handleWorkerOffline(Worker worker) {
        log.warn("检测到Worker心跳丢失，开始同步最后已知状态并标记为离线 | WorkerID: {}, Host: {}", 
            worker.getId(), worker.getHost());
        
        // 尝试从 Redis 获取最后一次心跳时间和负载信息
        String infoKey = RedisKey.getWorkerInfoKey(worker.getId());
        Map<Object, Object> info = redisTemplate.opsForHash().entries(infoKey);
        
        if (info != null && !info.isEmpty()) {
            StringBuilder syncDetail = new StringBuilder();
            if (info.containsKey("lastHeartbeat")) {
                try {
                    String lastHeartbeatStr = (String) info.get("lastHeartbeat");
                    worker.setLastHeartbeat(LocalDateTime.parse(lastHeartbeatStr));
                    syncDetail.append("lastHeartbeat=").append(lastHeartbeatStr).append(", ");
                } catch (Exception e) {
                    log.error("解析Redis最后心跳时间失败 | WorkerID: {}, Value: {}", worker.getId(), info.get("lastHeartbeat"));
                    worker.setLastHeartbeat(LocalDateTime.now());
                }
            }
            
            // 同步最后已知的负载信息
            if (info.containsKey("cpuUsage")) {
                worker.setCpuUsage((Double) info.get("cpuUsage"));
                syncDetail.append("cpuUsage=").append(worker.getCpuUsage()).append("%, ");
            }
            if (info.containsKey("memUsage")) {
                worker.setMemUsage((Double) info.get("memUsage"));
                syncDetail.append("memUsage=").append(worker.getMemUsage()).append("%, ");
            }
            if (info.containsKey("runningTasks")) {
                worker.setRunningTasks((Integer) info.get("runningTasks"));
                syncDetail.append("runningTasks=").append(worker.getRunningTasks());
            }
            
            log.info("从Redis同步最后状态成功 | WorkerID: {}, 详情: [{}]", worker.getId(), syncDetail.toString());
        } else {
            log.info("Redis中未找到Worker负载信息，仅标记离线 | WorkerID: {}", worker.getId());
        }
        
        worker.setStatus(WorkerStatus.offline);
        worker.setUpdateTime(LocalDateTime.now());
        workerMapper.updateById(worker);
        
        // 清理 Redis 中的负载信息 Hash
        redisTemplate.delete(infoKey);
        log.info("Worker离线流程处理完成，已清理Redis负载缓存 | WorkerID: {}", worker.getId());
    }

    /**
     * 从 Redis 同步负载信息到数据库
     */
    private void syncWorkerInfoFromRedis(Worker worker) {
        String infoKey = RedisKey.getWorkerInfoKey(worker.getId());
        Map<Object, Object> info = redisTemplate.opsForHash().entries(infoKey);
        
        if (info != null && !info.isEmpty()) {
            boolean changed = false;
            if (info.containsKey("cpuUsage")) {
                worker.setCpuUsage((Double) info.get("cpuUsage"));
                changed = true;
            }
            if (info.containsKey("memUsage")) {
                worker.setMemUsage((Double) info.get("memUsage"));
                changed = true;
            }
            if (info.containsKey("runningTasks")) {
                worker.setRunningTasks((Integer) info.get("runningTasks"));
                changed = true;
            }
            
            if (changed) {
                worker.setUpdateTime(LocalDateTime.now());
                workerMapper.updateById(worker);
                log.debug("同步Worker负载信息到数据库成功，WorkerID: {}", worker.getId());
            }
        }
    }
}