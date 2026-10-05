package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 文物上展厅陈展：材质取自文物档案，按点位归组采样 */
@Data
public class HallRelicRequest {

    @NotNull(message = "文物id不能为空")
    private Long relicId;

    @NotBlank(message = "巡检点位编码不能为空")
    private String pointCode;

    /** 陈展编号 */
    private String displayNo;
}
