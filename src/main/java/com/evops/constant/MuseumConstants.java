package com.evops.constant;

/**
 * 文物修复台账各业务表的状态与动作常量。
 * 库中一律存字符串码值，禁止用物理删除表达"撤销"。
 */
public final class MuseumConstants {

    private MuseumConstants() {
    }

    /** 文物档案 / 入藏登记状态 */
    public static final String ACTIVE = "ACTIVE";
    public static final String REVOKED = "REVOKED";

    /** 保存等级：一 / 二 / 三级 */
    public static final String GRADE_ONE = "ONE";
    public static final String GRADE_TWO = "TWO";
    public static final String GRADE_THREE = "THREE";

    /** 修复项目状态：立项 -> 修复中 -> 修复完成 -> 已验收（作废为旁路终态） */
    public static final String PROJECT_PLANNED = "PLANNED";
    public static final String PROJECT_REPAIRING = "REPAIRING";
    public static final String PROJECT_REPAIRED = "REPAIRED";
    public static final String PROJECT_ACCEPTED = "ACCEPTED";
    public static final String PROJECT_CANCELLED = "CANCELLED";

    /** 借还状态 */
    public static final String LOAN_LENT = "LENT";
    public static final String LOAN_RETURNED = "RETURNED";
    public static final String LOAN_OVERDUE = "OVERDUE";

    /** 验收结论 */
    public static final String ACCEPT_PASS = "PASS";
    public static final String ACCEPT_CONDITIONAL = "CONDITIONAL";

    /** 台账流水动作 */
    public static final String ACTION_INTAKE = "INTAKE";
    public static final String ACTION_LOAN_OUT = "LOAN_OUT";
    public static final String ACTION_RETURN = "RETURN";
    public static final String ACTION_REPAIR_START = "REPAIR_START";
    public static final String ACTION_REPAIR_STAGE = "REPAIR_STAGE";
    public static final String ACTION_REPAIR_FINISH = "REPAIR_FINISH";
    public static final String ACTION_ACCEPT = "ACCEPT";
    public static final String ACTION_REVOKE = "REVOKE";
    public static final String ACTION_CANCEL = "CANCEL";
    /** 影像归档：批量上传确认 / 影像作废 / 权限收紧 / 权限放开 */
    public static final String ACTION_IMAGE_UPLOAD = "IMAGE_UPLOAD";
    public static final String ACTION_IMAGE_VOID = "IMAGE_VOID";
    public static final String ACTION_POLICY_TIGHTEN = "PERMIT_TIGHTEN";
    public static final String ACTION_POLICY_RELAX = "PERMIT_RELAX";

    /** 起步幂等锁固定主键，全表只允许一行 */
    public static final int BOOTSTRAP_LOCK_ID = 1;

    // --------------------------------------------------------------------
    // museum-2 文物多角度影像归档
    // --------------------------------------------------------------------

    /** 影像视角：正面 / 背面 / 底部 / 细部 */
    public static final String VIEW_FRONT = "FRONT";
    public static final String VIEW_BACK = "BACK";
    public static final String VIEW_BOTTOM = "BOTTOM";
    public static final String VIEW_DETAIL = "DETAIL";

    /** 影像引用 / 拍摄批次状态：有效 / 已作废（作废不是删除） */
    public static final String IMAGE_ACTIVE = "ACTIVE";
    public static final String IMAGE_VOID = "VOID";
    public static final String BATCH_CONFIRMED = "CONFIRMED";

    /** 权限策略表固定主键，全表只允许一行 */
    public static final int POLICY_ID = 1;

    /** 岗位（下载脱敏按岗位区分）：修复师 / 馆藏保管员 / 观摩访客 */
    public static final String ROLE_RESTORER = "RESTORER";
    public static final String ROLE_CURATOR = "CURATOR";
    public static final String ROLE_VIEWER = "VIEWER";

    // --------------------------------------------------------------------
    // museum-3 修复材料批号与用量核算
    // --------------------------------------------------------------------

