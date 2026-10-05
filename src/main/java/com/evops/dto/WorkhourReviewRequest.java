package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 负责人审核工时：APPROVE 通过 / RETURN 退回。
 * 退回后版本封存只读，修订只能在该链上补充新版本。
 */
@Data
public class WorkhourReviewRequest {

    /** APPROVE 通过 / RETURN 退回 */
    @NotBlank(message = "审核决定不能为空")
    private String decision;

    /** 审核负责人 */
    @NotBlank(message = "审核人不能为空")
    private String reviewer;

    /** 审核意见（退回时必填原因） */
    private String comment;
}
