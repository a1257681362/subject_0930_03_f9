package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 修复工时填报：修复师按工单填报起止时间、工序与设备。
 *
 * <p>按「填报链 chain_no + 版本号 version_no」管理：首次填报为 v1；
 * 负责人退回（RETURNED）后版本封存只读，修订只能在本链上补充新版本
 * （version_no 递增），旧版本及其审核记录永不改写。</p>
 *
 * <p>status: PENDING 待审核 / APPROVED 审核通过 / RETURNED 已退回。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workhour_entry")
public class WorkhourEntry extends BaseEntity {

    /** 填报链号：同一填报的各版本共享，WH-000001 起 */
    private String chainNo;

    /** 链内版本号，从 1 递增 */
    private Integer versionNo;

    /** 修复工单（项目） */
    private Long projectId;

    /** 修复师 */
    private String restorer;

    /** 工序 */
    private String processStep;

    /** 占用设备 */
    private Long equipmentId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** PENDING / APPROVED / RETURNED */
    private String status;

    /** 审核负责人 */
    private String reviewBy;

    private LocalDateTime reviewedAt;

    /** 审核意见（退回原因） */
    private String reviewComment;

    /** NORMAL 正常填报 / RESCHEDULE 采纳改期建议生成 */
    private String source;

    /** 来源为 RESCHEDULE 时关联的改期建议 */
    private Long suggestionId;
}
