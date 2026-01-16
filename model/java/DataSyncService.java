package com.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 数据同步服务
 * 处理具体的同步业务逻辑
 */
@Slf4j
@Service
public class DataSyncService {

    private final Random random = new Random();

    /**
     * 执行数据同步
     *
     * @param syncType 同步类型：full/delta
     * @param date     日期
     * @return 同步结果
     */
    public DataSyncCallback.SyncResult sync(String syncType, String date) {
        long startTime = System.currentTimeMillis();

        log.info("执行数据同步, 类型={}, 日期={}", syncType, date);

        try {
            // 模拟业务处理
            Thread.sleep(1000 + random.nextInt(2000));

            int records = 100 + random.nextInt(900);

            DataSyncCallback.SyncResult result = new DataSyncCallback.SyncResult();
            result.setRecordsProcessed(records);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setStatus("success");
            result.setMessage(String.format("成功同步 %d 条记录", records));

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            DataSyncCallback.SyncResult result = new DataSyncCallback.SyncResult();
            result.setRecordsProcessed(0);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setStatus("failed");
            result.setMessage("同步被中断");

            return result;
        }
    }
}
