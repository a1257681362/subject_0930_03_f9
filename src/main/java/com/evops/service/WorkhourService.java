package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.dto.EquipmentCreateRequest;
import com.evops.dto.EquipmentFaultRequest;
import com.evops.dto.WorkhourReviewRequest;
import com.evops.dto.WorkhourSubmitRequest;
import com.evops.entity.EquipmentFault;
import com.evops.entity.Relic;
import com.evops.entity.RepairEquipment;
import com.evops.entity.RepairProject;
import com.evops.entity.RescheduleSuggestion;
import com.evops.entity.WorkhourEntry;
import com.evops.entity.WorkhourLedger;
import com.evops.mapper.EquipmentFaultMapper;
import com.evops.mapper.RelicMapper;
import com.evops.mapper.RepairEquipmentMapper;
import com.evops.mapper.RepairProjectMapper;
import com.evops.mapper.RescheduleSuggestionMapper;
import com.evops.mapper.WorkhourEntryMapper;
import com.evops.mapper.WorkhourLedgerMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.evops.constant.MuseumConstants.*;

/**
 * 修复工时与设备排程（museum-9）。
 *
 * <p>关键规则：</p>
 * <ul>
 *   <li>修复师按工单填报起止时间、工序、设备；填报按「链号+版本号」管理，
 *       提交后由负责人审核（PENDING → APPROVED / RETURNED）。</li>
 *   <li>退回只读：RETURNED 版本封存，修订只能在该链上补充新版本，
 *       旧版本与审核记录永不改写。</li>
 *   <li>设备同一时段不能被两个工单占用：有效占用口径 = 每链最新版本
 *       + 状态 PENDING/APPROVED + 工单未作废 + 文物未撤销；时间窗重叠即拒绝，
 *       设备行锁串行化并发。</li>
 *   <li>跨天作业按自然日拆分统计：统计只取 APPROVED 有效版本，按自然日切片。</li>
 *   <li>设备故障自动生成改期建议（只记录建议时段），原排程一行不改；
 *       采纳建议需显式操作并生成待审核新版本。</li>
 *   <li>修复对象置为无效（工单 CANCELLED / 文物 REVOKED）后，排程的页面、
 *       导出与后台 JSON 共用 {@link #schedule} 同一查询口径。</li>
 * </ul>
 */
@Service
public class WorkhourService {

    /** 有效占用/排程口径中的在途状态 */
    private static final List<String> OCCUPYING_STATUS = Arrays.asList(WH_PENDING, WH_APPROVED);

    private final RepairEquipmentMapper equipmentMapper;
    private final WorkhourEntryMapper entryMapper;
    private final EquipmentFaultMapper faultMapper;
    private final RescheduleSuggestionMapper suggestionMapper;
    private final WorkhourLedgerMapper ledgerMapper;
    private final RepairProjectMapper projectMapper;
    private final RelicMapper relicMapper;

    public WorkhourService(RepairEquipmentMapper equipmentMapper,
                           WorkhourEntryMapper entryMapper,
                           EquipmentFaultMapper faultMapper,
                           RescheduleSuggestionMapper suggestionMapper,
                           WorkhourLedgerMapper ledgerMapper,
                           RepairProjectMapper projectMapper,
                           RelicMapper relicMapper) {
        this.equipmentMapper = equipmentMapper;
        this.entryMapper = entryMapper;
        this.faultMapper = faultMapper;
        this.suggestionMapper = suggestionMapper;
        this.ledgerMapper = ledgerMapper;
        this.projectMapper = projectMapper;
        this.relicMapper = relicMapper;
    }

    // ---- 设备档案 ----

    @Transactional
    public RepairEquipment createEquipment(EquipmentCreateRequest req) {
        RepairEquipment equipment = new RepairEquipment();
        equipment.setEquipmentNo(req.getEquipmentNo());
        equipment.setName(req.getName());
        equipment.setEquipType(req.getEquipType());
        equipment.setStatus(EQUIP_ACTIVE);
        try {
            equipmentMapper.insert(equipment);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("设备编号已存在: " + req.getEquipmentNo());
        }
        appendLedger(null, null, equipment.getId(), null, null,
                ACTION_EQUIPMENT_CREATE, equipment.getEquipmentNo(),
                "设备建档：" + equipment.getName()
                        + (req.getEquipType() == null ? "" : "（" + req.getEquipType() + "）"),
                LocalDateTime.now());
        return equipment;
    }