    /** 材料类别：颜料 / 黏合剂 / 保护剂 */
    public static final String MATERIAL_PIGMENT = "PIGMENT";
    public static final String MATERIAL_ADHESIVE = "ADHESIVE";
    public static final String MATERIAL_PROTECTANT = "PROTECTANT";

    /** 材料目录状态：在用 / 停用（停用不影响历史领用事实） */
    public static final String MATERIAL_ACTIVE = "ACTIVE";
    public static final String MATERIAL_DISABLED = "DISABLED";

    /** 批号库存状态：在库 / 用尽 / 报废 */
    public static final String BATCH_IN_STOCK = "IN_STOCK";
    public static final String BATCH_EXHAUSTED = "EXHAUSTED";
    public static final String BATCH_DISPOSED = "DISPOSED";

    /** 领用单状态：已领用 / 已退料 */
    public static final String USAGE_ISSUED = "ISSUED";
    public static final String USAGE_RETURNED = "RETURNED";

    /** 复核状态：无需复核 / 待复核 / 复核确认（明细行与领用单共用） */
    public static final String REVIEW_NONE = "NONE";
    public static final String REVIEW_PENDING = "PENDING";
    public static final String REVIEW_CONFIRMED = "CONFIRMED";
    public static final String REVIEW_OPEN = "OPEN";

    /** 台账流水动作：材料入库 / 领用 / 退料 / 差异待复核 / 复核确认 */
    public static final String ACTION_MATERIAL_INBOUND = "MATERIAL_INBOUND";
    public static final String ACTION_MATERIAL_ISSUE = "MATERIAL_ISSUE";
    public static final String ACTION_MATERIAL_RETURN = "MATERIAL_RETURN";
    public static final String ACTION_MATERIAL_REVIEW = "MATERIAL_REVIEW";
    public static final String ACTION_MATERIAL_REVIEW_CONFIRM = "MATERIAL_REVIEW_CONFIRM";

    // --------------------------------------------------------------------
    // museum-4 修复方案会签
    // --------------------------------------------------------------------

    /** 方案版本状态：会签中 / 已退回（只读） / 会签通过 */
    public static final String PLAN_SIGNING = "SIGNING";
    public static final String PLAN_RETURNED = "RETURNED";
    public static final String PLAN_APPROVED = "APPROVED";

    /** 会签角色：馆内专家 / 外部专家 / 保管人员 */
    public static final String SIGN_ROLE_INTERNAL = "INTERNAL";
    public static final String SIGN_ROLE_EXTERNAL = "EXTERNAL";
    public static final String SIGN_ROLE_CUSTODIAN = "CUSTODIAN";

    /** 会签决定：同意 / 退回 */
    public static final String SIGN_DECISION_APPROVE = "APPROVE";
    public static final String SIGN_DECISION_RETURN = "RETURN";

    /** 台账流水动作：方案提交会签 / 会签签署 / 方案退回 / 会签通过 */
    public static final String ACTION_PLAN_SUBMIT = "PLAN_SUBMIT";
    public static final String ACTION_PLAN_SIGN = "PLAN_SIGN";
    public static final String ACTION_PLAN_RETURN = "PLAN_RETURN";
    public static final String ACTION_PLAN_APPROVE = "PLAN_APPROVE";

    // --------------------------------------------------------------------
    // museum-5 展厅温湿度与照度巡检
    // --------------------------------------------------------------------

    /** 展厅状态：开放 / 关闭（关闭后不再生成新巡检任务，历史保留） */
    public static final String HALL_ACTIVE = "ACTIVE";
    public static final String HALL_CLOSED = "CLOSED";

    /** 展厅文物陈展状态：展出中 / 已撤展 */
    public static final String DISPLAY_ON = "ON_DISPLAY";
    public static final String DISPLAY_WITHDRAWN = "WITHDRAWN";

    /** 环境规则版本状态：草稿 / 当前生效 / 已被新版本取代（只追加不改写） */
    public static final String ENV_RULE_DRAFT = "DRAFT";
    public static final String ENV_RULE_ACTIVE = "ACTIVE";
    public static final String ENV_RULE_SUPERSEDED = "SUPERSEDED";

