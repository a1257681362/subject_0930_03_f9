package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 改期建议处理 / 故障恢复登记：处理人必填。
 */
@Data
public class SuggestionHandleRequest {

    @NotBlank(message = "处理人不能为空")
    private String handler;
}
