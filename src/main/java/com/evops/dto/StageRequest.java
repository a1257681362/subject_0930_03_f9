package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class StageRequest {

    @NotBlank(message = "阶段名称不能为空")
    private String stageName;

    @NotBlank(message = "阶段结果不能为空")
    private String stageResult;

    private String operator;
}
