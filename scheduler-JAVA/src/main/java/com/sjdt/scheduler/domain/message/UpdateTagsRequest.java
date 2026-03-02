package com.sjdt.scheduler.domain.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Worker标签更新请求DTO
 */
@Data
@Schema(description = "Worker标签更新请求DTO")
public class UpdateTagsRequest {

    @Schema(description = "标签列表", example = "[\"V1\", \"FastAPI\"]")
    private List<String> tags;

}
