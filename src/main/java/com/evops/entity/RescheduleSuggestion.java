package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 改期建议：设备故障时自动生成，只记录「建议平移到的时段」。
 *
 * <p>原工时排程不被悄悄改变——采纳需显式操作，且采纳是在原填报链上
 * 追加一个待审核新版本（source=RESCHEDULE），仍需负责人审核后才生效。</p>
 *
 * <p>(fault_id, entry_id) 唯一：同一故障对同一工时版本只生成一条建议。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reschedule_suggestion")
public class RescheduleSuggestion extends BaseEntity {

    private Long faultId;

    /** 受影响的工时版本 */
    private Long entryId;

    private String chainNo;

    private Long equipmentId;

    private Long projectId;

    /** 原排程时段（快照，永不改写） */
    private LocalDateTime origStart;

    private LocalDateTime origEnd;

    /** 建议平移到的时段 */
    private LocalDateTime suggestedStart;

    private LocalDateTime suggestedEnd;

    /** PENDING 待处理 / ADOPTED 已采纳 / IGNORED 已忽略 */
    private String status;

    /** 采纳后生成的新工时版本 */
    private Long adoptedEntryId;

    private String handledBy;

    private LocalDateTime handledAt;
}
