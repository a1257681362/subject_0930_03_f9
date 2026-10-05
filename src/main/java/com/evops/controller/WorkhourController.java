package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.EquipmentCreateRequest;
import com.evops.dto.EquipmentFaultRequest;
import com.evops.dto.SuggestionHandleRequest;
import com.evops.dto.WorkhourReviewRequest;
import com.evops.dto.WorkhourSubmitRequest;
import com.evops.entity.EquipmentFault;
import com.evops.entity.RepairEquipment;
import com.evops.entity.RescheduleSuggestion;
import com.evops.entity.WorkhourEntry;
import com.evops.entity.WorkhourLedger;
import com.evops.service.WorkhourService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 修复工时与设备排程（museum-9）：
 * 修复师按工单填报起止时间/工序/设备；设备同一时段不能被两个工单占用；
 * 跨天作业按自然日拆分统计；工时提交后由负责人审核，退回只能补充新版本；
 * 设备故障自动生成改期建议但不悄悄改变原排程；
 * 排程的页面 / CSV 导出 / 后台 JSON 共用同一查询口径。
 */
@RestController
public class WorkhourController {

    static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkhourService workhourService;

    public WorkhourController(WorkhourService workhourService) {
        this.workhourService = workhourService;
    }

    // ---- 设备档案 ----

    @PostMapping("/api/equipment")
    public ApiResponse<RepairEquipment> createEquipment(@Valid @RequestBody EquipmentCreateRequest request) {
        return ApiResponse.ok(workhourService.createEquipment(request));
    }

    @GetMapping("/api/equipment")
    public ApiResponse<List<RepairEquipment>> listEquipment(@RequestParam(required = false) String status) {
        return ApiResponse.ok(workhourService.listEquipment(status));
    }

    // ---- 工时填报 / 审核 / 退回修订 ----

    /** 修复师按工单填报工时（起止时间/工序/设备），提交后待负责人审核 */
    @PostMapping("/api/workhour-entries")
    public ApiResponse<WorkhourEntry> submit(@Valid @RequestBody WorkhourSubmitRequest request) {
        return ApiResponse.ok(workhourService.submit(request));
    }

    @GetMapping("/api/workhour-entries")
    public ApiResponse<List<WorkhourEntry>> listEntries(@RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String chainNo,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(workhourService.listEntries(projectId, chainNo, status));
    }

    /** 负责人审核：APPROVE 通过 / RETURN 退回（退回封存只读） */
    @PostMapping("/api/workhour-entries/{id}/review")
    public ApiResponse<WorkhourEntry> review(@PathVariable Long id,
                                             @Valid @RequestBody WorkhourReviewRequest request) {
        return ApiResponse.ok(workhourService.review(id, request));
    }

    /** 退回后修订：只能在该链上补充新版本，旧版本永不改写 */
    @PostMapping("/api/workhour-entries/{id}/revise")
    public ApiResponse<WorkhourEntry> revise(@PathVariable Long id,
                                             @Valid @RequestBody WorkhourSubmitRequest request) {
        return ApiResponse.ok(workhourService.revise(id, request));
    }

    /** 工时统计：仅审核通过工时，跨天作业按自然日拆分 */
    @GetMapping("/api/workhour-entries/stats")
    public ApiResponse<List<Map<String, Object>>> stats(@RequestParam String from,
                                                        @RequestParam String to) {
        return ApiResponse.ok(workhourService.stats(LocalDate.parse(from), LocalDate.parse(to)));
    }

    // ---- 设备故障与改期建议 ----

    /** 登记设备故障：自动生成改期建议（只记录建议，不改原排程） */
    @PostMapping("/api/equipment/{id}/faults")
    public ApiResponse<Map<String, Object>> reportFault(@PathVariable Long id,
                                                        @Valid @RequestBody EquipmentFaultRequest request) {
        return ApiResponse.ok(workhourService.reportFault(id, request));
    }

    @GetMapping("/api/equipment/faults")
    public ApiResponse<List<EquipmentFault>> listFaults(@RequestParam(required = false) Long equipmentId,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(workhourService.listFaults(equipmentId, status));
    }

