package com.sjdt.scheduler.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * API统一返回结果封装类
 * @param <T> 返回数据的泛型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)  // 忽略值为 null 的字段，不序列化到 JSON 中
public class Result<T> {

    /**
     * 状态码
     */
    private int code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 当前页码（可选，分页查询时使用）
     */
    @JsonProperty("page")
    private Long page;

    /**
     * 每页大小（可选，分页查询时使用）
     */
    @JsonProperty("page_size")
    private Long pageSize;

    /**
     * 总记录数（可选，分页查询时使用）
     */
    @JsonProperty("total")
    private Long total;

    /**
     * 总页数（可选，分页查询时使用）
     */
    @JsonProperty("total_pages")
    private Long totalPages;

    /**
     * 数据对象
     */
    private T data;

    /**
     * 构造一个成功的返回结果
     * @param data 返回的数据
     * @param <T>  泛型
     * @return Result
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    /**
     * 构造一个成功的返回结果（无数据）
     * @return Result
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 构造一个带分页信息的成功返回结果
     * @param data 返回的数据列表
     * @param page 当前页码
     * @param pageSize 每页大小
     * @param total 总记录数
     * @param totalPages 总页数
     * @param <T> 泛型
     * @return Result
     */
    public static <T> Result<T> successWithPage(T data, long page, long pageSize, long total, long totalPages) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setTotalPages(totalPages);
        result.setData(data);
        return result;
    }

    /**
     * 构造一个失败的返回结果
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     泛型
     * @return Result
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 根据错误码枚举构造一个失败的返回结果
     * @param errorCodeEnum 错误码枚举
     * @param <T>           泛型
     * @return Result
     */
    public static <T> Result<T> error(ErrorCodeEnum errorCodeEnum) {
        return error(errorCodeEnum.getCode(), errorCodeEnum.getMessage());
    }
}