    /** 巡检任务状态：待巡检 / 已完成 / 已取消（规则新版本不改写已完成巡检） */
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_DONE = "DONE";
    public static final String TASK_CANCELLED = "CANCELLED";

    /** 环境指标码：温度 / 相对湿度 / 照度 */
    public static final String METRIC_TEMP = "TEMP";
    public static final String METRIC_HUMIDITY = "HUMIDITY";
    public static final String METRIC_LUX = "LUX";

    /**
     * 异常事件状态：
     * OPEN 异常持续中（连续超限合并）/ HANDLING 处置中
     * / RESOLVED 指标恢复待保管员复核 / CLOSED 保管员复核关闭
     */
    public static final String EVENT_OPEN = "OPEN";
    public static final String EVENT_HANDLING = "HANDLING";
    public static final String EVENT_RESOLVED = "RESOLVED";
    public static final String EVENT_CLOSED = "CLOSED";

    /** 环境台账流水动作 */
    public static final String ACTION_RULE_PUBLISH = "RULE_PUBLISH";
    public static final String ACTION_TASK_GENERATE = "TASK_GENERATE";
    public static final String ACTION_READING_OK = "READING_OK";
    public static final String ACTION_READING_ABNORMAL = "READING_ABNORMAL";
    public static final String ACTION_EVENT_OPEN = "EVENT_OPEN";
    public static final String ACTION_EVENT_MERGE = "EVENT_MERGE";
    public static final String ACTION_EVENT_RESOLVE = "EVENT_RESOLVE";
    public static final String ACTION_EVENT_HANDLE = "EVENT_HANDLE";
    public static final String ACTION_EVENT_REVIEW_REJECT = "EVENT_REVIEW_REJECT";
    public static final String ACTION_EVENT_CLOSE = "EVENT_CLOSE";

    // --------------------------------------------------------------------
    // museum-6 文物借展协议与押运闭环
    // --------------------------------------------------------------------

    /** 借展协议主单状态：待签 / 已签生效 / 押运中 / 已归还验收 / 已取消 */
    public static final String AGR_DRAFT = "DRAFT";
    public static final String AGR_SIGNED = "SIGNED";
    public static final String AGR_IN_TRANSIT = "IN_TRANSIT";
    public static final String AGR_RETURNED = "RETURNED";
    public static final String AGR_CANCELLED = "CANCELLED";

    /** 协议版本状态：待签 / 已签生效 / 已被新版本取代（只追加不改写） */
    public static final String AGR_VERSION_DRAFT = "DRAFT";
    public static final String AGR_VERSION_SIGNED = "SIGNED";
    public static final String AGR_VERSION_SUPERSEDED = "SUPERSEDED";

    /** 押运节点：封箱 -> 交接 -> 到馆 -> 开箱（顺序固定） */
    public static final String NODE_SEAL = "SEAL";
    public static final String NODE_HANDOVER = "HANDOVER";
    public static final String NODE_ARRIVAL = "ARRIVAL";
    public static final String NODE_OPEN_BOX = "OPEN_BOX";

    /** 归还验收结论：全部相符 / 存在差异 */
    public static final String CHECK_ALL_MATCH = "ALL_MATCH";
    public static final String CHECK_WITH_DIFF = "WITH_DIFF";

    /** 归还逐行比对结果：相符 / 损伤 / 缺失 */
    public static final String LINE_INTACT = "INTACT";
    public static final String LINE_DAMAGED = "DAMAGED";
    public static final String LINE_MISSING = "MISSING";

    /** 台账流水动作：协议登记/签署/变更、节点登记/异常/异常解除、归还验收 */
    public static final String ACTION_LOAN_AGR_REGISTER = "LOAN_AGR_REGISTER";
    public static final String ACTION_LOAN_AGR_SIGN = "LOAN_AGR_SIGN";
    public static final String ACTION_LOAN_AGR_AMEND = "LOAN_AGR_AMEND";
    public static final String ACTION_ESCORT_NODE = "ESCORT_NODE";
    public static final String ACTION_ESCORT_ANOMALY = "ESCORT_ANOMALY";
    public static final String ACTION_ESCORT_RESUME = "ESCORT_RESUME";
    public static final String ACTION_LOAN_RETURN_CHECK = "LOAN_RETURN_CHECK";

