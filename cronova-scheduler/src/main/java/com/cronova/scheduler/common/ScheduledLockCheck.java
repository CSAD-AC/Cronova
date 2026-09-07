package com.cronova.scheduler.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务分布式锁启动期校验器
 * <p>
 * 在所有单例 bean 初始化完成后执行一次，扫描容器中全部 {@code @Scheduled} 方法，
 * 校验其是否配套标注了 {@link SchedulerLocked}：
 * <ul>
 *     <li>有 {@code @Scheduled} 无 {@code @SchedulerLocked}：WARN 提醒——多实例部署时该方法会被
 *         <b>所有实例并发重复执行</b>（正确性虽由数据库 CAS 兜底，但会浪费扫描资源）；</li>
 *     <li>有 {@code @SchedulerLocked} 无 {@code @Scheduled}：注解无效，WARN 提醒（可能加错了方法）。</li>
 * </ul>
 * 目的：把"新写定时任务忘加锁"从运行时隐患变成启动期可见的日志告警，
 * 配合 AOP 切面后新增定时任务只需声明一个注解，不再有"三处同步"（周期/锁TTL/key 常量）的维护负担。
 * <p>
 * 使用方法：无需手动调用，Spring 容器启动完成后自动执行。
 */
@Slf4j
@Component
public class ScheduledLockCheck implements SmartInitializingSingleton {

    /** Bean 工厂：用于遍历全部 bean 定义并按名称解析类型（不触发 bean 实例化） */
    private final ConfigurableListableBeanFactory beanFactory;

    public ScheduledLockCheck(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * 所有单例 bean 初始化完成后回调：逐 bean 校验注解配套情况并汇总输出
     */
    @Override
    public void afterSingletonsInstantiated() {
        int violations = 0;
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition.isLazyInit()) {
                // 懒加载 bean 尚未实例化，getType 也可能触发类型预测开销，跳过
                continue;
            }
            Class<?> beanType;
            try {
                beanType = beanFactory.getType(beanName);
            } catch (Exception e) {
                // FactoryBean 等无法解析类型的 bean 直接忽略
                continue;
            }
            if (beanType == null) {
                continue;
            }
            violations += checkBean(beanName, beanType).size();
        }
        if (violations == 0) {
            log.info("定时任务分布式锁校验通过：所有 @Scheduled 方法均配套 @SchedulerLocked");
        } else {
            log.warn("定时任务分布式锁校验发现 {} 处注解不配套，请检查上方告警", violations);
        }
    }

    /**
     * 校验单个 bean 的方法级注解配套情况
     *
     * @param beanName bean 名称（用于日志定位）
     * @param beanType bean 类型（可能是 CGLIB 代理子类，方法注解仍从父类可读）
     * @return 违规描述列表（空列表表示该 bean 无问题）
     */
    List<String> checkBean(String beanName, Class<?> beanType) {
        List<String> violations = new ArrayList<>();
        for (Method method : beanType.getMethods()) {
            boolean scheduled = AnnotatedElementUtils.findMergedAnnotation(method, Scheduled.class) != null;
            boolean locked = AnnotatedElementUtils.findMergedAnnotation(method, SchedulerLocked.class) != null;

            if (scheduled && !locked) {
                String message = "定时任务未加分布式锁 | bean=%s#%s | 多实例部署时会被所有实例并发重复执行，请补充 @SchedulerLocked"
                        .formatted(beanName, method.getName());
                log.warn("[ScheduledLockCheck] {}", message);
                violations.add(message);
            } else if (!scheduled && locked) {
                String message = "@SchedulerLocked 标注在非 @Scheduled 方法上，注解无效 | bean=%s#%s"
                        .formatted(beanName, method.getName());
                log.warn("[ScheduledLockCheck] {}", message);
                violations.add(message);
            }
        }
        return violations;
    }
}
