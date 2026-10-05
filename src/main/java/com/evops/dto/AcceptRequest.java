package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AcceptRequest {

    @NotBlank(message = "验收人不能为空")
    private String acceptedBy;

    /** PASS / CONDITIONAL，默认 PASS */
    private String result;

    private String opinion;
}
