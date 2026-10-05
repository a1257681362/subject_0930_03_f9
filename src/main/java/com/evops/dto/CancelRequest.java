package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CancelRequest {

    @NotBlank(message = "作废原因不能为空")
    private String reason;
}
