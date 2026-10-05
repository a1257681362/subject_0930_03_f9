package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修复设备档案：修复工时按设备排程，同一时段不能被两个工单占用。
 * 故障/停用只置状态，不删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_repair_equipment")
public class RepairEquipment extends BaseEntity {

    /** 设备编号，全局唯一 */
    private String equipmentNo;

    private String name;

    /** 设备类别（如 显微设备/清洗设备/烘干设备） */
    private String equipType;

    /** ACTIVE 在用 / FAULTY 故障停用 / DISABLED 停用 */
    private String status;
}
