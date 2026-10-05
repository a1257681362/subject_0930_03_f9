package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备故障：登记即对受影响排程自动生成改期建议，
 * 但原排程一行不改；故障窗口内禁止新填报。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_equipment_fault")
public class EquipmentFault extends BaseEntity {

    /** 故障单号，FT-000001 起 */
    private String faultNo;

    private Long equipmentId;

    /** 故障时段（预计修复完成时刻为 faultEnd） */
    private LocalDateTime faultStart;

    private LocalDateTime faultEnd;

    private String description;

    private String reporter;

    /** OPEN 故障中 / RESOLVED 已恢复 */
    private String status;

    private LocalDateTime resolvedAt;

    private String resolver;
}
