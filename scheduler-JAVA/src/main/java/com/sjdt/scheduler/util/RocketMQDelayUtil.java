package com.sjdt.scheduler.util;

/**
 * RocketMQ 延时级别转换工具
 */
public class RocketMQDelayUtil {

    /**
     * RocketMQ 默认延时级别 (开源版本)
     * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    private static final int[] DELAY_SECONDS = {
            1, 5, 10, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 1200, 1800, 3600, 7200
    };

    /**
     * 将秒数转换为最接近的 RocketMQ 延时级别
     *
     * @param seconds 秒数
     * @return 延时级别 (1-18)
     */
    public static int getDelayLevel(int seconds) {
        if (seconds <= DELAY_SECONDS[0]) return 1;
        
        for (int i = 0; i < DELAY_SECONDS.length; i++) {
            if (seconds <= DELAY_SECONDS[i]) {
                return i + 1;
            }
        }
        
        return DELAY_SECONDS.length; // 最大 2h
    }
}
