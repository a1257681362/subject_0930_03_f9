package com.evops.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 修复师按工单填报工时：起止时间、工序、设备。
 * 首次填报开新链（v1）；被退回链的修订复用本请求在 revise 接口追加新版本。
 */
@Data
public class WorkhourSubmitRequest {

    @NotNull(message = "工单不能为空")
    private Long projectId;

    @NotBlank(message = "修复师不能为空")
    private String restorer;

    @NotBlank(message = "工序不能为空")
    private String processStep;

    @NotNull(message = "设备不能为空")
    private Long equipmentId;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
