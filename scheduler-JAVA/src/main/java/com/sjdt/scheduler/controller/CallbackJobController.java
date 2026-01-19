package com.sjdt.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.sjdt.scheduler.common.ErrorCodeEnum;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.dto.CallbackJobConfigCreateDTO;
import com.sjdt.scheduler.domain.dto.CallbackJobConfigUpdateDTO;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.vo.CallbackJobConfigVO;
import com.sjdt.scheduler.service.ICallbackJobConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 回调任务配置Controller
 * 提供回调任务配置的CRUD操作
 */
@RestController
@RequestMapping("/api/callback-job-configs")
@SaCheckLogin
@io.swagger.v3.oas.annotations.tags.Tag(name = "回调任务配置管理", description = "回调任务配置的创建、查询、更新和删除")
public class CallbackJobController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    private final ICallbackJobConfigService callbackJobConfigService;

    @Autowired
    public CallbackJobController(ICallbackJobConfigService callbackJobConfigService) {
        this.callbackJobConfigService = callbackJobConfigService;
    }

    /**
     * 创建回调任务配置
     * 注意：这个接口现在主要保留用于独立创建回调配置
     * 通常建议通过TaskCreateDTO一次性创建任务和回调配置
     */
    @PostMapping
    @Operation(summary = "创建回调任务配置", description = "创建一个新的回调任务配置，通常建议通过任务创建接口一并创建")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功", 
                     content = @Content(schema = @Schema(implementation = CallbackJobConfigVO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<CallbackJobConfigVO> create(@Valid @RequestBody CallbackJobConfigCreateDTO dto) {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[{}] 开始创建回调任务配置，任务ID：{}", userId, dto.getTaskId());
        try {
            CallbackJobConfig config = CallbackJobConfig.builder()
                    .taskId(dto.getTaskId())
                    .callbackUrl(dto.getCallbackUrl())
                    .callbackMethod(dto.getCallbackMethod())
                    .secretKey(dto.getSecretKey())
                    .timeoutSeconds(dto.getTimeoutSeconds() != null ? dto.getTimeoutSeconds() : 30)
                    .retryCount(dto.getRetryCount() != null ? dto.getRetryCount() : 0)
                    .retryIntervalSeconds(dto.getRetryIntervalSeconds() != null ? dto.getRetryIntervalSeconds() : 60)
                    .totalCallbackCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .createdBy(userId)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            callbackJobConfigService.save(config);
            logger.info("[{}] 回调任务配置创建成功，配置ID：{}", userId, config.getId());

            CallbackJobConfigVO vo = new CallbackJobConfigVO();
            BeanUtils.copyProperties(config, vo);
            return Result.success(vo);
        } catch (Exception e) {
            logger.error("[{}] 创建回调任务配置失败，任务ID：{}", userId, dto.getTaskId(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 获取所有回调任务配置列表
     */
    @GetMapping
    @Operation(summary = "获取回调任务配置列表", description = "获取所有回调任务配置的列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
                     content = @Content(schema = @Schema(implementation = CallbackJobConfigVO.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<List<CallbackJobConfigVO>> list() {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[{}] 开始获取回调任务配置列表", userId);
        try {
            List<CallbackJobConfig> configs = callbackJobConfigService.list();
            logger.info("[{}] 获取回调任务配置列表成功，共{}个配置", userId, configs.size());
            List<CallbackJobConfigVO> vos = configs.stream()
                    .map(config -> {
                        CallbackJobConfigVO vo = new CallbackJobConfigVO();
                        BeanUtils.copyProperties(config, vo);
                        return vo;
                    })
                    .collect(Collectors.toList());
            return Result.success(vos);
        } catch (Exception e) {
            logger.error("[{}] 获取回调任务配置列表失败", userId, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 根据ID获取回调任务配置详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取回调任务配置详情", description = "根据配置ID获取回调任务配置的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
                     content = @Content(schema = @Schema(implementation = CallbackJobConfigVO.class))),
        @ApiResponse(responseCode = "400", description = "配置不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<CallbackJobConfigVO> getById(@Parameter(description = "配置ID", required = true, example = "config-001") 
                                               @PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[{}] 开始获取回调任务配置详情，配置ID：{}", userId, id);
        try {
            CallbackJobConfig config = callbackJobConfigService.getById(id);
            if (config == null) {
                logger.warn("[{}] 回调任务配置不存在，配置ID：{}", userId, id);
                return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
            }
            logger.info("[{}] 获取回调任务配置详情成功，配置ID：{}", userId, id);
            CallbackJobConfigVO vo = new CallbackJobConfigVO();
            BeanUtils.copyProperties(config, vo);
            return Result.success(vo);
        } catch (Exception e) {
            logger.error("[{}] 获取回调任务配置详情失败，配置ID：{}", userId, id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 根据任务ID获取回调任务配置列表
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "根据任务ID获取回调任务配置", description = "根据任务ID获取该任务关联的所有回调配置")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
                     content = @Content(schema = @Schema(implementation = CallbackJobConfigVO.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<List<CallbackJobConfigVO>> getByTaskId(@Parameter(description = "任务ID", required = true, example = "task-001") 
                                                         @PathVariable String taskId) {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[{}] 开始根据任务ID获取回调任务配置，任务ID：{}", userId, taskId);
        try {
            List<CallbackJobConfig> configs = callbackJobConfigService.getByTaskId(taskId);
            logger.info("[{}] 根据任务ID获取回调任务配置成功，任务ID：{}，共{}个配置", userId, taskId, configs.size());
            List<CallbackJobConfigVO> vos = configs.stream()
                    .map(config -> {
                        CallbackJobConfigVO vo = new CallbackJobConfigVO();
                        BeanUtils.copyProperties(config, vo);
                        return vo;
                    })
                    .collect(Collectors.toList());
            return Result.success(vos);
        } catch (Exception e) {
            logger.error("[{}] 根据任务ID获取回调任务配置失败，任务ID：{}", userId, taskId, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 更新回调任务配置
     */
    @PutMapping
    @Operation(summary = "更新回调任务配置", description = "更新现有回调任务配置的信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功", 
                     content = @Content(schema = @Schema(implementation = CallbackJobConfigVO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或配置不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<CallbackJobConfigVO> update(@Valid @RequestBody CallbackJobConfigUpdateDTO dto) {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[{}] 开始更新回调任务配置，配置ID：{}", userId, dto.getId());
        try {
            CallbackJobConfig existingConfig = callbackJobConfigService.getById(dto.getId());
            if (existingConfig == null) {
                logger.warn("[{}] 回调任务配置不存在，配置ID：{}", userId, dto.getId());
                return Result.error(ErrorCodeEnum.CALLBACK_JOB_NOT_FOUND);
            }

            if (dto.getTaskId() != null) {
                existingConfig.setTaskId(dto.getTaskId());
            }
            if (dto.getCallbackUrl() != null) {
                existingConfig.setCallbackUrl(dto.getCallbackUrl());
            }
            if (dto.getCallbackMethod() != null) {
                existingConfig.setCallbackMethod(dto.getCallbackMethod());
            }
            if (dto.getSecretKey() != null) {
                existingConfig.setSecretKey(dto.getSecretKey());
            }
            if (dto.getTimeoutSeconds() != null) {
                existingConfig.setTimeoutSeconds(dto.getTimeoutSeconds());
            }
            if (dto.getRetryCount() != null) {
                existingConfig.setRetryCount(dto.getRetryCount());
            }
            if (dto.getRetryIntervalSeconds() != null) {
                existingConfig.setRetryIntervalSeconds(dto.getRetryIntervalSeconds());
            }
            existingConfig.setUpdateTime(LocalDateTime.now());
            callbackJobConfigService.updateById(existingConfig);
            logger.info("[{}] 回调任务配置更新成功，配置ID：{}", userId, dto.getId());

            CallbackJobConfigVO vo = new CallbackJobConfigVO();
            BeanUtils.copyProperties(existingConfig, vo);
            return Result.success(vo);
        } catch (Exception e) {
            logger.error("[{}] 更新回调任务配置失败，配置ID：{}", userId, dto.getId(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 删除回调任务配置
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除回调任务配置", description = "根据配置ID删除回调任务配置")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "配置不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Void> delete(@Parameter(description = "配置ID", required = true, example = "config-001") 
                               @PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[{}] 开始删除回调任务配置，配置ID：{}", userId, id);
        try {
            CallbackJobConfig existingConfig = callbackJobConfigService.getById(id);
            if (existingConfig == null) {
                logger.warn("[{}] 回调任务配置不存在，配置ID：{}", userId, id);
                return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
            }

            callbackJobConfigService.removeById(id);
            logger.info("[{}] 回调任务配置删除成功，配置ID：{}", userId, id);
            return Result.success();
        } catch (Exception e) {
            logger.error("[{}] 删除回调任务配置失败，配置ID：{}", userId, id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 根据任务ID删除回调任务配置
     */
    @DeleteMapping("/task/{taskId}")
    @Operation(summary = "根据任务ID删除回调任务配置", description = "根据任务ID删除该任务关联的所有回调配置")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Void> deleteByTaskId(@Parameter(description = "任务ID", required = true, example = "task-001") 
                                       @PathVariable String taskId) {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[{}] 开始根据任务ID删除回调任务配置，任务ID：{}", userId, taskId);
        try {
            int deletedCount = callbackJobConfigService.deleteByTaskId(taskId);
            logger.info("[{}] 根据任务ID删除回调任务配置成功，任务ID：{}，共删除{}个配置", userId, taskId, deletedCount);
            return Result.success();
        } catch (Exception e) {
            logger.error("[{}] 根据任务ID删除回调任务配置失败，任务ID：{}", userId, taskId, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }
}