    /** 故障恢复：设备无其他在途故障时回到在用 */
    @PostMapping("/api/equipment/faults/{id}/resolve")
    public ApiResponse<EquipmentFault> resolveFault(@PathVariable Long id,
                                                    @Valid @RequestBody SuggestionHandleRequest request) {
        return ApiResponse.ok(workhourService.resolveFault(id, request.getHandler()));
    }

    @GetMapping("/api/reschedule-suggestions")
    public ApiResponse<List<RescheduleSuggestion>> listSuggestions(@RequestParam(required = false) Long faultId,
                                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(workhourService.listSuggestions(faultId, status));
    }

    /** 采纳建议：显式生成该链待审核新版本，原版本保留不改 */
    @PostMapping("/api/reschedule-suggestions/{id}/adopt")
    public ApiResponse<WorkhourEntry> adoptSuggestion(@PathVariable Long id,
                                                      @Valid @RequestBody SuggestionHandleRequest request) {
        return ApiResponse.ok(workhourService.adoptSuggestion(id, request.getHandler()));
    }

    /** 忽略建议：原排程维持不变 */
    @PostMapping("/api/reschedule-suggestions/{id}/ignore")
    public ApiResponse<RescheduleSuggestion> ignoreSuggestion(@PathVariable Long id,
                                                              @Valid @RequestBody SuggestionHandleRequest request) {
        return ApiResponse.ok(workhourService.ignoreSuggestion(id, request.getHandler()));
    }

    // ---- 修复工时与设备占用排程（页面 / 导出 / 后台同口径） ----

    /** 后台 JSON 口径 */
    @GetMapping("/api/workhour-schedule")
    public ApiResponse<List<Map<String, Object>>> schedule(@RequestParam(required = false) String from,
                                                           @RequestParam(required = false) String to,
                                                           @RequestParam(required = false) Long equipmentId,
                                                           @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(workhourService.schedule(parseDateTime(from), parseDateTime(to),
                equipmentId, projectId));
    }

    /** CSV 导出口径：与页面、后台 JSON 共用 WorkhourService.schedule 同一查询 */
    @GetMapping("/api/workhour-schedule/export")
    public ResponseEntity<byte[]> exportSchedule(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to,
                                                 @RequestParam(required = false) Long equipmentId,
                                                 @RequestParam(required = false) Long projectId) {
        List<Map<String, Object>> rows = workhourService.schedule(parseDateTime(from),
                parseDateTime(to), equipmentId, projectId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("填报链,版本,工单号,文物编号,文物名称,修复师,工序,设备编号,设备名称,开始时间,结束时间,状态,来源\n");
        for (Map<String, Object> row : rows) {
            csv.append(csvCell(row.get("chainNo"))).append(',')
                    .append(csvCell(row.get("versionNo"))).append(',')
                    .append(csvCell(row.get("projectNo"))).append(',')
                    .append(csvCell(row.get("relicNo"))).append(',')
                    .append(csvCell(row.get("relicName"))).append(',')
                    .append(csvCell(row.get("restorer"))).append(',')
                    .append(csvCell(row.get("processStep"))).append(',')
                    .append(csvCell(row.get("equipmentNo"))).append(',')
                    .append(csvCell(row.get("equipmentName"))).append(',')
                    .append(csvCell(format(row.get("startTime")))).append(',')
                    .append(csvCell(format(row.get("endTime")))).append(',')
                    .append(csvCell(row.get("status"))).append(',')
                    .append(csvCell(row.get("source"))).append('\n');
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", "workhour-schedule.csv");
        return ResponseEntity.ok().headers(headers)
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/api/workhour-ledgers")
    public ApiResponse<List<WorkhourLedger>> listLedger(@RequestParam(required = false) Long equipmentId,
                                                        @RequestParam(required = false) String chainNo) {
        return ApiResponse.ok(workhourService.listLedger(equipmentId, chainNo));
    }

    /** 解析 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd（当日 00:00:00） */
    static LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).atStartOfDay();
        }
        return LocalDateTime.parse(trimmed, DATETIME);
    }

    private static String format(Object time) {
        return time == null ? "" : ((LocalDateTime) time).format(DATETIME);
    }

    private static String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
