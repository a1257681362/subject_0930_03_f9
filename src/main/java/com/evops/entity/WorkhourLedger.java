package com.evops.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工时台账：只追加、不修改、不删除。
 * 填报/审核/退回/修订/故障/故障恢复/建议生成/采纳/忽略全部留痕。
 */
@Data
@TableName("t_workhour_ledger")
public class WorkhourLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long entryId;

    private String chainNo;

    private Long equipmentId;

    private Long faultId;

    private Long suggestionId;

    private String action;

    private String bizNo;

    private String summary;

    private LocalDateTime occurredAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