    // --------------------------------------------------------------------
    // museum-7 文物库房库位与密集架预约
    // --------------------------------------------------------------------

    /** 温区：恒温 / 冷藏 / 常温 */
    public static final String ZONE_CONST_TEMP = "CONST_TEMP";
    public static final String ZONE_COLD = "COLD";
    public static final String ZONE_ORDINARY = "ORDINARY";

    /** 防火等级：F1 最高 / F2 / F3 */
    public static final String FIRE_F1 = "F1";
    public static final String FIRE_F2 = "F2";
    public static final String FIRE_F3 = "F3";

    /** 库区 / 密集架状态：在用 / 关闭停用 */
    public static final String STORAGE_ACTIVE = "ACTIVE";
    public static final String STORAGE_CLOSED = "CLOSED";

    /** 格位状态：可用 / 已停用 */
    public static final String CELL_ACTIVE = "ACTIVE";
    public static final String CELL_DISABLED = "DISABLED";

    /** 预约（兼移动主单）状态：已预约 / 已开工 / 已完成 / 已取消 / 超时释放 */
    public static final String RES_RESERVED = "RESERVED";
    public static final String RES_MOVING = "MOVING";
    public static final String RES_COMPLETED = "COMPLETED";
    public static final String RES_CANCELLED = "CANCELLED";
    public static final String RES_EXPIRED = "EXPIRED";

    /** 移动单状态：移动中 / 已完成 / 已取消 */
    public static final String MOVE_MOVING = "MOVING";
    public static final String MOVE_COMPLETED = "COMPLETED";
    public static final String MOVE_CANCELLED = "CANCELLED";

    /** 库存查询岗位：库房管理员（见全部）/ 馆藏保管员（敏感位置脱敏）/ 访客（过滤敏感位置） */
    public static final String ROLE_WAREHOUSE_KEEPER = "WAREHOUSE_KEEPER";

    /** 库房台账流水动作 */
    public static final String ACTION_CELL_MAINTAIN = "CELL_MAINTAIN";
    public static final String ACTION_RESERVE = "RESERVE";
    public static final String ACTION_RESERVE_CANCEL = "RESERVE_CANCEL";
    public static final String ACTION_RESERVE_EXPIRE = "RESERVE_EXPIRE";
    public static final String ACTION_MOVE_START = "MOVE_START";
    public static final String ACTION_MOVE_COMPLETE = "MOVE_COMPLETE";

    // --------------------------------------------------------------------
    // museum-8 虫害监测
    // --------------------------------------------------------------------

    /** 虫害阈值规则版本状态：草稿 / 当前生效 / 已被新版本取代（只追加不改写） */
    public static final String PEST_RULE_DRAFT = "DRAFT";
    public static final String PEST_RULE_ACTIVE = "ACTIVE";
    public static final String PEST_RULE_SUPERSEDED = "SUPERSEDED";

    /** 诱捕点位区域类型：展厅 / 库房 */
    public static final String PEST_ZONE_HALL = "HALL";
    public static final String PEST_ZONE_STORAGE = "STORAGE";

    /** 诱捕点位状态：在用 / 停用（停用不删历史） */
    public static final String PEST_POINT_ACTIVE = "ACTIVE";
    public static final String PEST_POINT_RETIRED = "RETIRED";

    /** 诱捕片状态：在布 / 已更换（更换不删旧片与旧监测记录） */
    public static final String PEST_CARD_ACTIVE = "ACTIVE";
    public static final String PEST_CARD_REPLACED = "REPLACED";

    /** 监测结论：正常 / 超阈值（结论随记录固化，规则新版本不改写旧结论） */
    public static final String PEST_CONCLUSION_NORMAL = "NORMAL";
    public static final String PEST_CONCLUSION_OVER = "OVER_THRESHOLD";

