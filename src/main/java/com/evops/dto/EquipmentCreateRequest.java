package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 修复设备建档：设备编号唯一。
 */
@Data
public class EquipmentCreateRequest {

    @NotBlank(message = "设备编号不能为空")
    private String equipmentNo;

    @NotBlank(message = "设备名称不能为空")
    private String name;

    /** 设备类别（如 显微设备/清洗设备/烘干设备） */
    private String equipType;
}
