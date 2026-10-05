package com.evops.service;

import com.evops.common.PermissionClosedException;
import com.evops.entity.PermissionPolicy;
import com.evops.mapper.PermissionPolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.evops.constant.MuseumConstants.POLICY_ID;

/**
 * 文物修复权限策略：单行开关。
 *
 * <p>当博物馆文物修复权限被收紧（{@code repairPhotoUploadOpen=false}）时，
 * 影像上传等推进型动作立即停止；放开后恢复。策略翻转不回滚、不删除任何
 * 已确认的影像版本、物理文件或台账事实。</p>
 */
@Service
public class PermissionService {

    private final PermissionPolicyMapper policyMapper;

    public PermissionService(PermissionPolicyMapper policyMapper) {
        this.policyMapper = policyMapper;
    }

    /** 当前是否允许影像上传 */
    public boolean isUploadOpen() {
        return getPolicy().getRepairPhotoUploadOpen();
    }

    /** 收紧时直接拒绝，上传不推进任何写入 */
    public void requireUploadOpen() {
        if (!isUploadOpen()) {
            throw new PermissionClosedException(
                    "博物馆文物修复权限已收紧，影像上传已停止推进；已确认的版本归档继续保留");
        }
    }

    public PermissionPolicy getPolicy() {
        PermissionPolicy policy = policyMapper.selectById(POLICY_ID);
        if (policy == null) {
            // 理论上 schema.sql 已播种 id=1；兜底补一条开放策略，避免空指针
            policy = new PermissionPolicy();
            policy.setId(POLICY_ID);
            policy.setRepairPhotoUploadOpen(true);
            policy.setUpdatedBy("SYSTEM");
            policy.setUpdatedAt(LocalDateTime.now());
            policyMapper.insert(policy);
        }
        return policy;
    }

    /** 收紧 / 放开影像上传，返回最新策略 */
    @Transactional
    public PermissionPolicy updateOpen(boolean open, String operator, String reason) {
        PermissionPolicy policy = getPolicy();
        boolean changed = !Boolean.valueOf(open).equals(policy.getRepairPhotoUploadOpen());
        policy.setRepairPhotoUploadOpen(open);
        policy.setUpdatedBy(operator == null || operator.isEmpty() ? "SYSTEM" : operator);
        policy.setUpdatedAt(LocalDateTime.now());
        policyMapper.updateById(policy);
        // reason 仅用于调用方留痕/日志，不另建历史表
        if (changed) {
            org.slf4j.LoggerFactory.getLogger(PermissionService.class)
                    .info("文物修复影像上传权限已{}，操作人={}，原因={}",
                            open ? "放开" : "收紧", policy.getUpdatedBy(), reason);
        }
        return policy;
    }
}