    /** 监测提交人角色：馆内人员 / 外包人员（外包只能提交现场数据，不能关闭任务） */
    public static final String PEST_ROLE_INTERNAL = "INTERNAL";
    public static final String PEST_ROLE_OUTSOURCE = "OUTSOURCE";

    /** 处置任务状态：待处置 / 已关闭 */
    public static final String PEST_TASK_OPEN = "OPEN";
    public static final String PEST_TASK_CLOSED = "CLOSED";

    // --------------------------------------------------------------------
    // museum-9 修复工时与设备排程
    // --------------------------------------------------------------------

    /** 修复设备状态：在用 / 故障停用 / 停用（故障恢复后回到在用） */
    public static final String EQUIP_ACTIVE = "ACTIVE";
    public static final String EQUIP_FAULTY = "FAULTY";
    public static final String EQUIP_DISABLED = "DISABLED";

    /** 工时填报版本状态：待审核 / 审核通过 / 已退回（封存只读，修订只能补新版本） */
    public static final String WH_PENDING = "PENDING";
    public static final String WH_APPROVED = "APPROVED";
    public static final String WH_RETURNED = "RETURNED";

    /** 工时版本来源：正常填报 / 采纳改期建议生成 */
    public static final String WH_SOURCE_NORMAL = "NORMAL";
    public static final String WH_SOURCE_RESCHEDULE = "RESCHEDULE";

    /** 审核决定：通过 / 退回 */
    public static final String WH_DECISION_APPROVE = "APPROVE";
    public static final String WH_DECISION_RETURN = "RETURN";

    /** 设备故障状态：故障中 / 已恢复 */
    public static final String FAULT_OPEN = "OPEN";
    public static final String FAULT_RESOLVED = "RESOLVED";

    /** 改期建议状态：待处理 / 已采纳 / 已忽略 */
    public static final String SUGGEST_PENDING = "PENDING";
    public static final String SUGGEST_ADOPTED = "ADOPTED";
    public static final String SUGGEST_IGNORED = "IGNORED";

    /** 工时台账流水动作 */
    public static final String ACTION_EQUIPMENT_CREATE = "EQUIPMENT_CREATE";
    public static final String ACTION_WH_SUBMIT = "WH_SUBMIT";
    public static final String ACTION_WH_APPROVE = "WH_APPROVE";
    public static final String ACTION_WH_RETURN = "WH_RETURN";
    public static final String ACTION_WH_REVISE = "WH_REVISE";
    public static final String ACTION_EQUIP_FAULT = "EQUIP_FAULT";
    public static final String ACTION_EQUIP_FAULT_RESOLVE = "EQUIP_FAULT_RESOLVE";
    public static final String ACTION_SUGGEST_GENERATE = "SUGGEST_GENERATE";
    public static final String ACTION_SUGGEST_ADOPT = "SUGGEST_ADOPT";
    public static final String ACTION_SUGGEST_IGNORE = "SUGGEST_IGNORE";

    /** 虫害台账流水动作 */
    public static final String ACTION_PEST_RULE_PUBLISH = "RULE_PUBLISH";
    public static final String ACTION_PEST_POINT_CREATE = "POINT_CREATE";
    public static final String ACTION_PEST_POINT_MOVE = "POINT_MOVE";
    public static final String ACTION_PEST_POINT_RETIRE = "POINT_RETIRE";
    public static final String ACTION_PEST_CARD_DEPLOY = "CARD_DEPLOY";
    public static final String ACTION_PEST_CARD_REPLACE = "CARD_REPLACE";
    public static final String ACTION_PEST_RECORD_SUBMIT = "RECORD_SUBMIT";
    public static final String ACTION_PEST_TASK_GENERATE = "TASK_GENERATE";
    public static final String ACTION_PEST_TASK_CLOSE = "TASK_CLOSE";
    public static final String ACTION_PEST_MONTH_FREEZE = "MONTH_FREEZE";
}
