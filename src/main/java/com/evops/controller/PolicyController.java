package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.PolicyUpdateRequest;
import com.evops.entity.PermissionPolicy;
import com.evops.service.LedgerService;
import com.evops.service.PermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;

import static com.evops.constant.MuseumConstants.ACTION_POLICY_RELAX;
import static com.evops.constant.MuseumConstants.ACTION_POLICY_TIGHTEN;

/**
 * 文物修复权限策略：收紧后影像上传停止推进，放开后恢复。
 * 已确认的影像版本、物理文件与台账事实不因收紧而改动或删除。
 */
@RestController
@RequestMapping("/api/policy/repair-photo-upload")
public class PolicyController {

    /** 台账中全局策略事件使用的占位文物 id（无具体文物归属） */
    private static final long GLOBAL_SCOPE = 0L;

    private final PermissionService permissionService;
    private final LedgerService ledgerService;

    public PolicyController(PermissionService permissionService,
                            LedgerService ledgerService) {
        this.permissionService = permissionService;
        this.ledgerService = ledgerService;
    }

    /** 查看当前策略 */
    @GetMapping
    public ApiResponse<PermissionPolicy> get() {
        return ApiResponse.ok(permissionService.getPolicy());
    }

    /** 收紧（open=false）/ 放开（open=true）影像上传，并写台账留痕 */
    @PutMapping
    public ApiResponse<PermissionPolicy> update(@Valid @RequestBody PolicyUpdateRequest request) {
        boolean wasOpen = permissionService.isUploadOpen();
        PermissionPolicy policy = permissionService.updateOpen(
                request.getOpen(), request.getOperator(), request.getReason());

        if (wasOpen != request.getOpen()) {
            String operator = request.getOperator() == null ? "SYSTEM" : request.getOperator();
            String summary = (request.getOpen() ? "文物修复影像上传权限放开"
                    : "博物馆文物修复权限收紧，影像上传停止推进，已确认版本归档继续保留")
                    + "，操作人：" + operator
                    + (request.getReason() != null && !request.getReason().isEmpty()
                            ? "，原因：" + request.getReason() : "");
            ledgerService.append(GLOBAL_SCOPE, null, null,
                    request.getOpen() ? ACTION_POLICY_RELAX : ACTION_POLICY_TIGHTEN,
                    "POLICY#1", summary, LocalDateTime.now());
        }
        return ApiResponse.ok(policy);
    }
}