    public List<RepairEquipment> listEquipment(String status) {
        return equipmentMapper.selectList(new LambdaQueryWrapper<RepairEquipment>()
                .eq(status != null && !status.isEmpty(), RepairEquipment::getStatus, status)
                .orderByAsc(RepairEquipment::getId));
    }

    public RepairEquipment requireEquipment(Long id) {
        RepairEquipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new IllegalArgumentException("设备不存在: id=" + id);
        }
        return equipment;
    }

    // ---- 工时填报 / 审核 / 退回修订 ----

    /**
     * 填报工时：仅 REPAIRING 工单可填报；开新链 v1，状态 PENDING。
     * 设备同时段占用互斥在行锁内复查。
     */
    @Transactional
    public WorkhourEntry submit(WorkhourSubmitRequest req) {
        RepairProject project = requireRepairingProject(req.getProjectId());
        validateWindow(req.getStartTime(), req.getEndTime());
        RepairEquipment equipment = requireEquipment(req.getEquipmentId());

        // 设备行锁：同设备并发填报串行化，锁内复查故障与占用重叠
        equipmentMapper.lockRow(equipment.getId());
        checkEquipmentUsable(equipment, req.getStartTime(), req.getEndTime());
        checkOverlap(equipment, req.getStartTime(), req.getEndTime(), null);

        WorkhourEntry entry = new WorkhourEntry();
        entry.setChainNo(String.format("WH-%06d", entryMapper.nextChainSeq()));
        entry.setVersionNo(1);
        fillEntry(entry, req);
        entry.setStatus(WH_PENDING);
        entry.setSource(WH_SOURCE_NORMAL);
        entryMapper.insert(entry);

        appendLedger(entry.getId(), entry.getChainNo(), equipment.getId(), null, null,
                ACTION_WH_SUBMIT, project.getProjectNo(),
                "工时填报 " + entry.getChainNo() + " v1：工单 " + project.getProjectNo()
                        + "，修复师 " + req.getRestorer() + "，工序 " + req.getProcessStep()
                        + "，设备 " + equipment.getEquipmentNo()
                        + "，时段 " + req.getStartTime() + "~" + req.getEndTime(),
                LocalDateTime.now());
        return entry;
    }

    /**
     * 负责人审核：仅当前链最新版本且 PENDING 可审核。
     * RETURN 后版本封存只读，修订只能补充新版本。
     */
    @Transactional
    public WorkhourEntry review(Long entryId, WorkhourReviewRequest req) {
        WorkhourEntry entry = requireEntry(entryId);
        requireLatestVersion(entry);
        if (!WH_PENDING.equals(entry.getStatus())) {
            throw new IllegalArgumentException("工时版本状态为 " + entry.getStatus()
                    + "，不能重复审核；退回版本已封存，修订请补充新版本");
        }
        if (!WH_DECISION_APPROVE.equals(req.getDecision())
                && !WH_DECISION_RETURN.equals(req.getDecision())) {
            throw new IllegalArgumentException("审核决定只能是 APPROVE 或 RETURN");
        }
        if (WH_DECISION_RETURN.equals(req.getDecision())
                && (req.getComment() == null || req.getComment().isEmpty())) {
            throw new IllegalArgumentException("退回必须填写退回原因");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean approved = WH_DECISION_APPROVE.equals(req.getDecision());
        entry.setStatus(approved ? WH_APPROVED : WH_RETURNED);
        entry.setReviewBy(req.getReviewer());
        entry.setReviewedAt(now);
        entry.setReviewComment(req.getComment());
        entryMapper.updateById(entry);

        appendLedger(entry.getId(), entry.getChainNo(), entry.getEquipmentId(), null, null,
                approved ? ACTION_WH_APPROVE : ACTION_WH_RETURN, entry.getChainNo(),
                "工时审核 " + entry.getChainNo() + " v" + entry.getVersionNo()
                        + "：" + (approved ? "通过" : "退回")
                        + "，负责人 " + req.getReviewer()
                        + (req.getComment() == null ? "" : "，意见：" + req.getComment()), now);
        return entry;
    }

    /**
     * 退回后修订：只能在被退回链的最新版本上补充新版本（旧版本永不改写）。
     * 新版本重新走设备占用校验与负责人审核。
     */
    @Transactional
    public WorkhourEntry revise(Long entryId, WorkhourSubmitRequest req) {
        WorkhourEntry previous = requireEntry(entryId);
        requireLatestVersion(previous);
        if (!WH_RETURNED.equals(previous.getStatus())) {
            throw new IllegalArgumentException("只有被退回的填报才能修订补充新版本，当前状态: "
                    + previous.getStatus());
        }
        if (!previous.getProjectId().equals(req.getProjectId())) {
            throw new IllegalArgumentException("修订版本必须属于原工单");
        }
        RepairProject project = requireRepairingProject(req.getProjectId());
        validateWindow(req.getStartTime(), req.getEndTime());
        RepairEquipment equipment = requireEquipment(req.getEquipmentId());

        equipmentMapper.lockRow(equipment.getId());
        checkEquipmentUsable(equipment, req.getStartTime(), req.getEndTime());
        // 排除本链：本链旧版本（已退回）本就不占口径，这里再显式排除
        checkOverlap(equipment, req.getStartTime(), req.getEndTime(), previous.getChainNo());

        WorkhourEntry entry = new WorkhourEntry();
        entry.setChainNo(previous.getChainNo());
        entry.setVersionNo(previous.getVersionNo() + 1);
        fillEntry(entry, req);
        entry.setStatus(WH_PENDING);
        entry.setSource(WH_SOURCE_NORMAL);
        entryMapper.insert(entry);

        appendLedger(entry.getId(), entry.getChainNo(), equipment.getId(), null, null,
                ACTION_WH_REVISE, project.getProjectNo(),
                "退回修订 " + entry.getChainNo() + " v" + entry.getVersionNo()
                        + "（退回版本 v" + previous.getVersionNo() + " 封存只读）：工单 "
                        + project.getProjectNo() + "，修复师 " + req.getRestorer()
                        + "，时段 " + req.getStartTime() + "~" + req.getEndTime(),
                LocalDateTime.now());
        return entry;
    }

    // ---- 设备故障与改期建议 ----

    /**
     * 登记设备故障：设备置 FAULTY，并对故障窗口内的有效排程自动生成改期建议。
     * 建议只记录「建议平移到的时段」，原排程一行不改。
     */
    @Transactional
    public Map<String, Object> reportFault(Long equipmentId, EquipmentFaultRequest req) {
        RepairEquipment equipment = requireEquipment(equipmentId);
        if (EQUIP_DISABLED.equals(equipment.getStatus())) {
            throw new IllegalArgumentException("设备已停用，不能登记故障: " + equipment.getEquipmentNo());
        }
        validateWindow(req.getFaultStart(), req.getFaultEnd());

        equipmentMapper.lockRow(equipment.getId());

        LocalDateTime now = LocalDateTime.now();
        EquipmentFault fault = new EquipmentFault();
        fault.setFaultNo(String.format("FT-%06d", faultMapper.nextFaultSeq()));
        fault.setEquipmentId(equipment.getId());
        fault.setFaultStart(req.getFaultStart());
        fault.setFaultEnd(req.getFaultEnd());
        fault.setDescription(req.getDescription());
        fault.setReporter(req.getReporter());
        fault.setStatus(FAULT_OPEN);
        faultMapper.insert(fault);

        equipment.setStatus(EQUIP_FAULTY);
        equipmentMapper.updateById(equipment);

        appendLedger(null, null, equipment.getId(), fault.getId(), null,
                ACTION_EQUIP_FAULT, fault.getFaultNo(),
                "设备故障 " + fault.getFaultNo() + "：" + equipment.getEquipmentNo()
                        + "，故障时段 " + req.getFaultStart() + "~" + req.getFaultEnd()
                        + "，报告人 " + req.getReporter() + "，说明：" + req.getDescription(), now);

        // 自动生成改期建议：只追加建议记录，绝不动原排程
        List<RescheduleSuggestion> suggestions = new ArrayList<>();
        for (WorkhourEntry occupied : effectiveEntries(equipment.getId(),
                req.getFaultStart(), req.getFaultEnd(), null)) {
            RescheduleSuggestion suggestion = new RescheduleSuggestion();
            suggestion.setFaultId(fault.getId());
            suggestion.setEntryId(occupied.getId());
            suggestion.setChainNo(occupied.getChainNo());
            suggestion.setEquipmentId(equipment.getId());
            suggestion.setProjectId(occupied.getProjectId());
            suggestion.setOrigStart(occupied.getStartTime());
            suggestion.setOrigEnd(occupied.getEndTime());
            // 建议平移：整体移到预计修复完成时刻之后，保持时长不变
            Duration duration = Duration.between(occupied.getStartTime(), occupied.getEndTime());
            suggestion.setSuggestedStart(req.getFaultEnd());
            suggestion.setSuggestedEnd(req.getFaultEnd().plus(duration));
            suggestion.setStatus(SUGGEST_PENDING);
            try {
                suggestionMapper.insert(suggestion);
            } catch (DuplicateKeyException e) {
                continue; // (fault_id, entry_id) 幂等：同一故障对同一版本只一条建议
            }
            suggestions.add(suggestion);
            appendLedger(occupied.getId(), occupied.getChainNo(), equipment.getId(),
                    fault.getId(), suggestion.getId(),
                    ACTION_SUGGEST_GENERATE, fault.getFaultNo(),
                    "改期建议：填报链 " + occupied.getChainNo() + " v" + occupied.getVersionNo()
                            + " 原时段 " + occupied.getStartTime() + "~" + occupied.getEndTime()
                            + "，建议改期至 " + suggestion.getSuggestedStart()
                            + "~" + suggestion.getSuggestedEnd()
                            + "（原排程不变，待人工处理）", now);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fault", fault);
        result.put("suggestions", suggestions);
        result.put("suggestionCount", suggestions.size());
        return result;
    }

    /**
     * 故障恢复：故障单 RESOLVED；设备无其他在途故障时回到 ACTIVE。
     */
    @Transactional
    public EquipmentFault resolveFault(Long faultId, String resolver) {
        EquipmentFault fault = requireFault(faultId);
        if (!FAULT_OPEN.equals(fault.getStatus())) {
            throw new IllegalArgumentException("故障单已恢复，不能重复操作: " + fault.getFaultNo());
        }
        LocalDateTime now = LocalDateTime.now();
        fault.setStatus(FAULT_RESOLVED);
        fault.setResolvedAt(now);
        fault.setResolver(resolver);
        faultMapper.updateById(fault);

        Long openFaults = faultMapper.selectCount(new LambdaQueryWrapper<EquipmentFault>()
                .eq(EquipmentFault::getEquipmentId, fault.getEquipmentId())
                .eq(EquipmentFault::getStatus, FAULT_OPEN));
        RepairEquipment equipment = requireEquipment(fault.getEquipmentId());
        if ((openFaults == null || openFaults == 0)
                && EQUIP_FAULTY.equals(equipment.getStatus())) {
            equipment.setStatus(EQUIP_ACTIVE);
            equipmentMapper.updateById(equipment);
        }
        appendLedger(null, null, equipment.getId(), fault.getId(), null,
                ACTION_EQUIP_FAULT_RESOLVE, fault.getFaultNo(),
                "故障恢复 " + fault.getFaultNo() + "：" + equipment.getEquipmentNo()
                        + "，恢复登记人 " + resolver, now);
        return fault;
    }

    /**
     * 采纳改期建议：显式操作，在原填报链上追加一个 PENDING 新版本
     * （source=RESCHEDULE，时段取建议值），仍需负责人审核；原版本保留不改。
     */
    @Transactional
    public WorkhourEntry adoptSuggestion(Long suggestionId, String handler) {
        RescheduleSuggestion suggestion = requireSuggestion(suggestionId);
        if (!SUGGEST_PENDING.equals(suggestion.getStatus())) {
            throw new IllegalArgumentException("建议已处理，不能重复操作");
        }
        WorkhourEntry origin = requireEntry(suggestion.getEntryId());
        requireLatestVersion(origin);
        RepairProject project = requireRepairingProject(origin.getProjectId());
        RepairEquipment equipment = requireEquipment(suggestion.getEquipmentId());

        equipmentMapper.lockRow(equipment.getId());
        checkEquipmentUsable(equipment, suggestion.getSuggestedStart(), suggestion.getSuggestedEnd());
        checkOverlap(equipment, suggestion.getSuggestedStart(), suggestion.getSuggestedEnd(),
                origin.getChainNo());

        LocalDateTime now = LocalDateTime.now();
        WorkhourEntry entry = new WorkhourEntry();
        entry.setChainNo(origin.getChainNo());
        entry.setVersionNo(origin.getVersionNo() + 1);
        entry.setProjectId(origin.getProjectId());
        entry.setRestorer(origin.getRestorer());
        entry.setProcessStep(origin.getProcessStep());
        entry.setEquipmentId(origin.getEquipmentId());
        entry.setStartTime(suggestion.getSuggestedStart());
        entry.setEndTime(suggestion.getSuggestedEnd());
        entry.setStatus(WH_PENDING);
        entry.setSource(WH_SOURCE_RESCHEDULE);
        entry.setSuggestionId(suggestion.getId());
        entryMapper.insert(entry);

        suggestion.setStatus(SUGGEST_ADOPTED);
        suggestion.setAdoptedEntryId(entry.getId());
        suggestion.setHandledBy(handler);
        suggestion.setHandledAt(now);
        suggestionMapper.updateById(suggestion);

        appendLedger(entry.getId(), entry.getChainNo(), equipment.getId(),
                suggestion.getFaultId(), suggestion.getId(),
                ACTION_SUGGEST_ADOPT, entry.getChainNo(),
                "采纳改期建议：填报链 " + entry.getChainNo() + " 生成 v" + entry.getVersionNo()
                        + "（待审核），时段 " + entry.getStartTime() + "~" + entry.getEndTime()
                        + "；原版本 v" + origin.getVersionNo() + " 保留不改，处理人 " + handler, now);
        return entry;
    }

    /**
     * 忽略改期建议：显式操作，原排程维持不变。
     */
    @Transactional
    public RescheduleSuggestion ignoreSuggestion(Long suggestionId, String handler) {
        RescheduleSuggestion suggestion = requireSuggestion(suggestionId);
        if (!SUGGEST_PENDING.equals(suggestion.getStatus())) {
            throw new IllegalArgumentException("建议已处理，不能重复操作");
        }
        suggestion.setStatus(SUGGEST_IGNORED);
        suggestion.setHandledBy(handler);
        suggestion.setHandledAt(LocalDateTime.now());
        suggestionMapper.updateById(suggestion);
        appendLedger(suggestion.getEntryId(), suggestion.getChainNo(), suggestion.getEquipmentId(),
                suggestion.getFaultId(), suggestion.getId(),
                ACTION_SUGGEST_IGNORE, suggestion.getChainNo(),
                "忽略改期建议：填报链 " + suggestion.getChainNo() + " 维持原排程 "
                        + suggestion.getOrigStart() + "~" + suggestion.getOrigEnd()
                        + "，处理人 " + handler, suggestion.getHandledAt());
        return suggestion;
    }

    // ---- 排程查询（页面 / 导出 / 后台 JSON 同口径）与统计 ----

    /**
     * 修复工时与设备占用排程：全系统唯一查询口径。
     * 页面（Thymeleaf）、CSV 导出、后台 JSON 一律走本方法：
     * 每链最新版本 + 状态 PENDING/APPROVED + 工单未作废 + 文物未撤销。
     */
    public List<Map<String, Object>> schedule(LocalDateTime from, LocalDateTime to,
                                              Long equipmentId, Long projectId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, RepairEquipment> equipmentCache = new HashMap<>();
        Map<Long, RepairProject> projectCache = new HashMap<>();
        Map<Long, Relic> relicCache = new HashMap<>();
        for (WorkhourEntry entry : effectiveEntries(equipmentId, from, to, projectId)) {
            RepairProject project = projectCache.computeIfAbsent(entry.getProjectId(),
                    projectMapper::selectById);
            Relic relic = relicCache.computeIfAbsent(project.getRelicId(), relicMapper::selectById);
            RepairEquipment equipment = equipmentCache.computeIfAbsent(entry.getEquipmentId(),
                    equipmentMapper::selectById);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entryId", entry.getId());
            row.put("chainNo", entry.getChainNo());
            row.put("versionNo", entry.getVersionNo());
            row.put("projectId", project.getId());
            row.put("projectNo", project.getProjectNo());
            row.put("relicNo", relic == null ? null : relic.getRelicNo());
            row.put("relicName", relic == null ? null : relic.getName());
            row.put("restorer", entry.getRestorer());
            row.put("processStep", entry.getProcessStep());
            row.put("equipmentId", equipment.getId());
            row.put("equipmentNo", equipment.getEquipmentNo());
            row.put("equipmentName", equipment.getName());
            row.put("startTime", entry.getStartTime());
            row.put("endTime", entry.getEndTime());
            row.put("status", entry.getStatus());
            row.put("source", entry.getSource());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 工时统计：仅审核通过（APPROVED）的有效版本，跨天作业按自然日拆分，
     * 按「日期 + 工单 + 修复师 + 工序 + 设备」汇总分钟数。
     */
    public List<Map<String, Object>> stats(LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay();
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        Map<Long, RepairProject> projectCache = new HashMap<>();
        Map<Long, RepairEquipment> equipmentCache = new HashMap<>();
        for (WorkhourEntry entry : effectiveEntries(null, fromTs, toTs, null)) {
            if (!WH_APPROVED.equals(entry.getStatus())) {
                continue; // 统计口径：只算审核通过的工时
            }
            RepairProject project = projectCache.computeIfAbsent(entry.getProjectId(),
                    projectMapper::selectById);
            RepairEquipment equipment = equipmentCache.computeIfAbsent(entry.getEquipmentId(),
                    equipmentMapper::selectById);
            // 跨天拆分：按自然日切片累加
            LocalDateTime cursor = entry.getStartTime().isAfter(fromTs) ? entry.getStartTime() : fromTs;
            LocalDateTime end = entry.getEndTime().isBefore(toTs) ? entry.getEndTime() : toTs;
            while (cursor.isBefore(end)) {
                LocalDate day = cursor.toLocalDate();
                LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
                LocalDateTime sliceEnd = dayEnd.isBefore(end) ? dayEnd : end;
                long minutes = Duration.between(cursor, sliceEnd).toMinutes();
                String key = day + "|" + project.getId() + "|" + entry.getRestorer()
                        + "|" + entry.getProcessStep() + "|" + equipment.getId();
                Map<String, Object> row = grouped.computeIfAbsent(key, k -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("date", day.toString());
                    r.put("projectId", project.getId());
                    r.put("projectNo", project.getProjectNo());
                    r.put("restorer", entry.getRestorer());
                    r.put("processStep", entry.getProcessStep());
                    r.put("equipmentNo", equipment.getEquipmentNo());
                    r.put("minutes", 0L);
                    return r;
                });
                row.put("minutes", (Long) row.get("minutes") + minutes);
                cursor = sliceEnd;
            }
        }
        return new ArrayList<>(grouped.values());
    }

    // ---- 列表查询 ----

    public List<WorkhourEntry> listEntries(Long projectId, String chainNo, String status) {
        return entryMapper.selectList(new LambdaQueryWrapper<WorkhourEntry>()
                .eq(projectId != null, WorkhourEntry::getProjectId, projectId)
                .eq(chainNo != null && !chainNo.isEmpty(), WorkhourEntry::getChainNo, chainNo)
                .eq(status != null && !status.isEmpty(), WorkhourEntry::getStatus, status)
                .orderByAsc(WorkhourEntry::getChainNo)
                .orderByAsc(WorkhourEntry::getVersionNo));
    }

    public List<EquipmentFault> listFaults(Long equipmentId, String status) {
        return faultMapper.selectList(new LambdaQueryWrapper<EquipmentFault>()
                .eq(equipmentId != null, EquipmentFault::getEquipmentId, equipmentId)
                .eq(status != null && !status.isEmpty(), EquipmentFault::getStatus, status)
                .orderByDesc(EquipmentFault::getId));
    }

    public List<RescheduleSuggestion> listSuggestions(Long faultId, String status) {
        return suggestionMapper.selectList(new LambdaQueryWrapper<RescheduleSuggestion>()
                .eq(faultId != null, RescheduleSuggestion::getFaultId, faultId)
                .eq(status != null && !status.isEmpty(), RescheduleSuggestion::getStatus, status)
                .orderByAsc(RescheduleSuggestion::getId));
    }

    public List<WorkhourLedger> listLedger(Long equipmentId, String chainNo) {
        return ledgerMapper.selectList(new LambdaQueryWrapper<WorkhourLedger>()
                .eq(equipmentId != null, WorkhourLedger::getEquipmentId, equipmentId)
                .eq(chainNo != null && !chainNo.isEmpty(), WorkhourLedger::getChainNo, chainNo)
                .orderByAsc(WorkhourLedger::getId));
    }

    public WorkhourEntry requireEntry(Long id) {
        WorkhourEntry entry = entryMapper.selectById(id);
        if (entry == null) {
            throw new IllegalArgumentException("工时填报不存在: id=" + id);
        }
        return entry;
    }

    public EquipmentFault requireFault(Long id) {
        EquipmentFault fault = faultMapper.selectById(id);
        if (fault == null) {
            throw new IllegalArgumentException("故障单不存在: id=" + id);
        }
        return fault;
    }

    public RescheduleSuggestion requireSuggestion(Long id) {
        RescheduleSuggestion suggestion = suggestionMapper.selectById(id);
        if (suggestion == null) {
            throw new IllegalArgumentException("改期建议不存在: id=" + id);
        }
        return suggestion;
    }

    // ---- 内部 ----

    /**
     * 有效占用口径：每链最新版本 + 状态 PENDING/APPROVED
     * + 工单未作废（CANCELLED）+ 文物未撤销（REVOKED）。
     * 占用冲突判定、故障影响面分析、排程查询、工时统计共用这一口径。
     */
    private List<WorkhourEntry> effectiveEntries(Long equipmentId, LocalDateTime from,
                                                 LocalDateTime to, Long projectId) {
        List<WorkhourEntry> all = entryMapper.selectList(new LambdaQueryWrapper<WorkhourEntry>()
                .eq(equipmentId != null, WorkhourEntry::getEquipmentId, equipmentId)
                .eq(projectId != null, WorkhourEntry::getProjectId, projectId)
                .lt(from != null && to != null, WorkhourEntry::getStartTime, to)
                .gt(from != null && to != null, WorkhourEntry::getEndTime, from)
                .orderByAsc(WorkhourEntry::getChainNo)
                .orderByDesc(WorkhourEntry::getVersionNo));
        Map<String, Boolean> chainSeen = new HashMap<>();
        Map<Long, RepairProject> projectCache = new HashMap<>();
        Map<Long, Relic> relicCache = new HashMap<>();
        List<WorkhourEntry> result = new ArrayList<>();
        for (WorkhourEntry entry : all) {
            // 按 chain 升序、version 降序遍历，每链第一条即最新版本
            if (chainSeen.putIfAbsent(entry.getChainNo(), Boolean.TRUE) != null) {
                continue;
            }
            if (!OCCUPYING_STATUS.contains(entry.getStatus())) {
                continue;
            }
            RepairProject project = projectCache.computeIfAbsent(entry.getProjectId(),
                    projectMapper::selectById);
            if (project == null || PROJECT_CANCELLED.equals(project.getStatus())) {
                continue; // 工单已作废：修复对象无效，不占排程
            }
            Relic relic = relicCache.computeIfAbsent(project.getRelicId(), relicMapper::selectById);
            if (relic == null || REVOKED.equals(relic.getStatus())) {
                continue; // 文物已撤销：修复对象无效，不占排程
            }
            result.add(entry);
        }
        result.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        return result;
    }

    private RepairProject requireRepairingProject(Long projectId) {
        RepairProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("修复工单不存在: id=" + projectId);
        }
        if (PROJECT_CANCELLED.equals(project.getStatus())) {
            throw new IllegalArgumentException("工单已作废，修复对象无效，不能填报工时: "
                    + project.getProjectNo());
        }
        if (!PROJECT_REPAIRING.equals(project.getStatus())) {
            throw new IllegalArgumentException("工单当前状态为 " + project.getStatus()
                    + "，仅修复中（REPAIRING）工单可填报工时");
        }
        Relic relic = relicMapper.selectById(project.getRelicId());
        if (relic == null || REVOKED.equals(relic.getStatus())) {
            throw new IllegalArgumentException("文物已撤销，修复对象无效，不能填报工时");
        }
        return project;
    }

    private void requireLatestVersion(WorkhourEntry entry) {
        WorkhourEntry latest = entryMapper.selectOne(new LambdaQueryWrapper<WorkhourEntry>()
                .eq(WorkhourEntry::getChainNo, entry.getChainNo())
                .orderByDesc(WorkhourEntry::getVersionNo)
                .last("LIMIT 1"));
        if (latest != null && !latest.getId().equals(entry.getId())) {
            throw new IllegalArgumentException("填报链 " + entry.getChainNo()
                    + " 已存在更新版本 v" + latest.getVersionNo()
                    + "，只能操作最新版本；旧版本封存只读");
        }
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
    }

    /** 故障窗口内设备停用：与在途故障时段重叠的填报一律拒绝 */
    private void checkEquipmentUsable(RepairEquipment equipment, LocalDateTime start,
                                      LocalDateTime end) {
        if (EQUIP_DISABLED.equals(equipment.getStatus())) {
            throw new IllegalArgumentException("设备已停用，不能排程: " + equipment.getEquipmentNo());
        }
        Long faults = faultMapper.selectCount(new LambdaQueryWrapper<EquipmentFault>()
                .eq(EquipmentFault::getEquipmentId, equipment.getId())
                .eq(EquipmentFault::getStatus, FAULT_OPEN)
                .lt(EquipmentFault::getFaultStart, end)
                .gt(EquipmentFault::getFaultEnd, start));
        if (faults != null && faults > 0) {
            throw new IllegalArgumentException("设备 " + equipment.getEquipmentNo()
                    + " 在该时段存在故障停用记录，不能排程");
        }
    }

    /** 设备同一时段不能被两个工单占用：与有效占用口径时间窗重叠即拒绝 */
    private void checkOverlap(RepairEquipment equipment, LocalDateTime start,
                              LocalDateTime end, String excludeChainNo) {
        for (WorkhourEntry occupied : effectiveEntries(equipment.getId(), start, end, null)) {
            if (excludeChainNo != null && excludeChainNo.equals(occupied.getChainNo())) {
                continue;
            }
            RepairProject project = projectMapper.selectById(occupied.getProjectId());
            throw new IllegalArgumentException("设备 " + equipment.getEquipmentNo()
                    + " 在 " + occupied.getStartTime() + "~" + occupied.getEndTime()
                    + " 已被工单 " + (project == null ? occupied.getProjectId() : project.getProjectNo())
                    + "（填报链 " + occupied.getChainNo() + "）占用，同一时段不能被两个工单占用");
        }
    }

    private void fillEntry(WorkhourEntry entry, WorkhourSubmitRequest req) {
        entry.setProjectId(req.getProjectId());
        entry.setRestorer(req.getRestorer());
        entry.setProcessStep(req.getProcessStep());
        entry.setEquipmentId(req.getEquipmentId());
        entry.setStartTime(req.getStartTime());
        entry.setEndTime(req.getEndTime());
    }

    private void appendLedger(Long entryId, String chainNo, Long equipmentId, Long faultId,
                              Long suggestionId, String action, String bizNo, String summary,
                              LocalDateTime occurredAt) {
        WorkhourLedger ledger = new WorkhourLedger();
        ledger.setEntryId(entryId);
        ledger.setChainNo(chainNo);
        ledger.setEquipmentId(equipmentId);
        ledger.setFaultId(faultId);
        ledger.setSuggestionId(suggestionId);
        ledger.setAction(action);
        ledger.setBizNo(bizNo);
        ledger.setSummary(summary);
        ledger.setOccurredAt(occurredAt);
        ledgerMapper.insert(ledger);
    }
}
