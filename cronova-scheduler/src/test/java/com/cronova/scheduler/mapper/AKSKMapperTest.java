package com.cronova.scheduler.mapper;

import com.cronova.scheduler.domain.po.AKSK;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AKSKMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class AKSKMapperTest {

    @Autowired
    private AKSKMapper akskMapper;

    @Test
    void testInsertAndSelect() {
        // 创建测试AKSK
        AKSK aksk = AKSK.builder()
                .accessKey("AKTEST123456")
                .secretKey("SKTEST123456")
                .workerId("test-worker-123")
                .status("active")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入AKSK
        akskMapper.insert(aksk);

        // 根据ID查询
        AKSK foundAksk = akskMapper.selectById(aksk.getId());

        // 验证查询结果
        Assertions.assertNotNull(foundAksk);
        Assertions.assertEquals(aksk.getAccessKey(), foundAksk.getAccessKey());
        Assertions.assertEquals(aksk.getWorkerId(), foundAksk.getWorkerId());
        Assertions.assertEquals(aksk.getStatus(), foundAksk.getStatus());
    }

    @Test
    void testSelectByAccessKey() {
        // 创建测试AKSK
        AKSK aksk = AKSK.builder()
                .accessKey("AKTEST789012")
                .secretKey("SKTEST789012")
                .workerId("test-worker-456")
                .status("active")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入AKSK
        akskMapper.insert(aksk);

        // 根据Access Key查询
        AKSK foundAksk = akskMapper.selectByAccessKey(aksk.getAccessKey());

        // 验证查询结果
        Assertions.assertNotNull(foundAksk);
        Assertions.assertEquals(aksk.getId(), foundAksk.getId());
        Assertions.assertEquals(aksk.getWorkerId(), foundAksk.getWorkerId());
    }

    @Test
    void testUpdate() {
        // 创建测试AKSK
        AKSK aksk = AKSK.builder()
                .accessKey("AKTEST345678")
                .secretKey("SKTEST345678")
                .workerId("test-worker-789")
                .status("active")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入AKSK
        akskMapper.insert(aksk);

        // 更新AKSK
        aksk.setStatus("inactive");
        aksk.setUpdateTime(LocalDateTime.now());
        akskMapper.updateById(aksk);

        // 查询更新后的AKSK
        AKSK updatedAksk = akskMapper.selectById(aksk.getId());

        // 验证更新结果
        Assertions.assertNotNull(updatedAksk);
        Assertions.assertEquals("inactive", updatedAksk.getStatus());
    }

    @Test
    void testDelete() {
        // 创建测试AKSK
        AKSK aksk = AKSK.builder()
                .accessKey("AKTEST901234")
                .secretKey("SKTEST901234")
                .workerId("test-worker-012")
                .status("active")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入AKSK
        akskMapper.insert(aksk);

        // 删除AKSK
        int result = akskMapper.deleteById(aksk.getId());

        // 验证删除结果
        Assertions.assertEquals(1, result);

        // 验证AKSK已被删除
        AKSK deletedAksk = akskMapper.selectById(aksk.getId());
        Assertions.assertNull(deletedAksk);
    }
}