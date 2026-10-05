package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.AcceptRequest;
import com.evops.dto.CancelRequest;
import com.evops.dto.ProjectCreateRequest;
import com.evops.dto.StageRequest;
import com.evops.entity.RepairAcceptance;
import com.evops.entity.RepairProject;
import com.evops.entity.RepairStage;
import com.evops.service.RepairService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 修复项目链路：立项 -> 开工 -> 阶段结果 -> 完成 -> 验收；另支持作废。
 */
@RestController
@RequestMapping("/api/repair-projects")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    /** 立项：委托人、修复方案；项目编号重复会被拒绝 */
    @PostMapping
    public ApiResponse<RepairProject> create(@Valid @RequestBody ProjectCreateRequest request) {
        return ApiResponse.ok(repairService.createProject(request));
    }

    /** 开工 */
    @PostMapping("/{id}/start")
    public ApiResponse<RepairProject> start(@PathVariable Long id) {
        return ApiResponse.ok(repairService.startRepair(id));
    }

    /** 登记阶段结果（可多次，序号项目内递增） */
    @PostMapping("/{id}/stages")
    public ApiResponse<RepairStage> recordStage(@PathVariable Long id,
                                                @Valid @RequestBody StageRequest request) {
        return ApiResponse.ok(repairService.recordStage(id, request));
    }

    /** 修复完成，进入待验收 */
    @PostMapping("/{id}/finish")
    public ApiResponse<RepairProject> finish(@PathVariable Long id) {
        return ApiResponse.ok(repairService.finishRepair(id));
    }

    /** 验收：项目闭环 */
    @PostMapping("/{id}/accept")
    public ApiResponse<RepairAcceptance> accept(@PathVariable Long id,
                                                @Valid @RequestBody AcceptRequest request) {
        return ApiResponse.ok(repairService.accept(id, request));
    }

    /** 作废：只改状态 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<RepairProject> cancel(@PathVariable Long id,
                                             @Valid @RequestBody CancelRequest request) {
        return ApiResponse.ok(repairService.cancel(id, request.getReason()));
    }

    /** 项目详情：项目 + 阶段结果 + 验收记录 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        RepairProject project = repairService.requireProject(id);
        List<RepairStage> stages = repairService.listStages(id);
        RepairAcceptance acceptance = repairService.getAcceptance(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("project", project);
        detail.put("stages", stages);
        detail.put("acceptance", acceptance);
        return ApiResponse.ok(detail);
    }
}
