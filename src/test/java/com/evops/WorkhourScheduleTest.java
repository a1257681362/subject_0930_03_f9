package com.evops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 修复工时与设备排程（museum-9）业务规则验收：
 * 1) 修复师按工单填报起止时间/工序/设备，仅 REPAIRING 工单可填报；
 * 2) 设备同一时段不能被两个工单占用，端点相接与不同设备放行；
 * 3) 工时提交后负责人审核，退回封存只读、修订只能补充新版本；
 * 4) 跨天作业按自然日拆分统计（仅审核通过工时）；
 * 5) 设备故障自动生成改期建议，但原排程一行不改；采纳需显式操作并生成待审核新版本；
 * 6) 修复对象置为无效（工单作废 + 文物撤销）后，排程的页面 / CSV 导出 / 后台 JSON 同口径剔除。
 * 使用独立内存库，不调用起步接口。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:museum-workhour;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkhourScheduleTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void workhour_schedule_rules_hold() throws Exception {
        // ---- 两个 REPAIRING 工单（三级文物：会签顺序 馆内 -> 保管 -> 外部） ----
        long project1 = createRepairingProject("WH-R-001", "RP-WH-1", "工时测试陶罐一");
        long project2 = createRepairingProject("WH-R-002", "RP-WH-2", "工时测试陶罐二");
        long relic1 = dataOf(getJson("/api/relics?keyword=WH-R-001")).get(0).get("id").asLong();

        // ---- 设备建档 ----
        long eq1 = dataOf(postJson("/api/equipment", "{"
                + "\"equipmentNo\":\"EQ-WH-1\",\"name\":\"超声波清洗机\",\"equipType\":\"清洗设备\"}"))
                .get("id").asLong();
        long eq2 = dataOf(postJson("/api/equipment", "{"
                + "\"equipmentNo\":\"EQ-WH-2\",\"name\":\"修复显微镜\",\"equipType\":\"显微设备\"}"))
                .get("id").asLong();
        expectFail(postJson("/api/equipment", "{"
                + "\"equipmentNo\":\"EQ-WH-1\",\"name\":\"重号设备\"}"), "设备编号已存在");

        // ---- 非 REPAIRING 工单不能填报 ----
        long relic3 = createRelic("WH-R-003", "工时测试陶罐三");
        long plannedProject = dataOf(postJson("/api/repair-projects", "{"
                + "\"projectNo\":\"RP-WH-3\",\"relicId\":" + relic3
                + ",\"client\":\"藏品部\",\"plan\":\"仅立项未开工\"}")).get("id").asLong();
        expectFail(postJson("/api/workhour-entries", entryJson(plannedProject, "张师傅", "清洗",
                eq1, "2026-12-01 09:00:00", "2026-12-01 10:00:00")), "REPAIRING");

        // ---- 填报：起止时间校验 ----
        expectFail(postJson("/api/workhour-entries", entryJson(project1, "张师傅", "清洗",
                eq1, "2026-12-01 10:00:00", "2026-12-01 09:00:00")), "开始时间必须早于结束时间");

        // e1：工单1 张师傅 清洗 EQ1 09:00-12:00
        JsonNode e1 = dataOf(postJson("/api/workhour-entries", entryJson(project1, "张师傅", "清洗",
                eq1, "2026-12-01 09:00:00", "2026-12-01 12:00:00")));
        long e1Id = e1.get("id").asLong();
        String chain1 = e1.get("chainNo").asText();
        assertEquals(1, e1.get("versionNo").asInt());
        assertEquals("PENDING", e1.get("status").asText());

        // 设备同一时段不能被两个工单占用：EQ1 11:00-13:00 与 e1 重叠
        expectFail(postJson("/api/workhour-entries", entryJson(project2, "李师傅", "测绘",
                eq1, "2026-12-01 11:00:00", "2026-12-01 13:00:00")), "占用");
        // 端点相接放行：EQ1 12:00-14:00（e2，工单2）
        JsonNode e2 = dataOf(postJson("/api/workhour-entries", entryJson(project2, "李师傅", "测绘",
                eq1, "2026-12-01 12:00:00", "2026-12-01 14:00:00")));
        long e2Id = e2.get("id").asLong();
        String chain2 = e2.get("chainNo").asText();
        // 不同设备同时段放行：EQ2 10:00-13:00（e3，工单2）
        dataOf(postJson("/api/workhour-entries", entryJson(project2, "李师傅", "观察",
                eq2, "2026-12-01 10:00:00", "2026-12-01 13:00:00")));
        // 跨天作业：EQ2 2026-12-01 22:00 - 2026-12-02 02:00（e4，工单1）
        JsonNode e4 = dataOf(postJson("/api/workhour-entries", entryJson(project1, "王师傅", "加固",
                eq2, "2026-12-01 22:00:00", "2026-12-02 02:00:00")));
        long e4Id = e4.get("id").asLong();

        // ---- 审核：通过 / 退回只读 / 修订只能补充新版本 ----
        expectOk(postJson("/api/workhour-entries/" + e1Id + "/review",
                "{\"decision\":\"APPROVE\",\"reviewer\":\"李主任\"}"));
        expectFail(postJson("/api/workhour-entries/" + e1Id + "/review",
                "{\"decision\":\"APPROVE\",\"reviewer\":\"李主任\"}"), "不能重复审核");
        // 退回必须填原因
        expectFail(postJson("/api/workhour-entries/" + e2Id + "/review",
                "{\"decision\":\"RETURN\",\"reviewer\":\"李主任\"}"), "退回原因");
        expectOk(postJson("/api/workhour-entries/" + e2Id + "/review",
                "{\"decision\":\"RETURN\",\"reviewer\":\"李主任\",\"comment\":\"时段与库内作业冲突\"}"));
        // 退回版本封存只读：不能再审核，修订只能补新版本
        expectFail(postJson("/api/workhour-entries/" + e2Id + "/review",
                "{\"decision\":\"APPROVE\",\"reviewer\":\"李主任\"}"), "封存");
        expectFail(postJson("/api/workhour-entries/" + e2Id + "/revise",
                entryJson(project1, "李师傅", "测绘",
                        eq1, "2026-12-01 13:00:00", "2026-12-01 15:00:00")), "原工单");
        JsonNode e2v2 = dataOf(postJson("/api/workhour-entries/" + e2Id + "/revise",
                entryJson(project2, "李师傅", "测绘",
                        eq1, "2026-12-01 13:00:00", "2026-12-01 15:00:00")));
        long e2v2Id = e2v2.get("id").asLong();
        assertEquals(chain2, e2v2.get("chainNo").asText(), "修订必须沿用原填报链");
        assertEquals(2, e2v2.get("versionNo").asInt());
        assertEquals("PENDING", e2v2.get("status").asText());
        // 旧版本不被改写：v1 仍是 RETURNED 且时段不变
        JsonNode chain2Entries = dataOf(getJson("/api/workhour-entries?chainNo=" + chain2));
        assertEquals(2, chain2Entries.size());
        assertEquals("RETURNED", chain2Entries.get(0).get("status").asText());
        assertEquals("2026-12-01T12:00:00", chain2Entries.get(0).get("startTime").asText());
        // 只能操作最新版本：审核 v1 拒绝
        expectFail(postJson("/api/workhour-entries/" + e2Id + "/review",
                "{\"decision\":\"APPROVE\",\"reviewer\":\"李主任\"}"), "最新版本");
        expectOk(postJson("/api/workhour-entries/" + e2v2Id + "/review",
                "{\"decision\":\"APPROVE\",\"reviewer\":\"李主任\"}"));

        // ---- 设备故障：自动生成改期建议，但不悄悄改变原排程 ----
        JsonNode faultResult = dataOf(postJson("/api/equipment/" + eq1 + "/faults", "{"
                + "\"faultStart\":\"2026-12-01 10:00:00\",\"faultEnd\":\"2026-12-01 20:00:00\","
                + "\"description\":\"超声波发生器损坏\",\"reporter\":\"设备管理员\"}"));
        long faultId = faultResult.get("fault").get("id").asLong();
        assertEquals(2, faultResult.get("suggestionCount").asInt(),
                "故障窗口覆盖 e1(09-12) 与 e2v2(13-15)，应各生成一条建议");
        JsonNode suggestions = dataOf(getJson("/api/reschedule-suggestions?faultId=" + faultId));
        assertEquals(2, suggestions.size());
        Map<Long, JsonNode> byEntry = new HashMap<>();
        for (JsonNode s : suggestions) {
            byEntry.put(s.get("entryId").asLong(), s);
            assertEquals("PENDING", s.get("status").asText());
            assertEquals("2026-12-01T20:00:00", s.get("suggestedStart").asText(),
                    "建议平移到预计修复完成时刻");
        }
        // e1 原时长 3 小时：建议 20:00-23:00；e2v2 原时长 2 小时：建议 20:00-22:00
        assertEquals("2026-12-01T23:00:00",
                byEntry.get(e1Id).get("suggestedEnd").asText());
        assertEquals("2026-12-01T22:00:00",
                byEntry.get(e2v2Id).get("suggestedEnd").asText());
        // 原排程一行不改：e1 仍是 09:00-12:00 APPROVED，e2v2 仍是 13:00-15:00 APPROVED
        JsonNode chain1Entries = dataOf(getJson("/api/workhour-entries?chainNo=" + chain1));
        assertEquals(1, chain1Entries.size(), "故障建议不得新增或修改原排程版本");
        assertEquals("2026-12-01T09:00:00", chain1Entries.get(0).get("startTime").asText());
        assertEquals("APPROVED", chain1Entries.get(0).get("status").asText());
        // 设备故障停用：故障窗口内新填报拒绝
        assertEquals("FAULTY", dataOf(getJson("/api/equipment?status=FAULTY"))
                .get(0).get("status").asText());
        expectFail(postJson("/api/workhour-entries", entryJson(project1, "张师傅", "清洗",
                eq1, "2026-12-01 15:00:00", "2026-12-01 16:00:00")), "故障");

        // 采纳建议：显式操作，生成该链待审核新版本，原版本保留不改
        long suggestion1 = byEntry.get(e1Id).get("id").asLong();
        JsonNode adopted = dataOf(postJson("/api/reschedule-suggestions/" + suggestion1 + "/adopt",
                "{\"handler\":\"调度员\"}"));
        assertEquals(chain1, adopted.get("chainNo").asText());
        assertEquals(2, adopted.get("versionNo").asInt());
        assertEquals("PENDING", adopted.get("status").asText(), "采纳生成的新版本仍需负责人审核");
        assertEquals("RESCHEDULE", adopted.get("source").asText());
        assertEquals("2026-12-01T20:00:00", adopted.get("startTime").asText());
        assertEquals("ADOPTED", dataOf(getJson("/api/reschedule-suggestions?faultId=" + faultId
                + "&status=ADOPTED")).get(0).get("status").asText());
        expectFail(postJson("/api/reschedule-suggestions/" + suggestion1 + "/adopt",
                "{\"handler\":\"调度员\"}"), "已处理");
        // 原版本依旧保留不改
        chain1Entries = dataOf(getJson("/api/workhour-entries?chainNo=" + chain1));
        assertEquals(2, chain1Entries.size());
        assertEquals("2026-12-01T09:00:00", chain1Entries.get(0).get("startTime").asText());
        assertEquals("APPROVED", chain1Entries.get(0).get("status").asText());
        // 忽略另一条建议：原排程维持不变
        long suggestion2 = byEntry.get(e2v2Id).get("id").asLong();
        expectOk(postJson("/api/reschedule-suggestions/" + suggestion2 + "/ignore",
                "{\"handler\":\"调度员\"}"));
        // 故障恢复：设备回到在用
        expectOk(postJson("/api/equipment/faults/" + faultId + "/resolve",
                "{\"handler\":\"设备管理员\"}"));
        assertEquals(0, dataOf(getJson("/api/equipment?status=FAULTY")).size());

        // ---- 跨天作业按自然日拆分统计（仅审核通过工时） ----
        expectOk(postJson("/api/workhour-entries/" + e4Id + "/review",
                "{\"decision\":\"APPROVE\",\"reviewer\":\"李主任\"}"));
        JsonNode stats = dataOf(getJson("/api/workhour-entries/stats?from=2026-12-01&to=2026-12-02"));
        Map<String, Long> minutesByDay = new HashMap<>();
        for (JsonNode row : stats) {
            if ("王师傅".equals(row.get("restorer").asText())) {
                minutesByDay.put(row.get("date").asText(), row.get("minutes").asLong());
            }
        }
        assertEquals(120L, minutesByDay.get("2026-12-01"), "22:00-24:00 计 120 分钟");
        assertEquals(120L, minutesByDay.get("2026-12-02"), "00:00-02:00 计 120 分钟");

        // ---- 排程同口径：页面 / CSV 导出 / 后台 JSON ----
        JsonNode scheduleBefore = dataOf(getJson("/api/workhour-schedule"));
        assertTrue(containsProject(scheduleBefore, "RP-WH-1"), "后台 JSON 应含工单1排程");
        String csvBefore = getText("/api/workhour-schedule/export");
        assertTrue(csvBefore.contains("RP-WH-1"), "CSV 导出应含工单1排程");
        String pageBefore = getText("/workhour-schedule");
        assertTrue(pageBefore.contains("RP-WH-1"), "页面应含工单1排程");

        // 修复对象置为无效：工单作废 + 文物撤销
        expectOk(postJson("/api/repair-projects/" + project1 + "/cancel",
                "{\"reason\":\"修复对象置为无效\"}"));
        expectOk(postJson("/api/relics/" + relic1 + "/revoke",
                "{\"reason\":\"修复对象无效\"}"));

        // 三口径一致剔除：页面、导出、后台都不再出现工单1的排程
        JsonNode scheduleAfter = dataOf(getJson("/api/workhour-schedule"));
        assertFalse(containsProject(scheduleAfter, "RP-WH-1"), "后台 JSON 必须剔除无效对象排程");
        assertTrue(containsProject(scheduleAfter, "RP-WH-2"), "有效工单排程保留");
        String csvAfter = getText("/api/workhour-schedule/export");
        assertFalse(csvAfter.contains("RP-WH-1"), "CSV 导出必须剔除无效对象排程");
        assertTrue(csvAfter.contains("RP-WH-2"));
        String pageAfter = getText("/workhour-schedule");
        assertFalse(pageAfter.contains("RP-WH-1"), "页面必须剔除无效对象排程");
        assertTrue(pageAfter.contains("RP-WH-2"));

        // 无效对象的占用同时释放：工单2 现在可以占用 e1 原时段（09:00-12:00 EQ1）
        expectOk(postJson("/api/workhour-entries", entryJson(project2, "李师傅", "清洗",
                eq1, "2026-12-01 09:00:00", "2026-12-01 12:00:00")));

        // ---- 台账留痕 ----
        JsonNode ledger = dataOf(getJson("/api/workhour-ledgers?equipmentId=" + eq1));
        assertTrue(hasAction(ledger, "WH_SUBMIT"));
        assertTrue(hasAction(ledger, "WH_APPROVE"));
        assertTrue(hasAction(ledger, "WH_RETURN"));
        assertTrue(hasAction(ledger, "WH_REVISE"));
        assertTrue(hasAction(ledger, "EQUIP_FAULT"));
        assertTrue(hasAction(ledger, "SUGGEST_GENERATE"));
        assertTrue(hasAction(ledger, "SUGGEST_ADOPT"));
        assertTrue(hasAction(ledger, "SUGGEST_IGNORE"));
        assertTrue(hasAction(ledger, "EQUIP_FAULT_RESOLVE"));
    }

    // ------------------------------------------------------------------

    /** 三级文物走完全会签并开工，返回 REPAIRING 工单 id */
    private long createRepairingProject(String relicNo, String projectNo, String name)
            throws Exception {
        long relicId = createRelic(relicNo, name);
        long projectId = dataOf(postJson("/api/repair-projects", "{"
                + "\"projectNo\":\"" + projectNo + "\",\"relicId\":" + relicId
                + ",\"client\":\"藏品部\",\"plan\":\"清洗、加固、封护\"}")).get("id").asLong();
        long versionId = dataOf(postJson("/api/repair-projects/" + projectId + "/plan-versions",
                "{\"planContent\":\"清洗加固\",\"internalExpert\":\"馆内专家甲\","
                        + "\"externalExpert\":\"外部专家乙\",\"custodian\":\"保管员丙\","
                        + "\"submittedBy\":\"项目负责人\"}")).get("version").get("id").asLong();
        // 三级文物会签顺序：馆内专家 -> 保管人员 -> 外部专家
        signVersion(versionId, "INTERNAL", "馆内专家甲");
        signVersion(versionId, "CUSTODIAN", "保管员丙");
        signVersion(versionId, "EXTERNAL", "外部专家乙");
        expectOk(postEmpty("/api/repair-projects/" + projectId + "/start"));
        return projectId;
    }

    private long createRelic(String relicNo, String name) throws Exception {
        return dataOf(postJson("/api/relics", "{"
                + "\"relicNo\":\"" + relicNo + "\",\"name\":\"" + name + "\","
                + "\"category\":\"陶器\",\"material\":\"陶\",\"era\":\"唐代\","
                + "\"source\":\"征集\",\"preservationGrade\":\"THREE\"}")).get("id").asLong();
    }

    private void signVersion(long versionId, String role, String name) throws Exception {
        expectOk(postJson("/api/plan-versions/" + versionId + "/sign", "{"
                + "\"signerRole\":\"" + role + "\",\"signerName\":\"" + name + "\","
                + "\"decision\":\"APPROVE\"}"));
    }

    private String entryJson(long projectId, String restorer, String step, long equipmentId,
                             String start, String end) {
        return "{\"projectId\":" + projectId + ",\"restorer\":\"" + restorer + "\","
                + "\"processStep\":\"" + step + "\",\"equipmentId\":" + equipmentId + ","
                + "\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private boolean containsProject(JsonNode schedule, String projectNo) {
        for (JsonNode row : schedule) {
            if (projectNo.equals(row.get("projectNo").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAction(JsonNode ledger, String action) {
        for (JsonNode e : ledger) {
            if (action.equals(e.get("action").asText())) {
                return true;
            }
        }
        return false;
    }

    private String getText(String uri) throws Exception {
        return mockMvc.perform(get(uri)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult postJson(String uri, String body) throws Exception {
        return mockMvc.perform(post(uri)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
    }

    private MvcResult postEmpty(String uri) throws Exception {
        return mockMvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
    }

    private MvcResult getJson(String uri) throws Exception {
        return mockMvc.perform(get(uri)).andExpect(status().isOk()).andReturn();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    private void expectOk(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(root.get("success").asBoolean(),
                "期望成功，实际: " + root.get("message").asText());
    }

    private void expectFail(MvcResult result, String messageContains) throws Exception {
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains(messageContains),
                "错误信息应包含「" + messageContains + "」，实际: " + root.get("message").asText());
    }
}
