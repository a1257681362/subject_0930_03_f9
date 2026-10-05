package com.evops.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 设备故障登记：故障时段内设备停用，自动生成改期建议但不改原排程。
 */
@Data
public class EquipmentFaultRequest {

    @NotNull(message = "故障开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime faultStart;

    /** 预计修复完成时刻：改期建议以此为平移锚点 */
    @NotNull(message = "故障结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime faultEnd;

    @NotBlank(message = "故障说明不能为空")
    private String description;

    @NotBlank(message = "故障报告人不能为空")
    private String reporter;
}
