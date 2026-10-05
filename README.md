# EVOPS 基础工作区

这是 springboot 的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

`docs/schema/evops.sql` 是交付副本，与工作区 `src/main/resources/schema.sql` 保持一致（骨架原约定的
`../../docs/schema/evops.sql` 在当前环境根目录无写权限，故置于工作区内）。

## 博物馆文物修复台账（museum-1）

在骨架上新增的业务模块，**不含票务模块**。

### 数据模型

| 表 | 说明 |
| --- | --- |
| `t_relic` | 文物档案：文物编号、材质、年代、来源、保存等级（ONE/TWO/THREE）；撤销只置 `REVOKED` 状态 |
| `t_relic_intake` | 入藏登记：尺寸（高宽厚、重量）、库位、入藏时间，一件文物一条 |
| `t_relic_photo` | 入藏关联照片，一张照片一行 |
| `t_repair_project` | 修复项目：项目编号、委托人、修复方案；状态机见下 |
| `t_repair_stage` | 修复阶段结果，阶段号项目内递增 |
| `t_repair_acceptance` | 验收记录，一个项目一条 |
| `t_relic_loan` | 借出/归还单，一条记录贯通一次借还 |
| `t_relic_ledger` | 台账流水：入藏/借出/归还/修复/验收/撤销/作废/影像归档/权限调整/材料入库领用退料复核全部追加留痕 |
| `t_bootstrap_lock` | 起步幂等锁，固定单行 id=1 |
| `t_photo_file` | 影像物理文件，SHA-256 内容寻址、全库唯一一份；作废不删文件 |
| `t_photo_batch` | 拍摄批次：一次拍摄的正面/背面/底部/细部照片归一批次 |
| `t_relic_image` | 文物影像版本引用：一张照片一行，跨修复阶段形成新版本 |
| `t_permission_policy` | 文物修复权限开关（单行 id=1），收紧时影像上传停止 |
| `t_material` | 修复材料目录：颜料 PIGMENT / 黏合剂 ADHESIVE / 保护剂 PROTECTANT |
| `t_material_batch` / `t_material_inbound` | 材料批号库存与入库流水，按批号管理、同批号追加入库累加库存 |
| `t_material_usage` / `t_material_usage_line` | 工单材料领用单与明细行：批号、计划/实际用量、部位、操作者（行内快照） |
| `t_material_return` | 退料单，必须关联原领用单与原明细行，库存加回原批号 |
| `t_material_review` | 用量差异复核，每条超阈值明细行全生命周期唯一一条（定时任务幂等锚点） |
| `t_hall` / `t_hall_relic` | 展厅与在展陈展（点位+材质快照），巡检任务按展厅生成，撤展只置状态 |
| `t_env_rule_version` / `t_env_rule_item` | 环境巡检规则版本与材质条目（温湿度/照度阈值、采样频率），发布新版本旧版本置 `SUPERSEDED` 只追加 |
| `t_inspection_task` | 巡检任务：按展厅一轮一行，固化规则版本快照，`(hall_id, round_no)` 幂等 |
| `t_env_reading` / `t_env_reading_detail` | 点位读数与按材质判定明细：阈值/版本随读数固化，历史不随后续版本改写 |
| `t_env_event` / `t_env_event_photo` | 异常事件（按展厅+点位+指标+材质归并连续超限）与现场照片，关闭须处置人+照片+保管员复核 |
| `t_env_ledger` | 环境台账：规则发布/任务生成/读数/事件开闭处置复核全部只追加留痕、标明规则版本 |
| `t_loan_agreement` / `t_loan_agreement_version` | 借展协议主单与版本：借展方/保险金额/运输路线/归还日期随版本固化，变更只追加新版本，已签版本置 `SUPERSEDED` 不覆盖 |
| `t_loan_manifest_line` | 借展清单：随协议版本固化的出库清单快照，归还验收按出库版本清单逐项比对 |
| `t_loan_escort_node` | 押运节点：封箱/交接/到馆/开箱四类唯一、按序登记，异常暂停、开箱双人见证 |
| `t_loan_return_check` / `t_loan_return_check_line` | 归还验收头与逐行比对（相符/损伤/缺失），差异永久保留，一协议一条验收 |
| `t_pest_rule_version` / `t_pest_rule_item` | 虫种阈值规则版本与条目：发布新版本旧版本置 `SUPERSEDED` 只追加，监测记录旧结论不改写 |
| `t_pest_trap_point` / `t_pest_point_move` | 展厅/库房诱捕点位（编号/布放日期/坐标/责任区域）与迁移记录（前后坐标只追加） |
| `t_pest_trap_card` | 诱捕片：更换只置 `REPLACED`，旧片与旧监测记录永不删除 |
| `t_pest_monitor_record` / `t_pest_monitor_detail` / `t_pest_monitor_photo` | 监测记录（规则版本快照+结论固化）/ 虫种数量明细（阈值快照）/ 现场照片（内容寻址） |
| `t_pest_dispose_task` | 超阈值处置任务：关联责任区域，外包人员不能关闭 |
| `t_pest_month_freeze` | 监测月份冻结（一月一行只追加）：冻结后该月记录与照片不再接受补录 |
| `t_pest_ledger` | 虫害台账：规则发布/点位/诱捕片/监测/任务/冻结全部只追加留痕 |

约束：文物编号 `uk_relic_no`、项目编号 `uk_project_no` 数据库层唯一（含已撤销/已作废记录）；
撤销与作废均不删除任何数据。

### 状态链路

- 修复项目：`PLANNED → REPAIRING → REPAIRED → ACCEPTED`；`PLANNED/REPAIRING` 可 `CANCELLED`。
- 借出：`LENT → RETURNED`（在原单上更新，不新增记录）。
- 借展协议：`DRAFT → SIGNED → IN_TRANSIT → RETURNED`（`CANCELLED` 为旁路终态）；
  押运节点固定 `封箱 SEAL → 交接 HANDOVER → 到馆 ARRIVAL → 开箱 OPEN_BOX`，任一节点异常则暂停后续操作。
- 跨业务校验：文物借出未还不能重复借出、不能开工修复；有未闭环借还/项目/借展协议的文物不能撤销。

### 主要接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/bootstrap` | **博物馆文物修复档案起步**：首次播种一条走完全链路的有效样本 |
| `GET /api/bootstrap` | 只读查看起步样本（未起步为 null） |
| `POST /api/relics` | 文物建档（编号重复返回失败） |
| `GET  /api/relics?status=&keyword=` | 档案列表 |
| `GET  /api/relics/{id}` | 完整档案：档案 + 入藏照片 + 借还 + 项目 + 台账流水 |
| `POST /api/relics/{id}/intake` | 入藏登记（照片/尺寸/库位，至少一张照片） |
| `POST /api/relics/{id}/revoke` | 撤销（只改状态，记录保留） |
| `POST /api/loans` / `POST /api/loans/{id}/return` | 借出 / 归还 |
| `POST /api/repair-projects` | 修复立项（委托人、方案，项目编号唯一） |
| `POST /api/repair-projects/{id}/start` | 开工 |
| `POST /api/repair-projects/{id}/stages` | 登记阶段结果（可多次） |
| `POST /api/repair-projects/{id}/finish` | 修复完成 |
| `POST /api/repair-projects/{id}/accept` | 验收（PASS/CONDITIONAL） |
| `POST /api/repair-projects/{id}/cancel` | 作废 |

## 文物多角度影像归档（museum-2）

正面（FRONT）、背面（BACK）、底部（BOTTOM）、细部（DETAIL）照片按**拍摄批次**归档。

### 归档规则

- **逐张校验**：上传时逐张校验分辨率（默认不低于 800×800，可由 `museum.image.min-*-px` 调整）、
  拍摄时间（必填、格式 `yyyy-MM-ddTHH:mm:ss` 或 `yyyy-MM-dd HH:mm:ss`、不得晚于当前时间）、
  摄影者（必填）。批量上传中任意一张失败都不影响其余张，响应逐张给出具体原因。
- **同文件去重**：物理文件按 SHA-256 内容寻址落盘（`ab/cd/<hash>.ext`），同一文件重复上传
  只保留一份物理文件、新增一条影像引用（响应中 `duplicateFile=true`）。
- **修复阶段新版本**：同一修复阶段内补拍沿用当前版本号；换一个修复阶段（或非修复场景的新批次）
  必须形成新版本，版本号文物内从 1 递增，只追加、不覆盖。
- **作废不删原文件**：单张/整批作废只把引用与批次置 `VOID` 并登记原因、写台账；物理文件与引用行
  永久保留、可查不可再下载。
- **下载按岗位脱敏**：`RESTORER`（修复师）取原件；`CURATOR`（馆藏保管员）取长边≤1280 加水印的
  JPEG 副本；`VIEWER`（观摩访客/未声明岗位）取长边≤640 的更强脱敏副本，且版本清单中摄影者姓名
  脱敏为「姓\*\*」。原文件不对低权限岗位出库。
- **权限收紧闸门**：`PUT /api/policy/repair-photo-upload {"open":false}` 收紧文物修复权限后，
  影像上传在入口处整体停止、不产生任何批次/版本/文件；已确认的影像版本、物理文件与台账事实
  全部保留，仍可查询与下载。`open=true` 放开后继续按新版本归档。

### 影像接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/relics/{id}/image-batches` | multipart 批量上传一个拍摄批次；`files` 与同序的 `viewTypes/shotAts/photographers`，可选 `projectId/stageId/shotLocation/remark` |
| `GET  /api/relics/{id}/image-versions?role=` | 影像版本清单（按版本分组，按岗位脱敏摄影者） |
| `GET  /api/image-batches/{batchId}?role=` | 拍摄批次详情（批次头 + 逐张影像） |
| `GET  /api/images/{imageId}/download?role=` | 按岗位下载（原件或脱敏副本） |
| `POST /api/images/{imageId}/void` | 作废单张影像（原文件保留） |
| `POST /api/image-batches/{batchId}/void` | 作废整个拍摄批次 |
| `GET  /api/policy/repair-photo-upload` | 查看影像上传权限开关 |
| `PUT  /api/policy/repair-photo-upload` | 收紧（`open=false`）/ 放开（`open=true`），写台账留痕 |

批量上传响应示例（节选）：`versionNo`、`successCount/failedCount`，以及与文件同序的 `items`，
每项含 `index/fileName/viewType/success/duplicateFile/imageId/fileId/reason`。

## 修复材料批号与用量核算（museum-3）

颜料（PIGMENT）、黏合剂（ADHESIVE）和保护剂（PROTECTANT）**按批号入库**；修复工单
（仅 `REPAIRING` 状态可领料）领用时逐行记录**批号、计划用量、实际用量、使用部位与操作者**。

### 核算规则

- **过期材料不能领用**：批号 `expire_date` 早于领用当天即拒绝（允许入库登记临期/过期批号，
  但领用时按当天校验）；非在库（用尽/报废）批号同样禁领。
- **库存与修复进度一致**：库存按批号行管理，领用条件 UPDATE 原子扣减、整单任一失败整体回滚；
  随时可按批号对账：应有库存 = 累计入库 − 累计领用 + 累计退料。
- **历史工单不受批号变更影响**：明细行在领用时固化材料名称、批号、类别、单位、操作者快照，
  材料目录更名/改类或批号后续变化都不改写历史事实。
- **退料关联原领用单**：必须指定原单与原明细行，允许分次退但累计不得超过实际用量；
  库存加回原批号；整单全部退齐时状态 `ISSUED → RETURNED`。退料只追加，不覆盖原领用事实。
- **差异触发复核**：实际相对计划偏差严格超过阈值（`museum.material.variance-threshold-pct`，
  默认 20%）的明细行置 `PENDING`。
- **定时任务重启幂等**：差异复核扫描器在应用就绪时补扫一次、之后按
  `review-scan-interval-ms` 周期扫描；以 `t_material_review.uk_review_line` 为锚点，
  同一明细行**只产生一条复核记录、一条台账流水**——重启重扫**不多写记录**；操作者从原
  领用行原样带入复核单与台账，**不丢操作者**。扫描串行加锁，启动补扫与周期轮次不重入。
- **复核闭环**：`OPEN → CONFIRMED` 后解除行/单的待复核状态；确认人单列，原操作者不被覆盖。

### 材料接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/materials` | 材料建档（颜料/黏合剂/保护剂，编号唯一） |
| `GET  /api/materials?category=` | 材料目录，可按类别过滤 |
| `POST /api/materials/inbound` | 按批号入库（同批号追加入库累加库存、追加入库流水） |
| `GET  /api/materials/batches?materialId=&usable=` | 批号库存列表；`usable=true` 只看在库未过期 |
| `GET  /api/materials/batches/{id}/reconcile` | 批号库存对账 |
| `POST /api/material-usages` | 工单领用（逐行批号/计划实际用量/部位/操作者） |
| `GET  /api/material-usages/{id}` | 领用单详情（单头 + 明细 + 退料） |
| `GET  /api/material-usages?projectId=` | 按工单查领用 |
| `POST /api/material-returns` | 退料（关联原领用单/明细行） |
| `GET  /api/material-reviews?status=` | 差异复核清单 |
| `POST /api/material-reviews/scan` | 手动触发一轮幂等补扫 |
| `POST /api/material-reviews/{id}/confirm` | 复核确认（不覆盖原操作者） |

## 修复方案会签（museum-4）

修复方案按**版本**管理：每次提交一个新版本（版本号项目内递增），由馆内专家（INTERNAL）、
外部专家（EXTERNAL）、保管人员（CUSTODIAN）**依次会签**；全部同意才允许开工生成施工任务。

### 会签规则

- **签名顺序按文物风险配置**：提交版本时取文物保存等级快照 `riskLevel`，会签顺序由
  `museum.countersign.sign-order` 按等级决定（默认一级：外部专家→馆内专家→保管人员；
  二级：馆内→外部→保管；三级：馆内→保管→外部）。乱序签署一律拒绝。
- **退回即只读**：任一专家退回（RETURN）后版本封存为 `RETURNED`，不再接受任何签名；
  修订只能另开新版本，旧版本及其会签记录永不改写。
- **意见、签名时间、依据附件留痕**：每条会签记录保存实签人、决定、意见、
  `signedAt` 签名时间与依据附件引用，只追加不修改。
- **缺席专家不能代签**：实签人必须与版本指定的应签人完全一致，
  管理员或他人代签一律拒绝，系统不提供代签入口。
- **会签完成才允许开工**：`POST /api/repair-projects/{id}/start` 要求项目最新方案版本
  为 `APPROVED`，否则拒绝生成施工任务；开工后不允许再提交新版本。
- **版本归属同一口径**：签名永远挂在"签署时处于会签中的版本"上——版本退回/通过的
  临界时间之后，任何签名都不能再归入已封存版本，也不会混入新版本；
  `(plan_version_id, step_no)` 数据库唯一，同版本同一步骤不可能出现两条签名。

### 会签接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/repair-projects/{id}/plan-versions` | 提交新方案版本（指定三类应签人） |
| `GET  /api/repair-projects/{id}/plan-versions` | 项目方案版本列表（含会签记录） |
| `POST /api/plan-versions/{versionId}/sign` | 会签签署（顺序/应签人由系统校验） |
| `GET  /api/plan-versions/{versionId}` | 版本详情（版本 + 会签顺序 + 会签记录） |

### 起步幂等

`POST /api/bootstrap` 首次调用播种样本 `WW-2026-0001`（入藏 `RU-2026-0001` → 借出/归还
`LO-2026-0001` → 修复项目 `RP-2026-0001`，方案会签（一级：外部专家→馆内专家→保管人员）
通过后开工，含两个修复阶段与验收）；开工后另播种三类修复材料
（颜料 `B-2026-0001` / 黏合剂 `B-2026-0002` / 保护剂 `B-2026-0003` 批号入库）→ 工单领用
`MU-2026-0001`（三行用量，黏合剂行偏差 60% 触发一条差异复核）→ 退料 `MR-2026-0001`。

幂等保证：播种与 `t_bootstrap_lock`（单行、CHECK id=1）在同一事务提交；重复请求直接回读同一行返回
`created=false`，不会产生第二条文物/项目/台账记录；并发请求靠主键冲突整体回滚，已通过 8 并发测试验证。

### 测试

```bash
mvn test
```

- `BootstrapApiTest`：起步两次只产生一条全链路样本，台账动作顺序完整。
- `ConcurrentBootstrapTest`：8 并发同时起步，仅一次播种，全部命中同一条样本。
- `MuseumLedgerRuleTest`：编号唯一、入藏不重复、借还/修复/验收状态机不可跳步、撤销不抹除记录。
- `ImageArchiveTest`：四视角批次归档；分辨率/拍摄时间/摄影者/视角逐张返回失败原因；
  同文件重复上传磁盘仅一份；不同修复阶段形成新版本、同阶段补拍沿用版本；
  作废不删原文件；修复师取原件、保管员/访客取脱敏副本；权限收紧时上传停止且已确认版本保留。
- `MaterialAccountingTest`：三类材料按批号入库、同批号追加累加；过期/用尽批号禁领且失败整单回滚；
  领用记录用量/部位/操作者并原子扣减库存；差异超阈值触发复核，反复扫描（模拟重启）只产生一条复核、
  一条台账且保留操作者；退料关联原领用单、超额拒退、退齐整单关闭；材料更名不改历史工单快照；
  库存按「入库−领用+退料」全程对平。
- `RepairPlanCountersignTest`：会签顺序按风险等级配置（一级外部专家先签、三级外部专家末签）；
  乱序/代签一律拒绝；退回后旧版本只读、修订只能开新版本；意见/签名时间/依据附件留痕；
  会签未完成禁止开工、全部通过后允许开工；版本封存临界前后签名始终归属签署时的在签版本。

## 展厅温湿度与照度巡检（museum-5）

展厅按**点位**对温度、相对湿度、照度三项指标巡检。规则按**材质**配置阈值与采样频率、
按**版本**发布；巡检任务**按展厅**生成；异常必须关联**现场照片与处置人**，
**连续超限合并为同一事件**，事件关闭前必须由**保管员复核**；报表与台账均**标明规则版本**。

### 巡检规则

- **不同材质不同阈值与频率**：规则版本中每个材质一条目
  （温度/湿度/照度上下限 + `sampleIntervalMin` 采样频率，如纸质 60 分钟、青铜 120 分钟）。
  按展厅生成任务时，节拍取展厅内在展文物各材质频率的**最小值**（最严格者决定巡检密度）；
  全部材质均无条目时用 `museum.env.default-interval-min` 兜底。
- **规则按版本发布，只追加不改写**：`POST /api/env-rules/publish` 发布新版本后旧生效版本置
  `SUPERSEDED`（内容永不改写）。任务生成时固化 `rule_version_id/no` 快照，读数按**任务的版本**
  判定并把版本与各材质阈值固化在读数及 `t_env_reading_detail` 明细中——**新版本不改写任何
  已生成/已完成巡检**，任务/读数一经完成不可补改。
- **按展厅生成任务**：定时扫描（`museum.env.generate-interval-ms`，启动就绪补扫一轮）与
  手动 `POST /api/inspection/tasks/generate?force=true` 同一口径；每展厅轮次号递增，
  `(hall_id, round_no)` 数据库唯一，且每个展厅同时只有一个 `PENDING` 轮次，重复触发不生成重复任务。
- **逐材质判定**：一个点位可展陈多种材质，读数按各材质条目分别判定、分别固化阈值明细；
  点位在展但规则无条目的材质标记为 `uncoveredMaterials` 不猜测阈值；读数必须覆盖全部在展点位、
  点位不允许重复。
- **连续超限合并**：事件归并键 `展厅|点位|指标|材质`（`merge_key` 唯一，仅 `OPEN/HANDLING`
  在途事件持有）。同一材质同一指标连续越限只更新同一事件的末次读数/峰值/累计次数，不新开；
  指标恢复正常后事件转 `RESOLVED` 并释放归并键，再次越限另开新事件。
- **异常关联现场照片与处置人**：现场照片走影像归档 SHA-256 内容寻址（同文件只一份，
  不套用 800×800 归档下限）；事件关闭前必须已登记处置人且至少一张现场照片，否则保管员无法关闭。
- **保管员复核关闭**：仅 `RESOLVED`（指标已恢复）事件可复核；复核通过才 `CLOSED` 封存，
  复核不通过退回 `HANDLING`，系统停留待处理，须重新处置并待指标再次恢复后复核。
- **待处理与可追溯**：修复审批只完成一半（方案仍 `SIGNING`）时禁止开工、停留待处理，
  不影响巡检侧；规则发布、任务生成、每条读数、事件开/并/恢复/处置/驳回/关闭全部追加写入
  `t_env_ledger`，任务/读数/事件报表都带规则版本号，环境指标与巡检计划全程可追溯。

### 事件状态链路

`OPEN（连续超限并入）→ HANDLING（已登记处置人）→ RESOLVED（指标恢复待复核）→ CLOSED（保管员复核）`

- `OPEN` 登记处置人即转 `HANDLING`；处置中仍接受连续超限并入。
- 保管员在非 `RESOLVED` 状态复核一律拒绝（系统停留待处理）。
- 复核不通过：退回 `HANDLING`；现场持续恢复后系统自动重新置回 `RESOLVED`，不留死事件。

### 巡检接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/halls` / `GET /api/halls` / `GET /api/halls/{id}` | 展厅建档 / 列表 / 详情（含在展文物点位材质） |
| `POST /api/halls/{id}/relics` / `POST /api/halls/{id}/relics/{relicId}/withdraw` | 文物按点位上展 / 撤展（历史保留） |
| `POST /api/env-rules/publish` | 发布规则新版本（旧版本自动 `SUPERSEDED`） |
| `GET /api/env-rules` / `GET /api/env-rules/active` / `GET /api/env-rules/{id}` | 版本列表 / 当前生效版本 / 版本材质条目 |
| `POST /api/inspection/tasks/generate?force=true` | 手动触发一轮按展厅生成（定时任务同口径，幂等） |
| `GET /api/inspection/tasks?hallId=&status=` | 巡检任务列表（每项标明规则版本） |
| `POST /api/inspection/tasks/{id}/readings` | 提交一轮各点位温湿度/照度读数（覆盖全部点位） |
| `GET /api/inspection/tasks/{id}/report` | 任务报表：任务+读数+按材质阈值明细，标明规则版本 |
| `GET /api/env-events?hallId=&status=&pointCode=&material=` | 异常事件列表（可按状态/点位/材质过滤） |
| `GET /api/env-events/{id}` | 事件报表：事件头（首警规则版本）+ 现场照片 |
| `POST /api/env-events/{id}/handle` | 登记处置人与处置说明 |
| `POST /api/env-events/{id}/photos` | multipart 上传现场照片（内容寻址去重，关闭前至少一张） |
| `POST /api/env-events/{id}/review` | 保管员复核：通过关闭 / 不通过退回处置中 |
| `GET /api/env-ledgers?hallId=` | 环境台账（不传展厅为全馆；规则发布挂全馆） |

### 测试

`EnvInspectionTest`（独立内存库）覆盖：三类材质不同阈值/频率、任务节拍取最小频率；
任务/读数/事件固化版本快照，发布 v2 后历史任务与读数仍按 v1 阈值；连续两轮超限合并为一个事件、
恢复后再次超限另开新事件；关闭前缺处置人或缺照片均拒绝、保管员复核通过才关闭、驳回停留待处理；
同点位纸/青铜同时越限分别成事件；十轮任务与环境台账全程标明规则版本可追溯；
修复方案会签只完成一半时禁止开工、巡检侧任务与指标不受影响。

## 文物借展协议与押运闭环（museum-6）

借展按**协议**登记借展方、保险金额、运输路线与归还日期；协议变更按**版本**只追加；
押运按**封箱 → 交接 → 到馆 → 开箱**四个节点推进；归还按**原借展清单逐项验收**并保留差异。

### 借展规则

- **协议登记**：借展方、保险金额（不可为负）、运输路线、约定归还日期与借展清单（至少一行）必填；
  同一文物同时只允许一条未闭环（未归还验收/未取消）协议；协议首版为 `DRAFT`，签署后才生效。
- **协议变更只出新版**：变更复制当前版本条款与清单生成新版本（`version_no` 协议内递增，变更原因必填）；
  新版本签署时旧 `SIGNED` 版本置 `SUPERSEDED`，**已签协议不能直接覆盖**，旧版本与清单快照永不改写；
  新版本签署前押运节点一律暂停。
- **押运节点按序推进**：封箱 `SEAL` → 交接 `HANDOVER` → 到馆 `ARRIVAL` → 开箱 `OPEN_BOX`，
  每类节点协议内仅一条，跳序/重复登记均拒绝；节点固化登记时的协议版本与处理人。
- **任一节点异常即暂停**：节点登记异常必须填异常原因，此后后续节点全部拒绝登记，
  须先解除异常（登记解除人与解除说明）才能继续；异常原因、处理人、解除人全部留痕。
- **开箱双人见证**：开箱必须登记两名**不同**见证人，单人或同名一律拒绝。
- **封箱贯通既有占用口径**：首节点封箱时向 `t_relic_loan` 写一条 `LENT` 记录（单号取协议号），
  押运在途期间修复开工、文物撤销、旧通道重复借出继续被 museum-1 跨业务校验拦截。
- **归还逐项验收**：开箱完成且异常全部解除后，按**封箱出库时签署版本的原清单**逐行比对，
  漏项/多项/重复均拒绝；每行给 `INTACT 相符 / DAMAGED 损伤 / MISSING 缺失`，
  非相符行必须填差异说明；结论全部相符 `ALL_MATCH`，否则 `WITH_DIFF`，差异逐行永久保留；
  验收闭环后协议置 `RETURNED`，贯通借出记录同步归还。
- **修复侧反查**：`GET /api/loan-agreements/trace?projectId=` 由修复项目经文物反查借展原单，
  返回原单号、当前版本、借展方/保险金额/路线/归还日期、四节点处理人及**异常原因/解除人**、验收差异。
- 协议登记/签署/变更、节点登记/异常/解除、归还验收全部追加写入 `t_relic_ledger` 留痕。

### 借展接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/loan-agreements` | 协议登记（借展方/保险金额/运输路线/归还日期 + 清单） |
| `GET  /api/loan-agreements?relicId=&status=` | 协议列表 |
| `GET  /api/loan-agreements/{id}` | 协议详情：主单 + 全部版本与清单 + 押运节点 + 归还验收 |
| `POST /api/loan-agreements/{id}/sign` | 签署当前待签版本（旧版自动 `SUPERSEDED`） |
| `POST /api/loan-agreements/{id}/amend` | 协议变更，生成待签新版本（已签内容不覆盖） |
| `POST /api/loan-agreements/{id}/escort-nodes` | 登记押运节点（封箱/交接/到馆/开箱；异常暂停、开箱双人见证） |
| `POST /api/escort-nodes/{nodeId}/resolve` | 押运异常解除，后续节点恢复 |
| `POST /api/loan-agreements/{id}/return-check` | 归还验收（与原清单逐项比对，差异保留） |
| `GET  /api/loan-agreements/trace?projectId=` | 修复侧反查：原单、节点处理人、异常原因与验收差异 |

### 测试

`LoanExhibitionTest`（独立内存库）覆盖：登记必填与重复协议拦截；未签不能押运、已签只能变更出新版；
四节点跳序/重复拒绝；节点异常暂停、解除后恢复且异常原因/处理人/解除人可查；
开箱单人/同名见证人拒绝、两名不同见证人通过；归还验收漏项/多项拒绝、损伤与缺失差异逐行保留、
贯通借出记录同步归还；修复项目反查定位原单与异常原因；在途期间修复开工与文物撤销被拦截；
台账动作链完整可追溯。

## 文物库房库位与密集架预约（museum-7）

库房按**库区 → 密集架 → 格位**三级维护：库位按**温区**（恒温 CONST_TEMP / 冷藏 COLD /
常温 ORDINARY）、**防火等级**（F1/F2/F3）与**承重**建档；文物/箱体搬入前必须先
**预约目标格位**；**移动单**固化原位、目的位、箱号与操作人员。

### 预约与移动规则

- **搬入前必须预约**：预约登记目标格位、箱号、计划操作时段与预约人，格位承重超限、
  格位停用、原格位未存放该箱（库内移动）一律拒绝；同一格位同时只有一个在途预约
  （`hold_key` 稀疏唯一约束，终态置空）。
- **超时自动释放，但不释放已开工格位**：预约 `expire_at = 预约时间 + reservation-expire-minutes`
  （默认 30 分钟，可在请求中以 `expireMinutes` 覆盖）；定时扫描（启动补扫 + 周期扫描，
  串行不重入）只把**仍为 RESERVED 且已到期**的单置 EXPIRED 并清格位持有键，
  **WHERE 不含 MOVING——已开始移动的格位永不释放**，释放后格位可再约。
- **互斥设备同时段单一操作**：以一台密集架为互斥单位，同一架体上时间窗重叠
  （`start < 存在.end AND end > 存在.start`）的在途预约/移动只允许一个，时间端点相接、
  不同架体并行放行；架体行 UPDATE 占位锁把同架并发串行化，重叠判定在锁内复查。
- **移动单留痕**：开工（RESERVED→MOVING）登记操作人员并生成移动单，固化原位/目的位
  /箱号/操作人员快照；完成（MOVING→COMPLETED）在 `t_storage_stock` 原子清原格、立目的格；
  已开工不可取消；开工/完成/取消重复调用幂等，不产生第二条。
- **补传/补录/重试只形成一条结果**：预约以请求流水号 `requestNo` 为幂等锚点（全库唯一），
  同号重复提交（含并发）命中同一条预约并原样返回，绝不产生第二条可用结果。
- **库存查询按馆员权限隐藏敏感位置**（`?role=`，沿用 museum-2 传参方式）：
  `WAREHOUSE_KEEPER` 库房管理员见全部精确位置；`CURATOR` 馆藏保管员对敏感格位保留库存
  事实但精确位置脱敏（`masked=true`、`locationName="敏感库位"`，格位/架/库区/层列置空）；
  `VIEWER`（访客/未声明角色）直接过滤掉敏感格位。
- 建档停用、预约/取消/超时释放、移动开工/完成全部只追加写入 `t_storage_ledger`。

### 库房接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/storage/areas` / `GET /api/storage/areas` / `POST /api/storage/areas/{id}/close` | 库区建档（温区/防火等级/承重）/ 列表 / 关闭 |
| `POST /api/storage/racks` / `GET /api/storage/racks?areaId=` | 密集架（互斥设备）建档 / 列表 |
| `POST /api/storage/cells` / `GET /api/storage/cells?areaId=&rackId=` / `POST /api/storage/cells/{id}/disable` | 格位建档（承重上限/敏感标记，温区防火默认随库区）/ 列表 / 停用 |
| `POST /api/storage/reservations` | 预约目标格位（`requestNo` 幂等；可选 originCellId/relicId/weightKg/expireMinutes） |
| `GET  /api/storage/reservations?status=&requestNo=&cellId=` | 预约列表 |
| `GET  /api/storage/reservations/{id}` | 预约详情：预约 + 移动单 + 库房台账 |
| `POST /api/storage/reservations/{id}/start` | 开工移动（操作人员），生成移动单 |
| `POST /api/storage/reservations/{id}/complete` | 完成移动，库存清原格立目的格 |
| `POST /api/storage/reservations/{id}/cancel` | 取消预约（MOVING 不可取消） |
| `POST /api/storage/reservations/expire-scan` | 手动触发一轮超时释放（定时任务同口径，MOVING 不释放） |
| `GET  /api/storage/move-orders?rackId=&boxNo=` | 移动单列表 |
| `GET  /api/storage/stock?role=&areaId=` | 库存查询，按馆员权限隐藏/脱敏敏感位置 |

### 测试

`StorageReservationTest`（独立内存库）覆盖：温区/防火等级/承重维护与非法枚举、超重、
停用拦截；预约在途唯一、请求流水号补传/补录/重试只一条；互斥设备同架重叠拒绝、错峰与
不同架放行；移动单原位/目的位/箱号/操作人员齐全、完成后库存清原格立目的格、重复调用幂等；
超时自动释放后格位可再约、**MOVING 到期不释放且继续占格**；访客过滤、保管员脱敏、
管理员全见的三档权限；台账动作链可追溯。
`StorageReservationConcurrentTest` 覆盖：8 并发约同格位仅 1 成功、8 并发同架重叠仅 1 成功、
8 并发同一流水号全部命中同一条。

## 虫害监测（museum-8）

展厅与库房设置**诱捕点位**，监测记录登记**虫种数量与照片**；虫种阈值规则按**版本**发布，
监测结论随记录固化；**超阈值生成处置任务并关联责任区域**；**更换诱捕片不删除旧记录**；
**点位迁移保留前后坐标**；**监测结果按月份冻结**；**外包人员只能提交现场数据，不能关闭任务**。

### 监测规则

- **点位建档**：展厅（`HALL`，引用 `t_hall`）与库房（`STORAGE`，引用 `t_storage_area`）均可布放；
  点位编号全库唯一，登记布放日期、坐标与责任区域；区域名称快照自展厅/库区带入。
- **虫种阈值规则按版本发布，只追加不改写**：`POST /api/pest-rules/publish` 发布新版本后
  旧生效版本置 `SUPERSEDED`（内容永不改写）。监测记录提交时固化 `rule_version_id/no` 快照、
  逐虫种阈值快照与判定结论——**规则调整只能生成新版本，不能改动监测记录旧结论**。
- **监测提交**：外包人员（`OUTSOURCE`）与馆内人员（`INTERNAL`）均可提交现场数据；
  虫种数量逐条登记（同记录内虫种不可重复），规则无条目的虫种不猜测阈值、不参与判定；
  记录关联提交时点位的在布诱捕片。照片走影像归档 SHA-256 内容寻址（同文件只一份），
  同一记录内同照片只登记一次。
- **超阈值生成处置任务**：任一虫种数量严格大于阈值即判定 `OVER_THRESHOLD`，
  生成处置任务（一记录至多一任务）并关联点位**责任区域**快照。
- **更换诱捕片不删除旧记录**：布放新片时旧片置 `REPLACED`（含更换时间/更换人），
  旧片与其监测记录永久保留可查；旧监测记录仍指向旧片。
- **点位迁移保留前后坐标**：迁移写入 `t_pest_point_move`（from/to 坐标、迁移人、原因），
  点位行只保存当前坐标，迁移记录只追加。
- **监测结果按月份冻结**：`POST /api/pest-monitor/freeze` 冻结月份（`yyyy-MM`，幂等，
  重复冻结只一条）；冻结后该月监测记录与照片不再接受补录/更改，已冻结事实保留可查；
  冻结只追加，不解冻。
- **外包人员不能关闭任务**：`POST /api/pest-tasks/{id}/close` 仅 `INTERNAL` 可关闭，
  `OUTSOURCE` 一律拒绝；已关闭任务不可重复关闭。
- 规则发布、点位建档/迁移/停用、诱捕片布放/更换、监测提交、任务生成/关闭、月份冻结
  全部只追加写入 `t_pest_ledger`，尽量标明规则版本，虫害监测全程可追溯。

### 监测接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/pest-rules/publish` | 发布虫种阈值规则新版本（旧版本自动 SUPERSEDED） |
| `GET /api/pest-rules` / `GET /api/pest-rules/active` / `GET /api/pest-rules/{id}` | 版本列表 / 当前生效版本 / 版本虫种条目 |
| `POST /api/pest-points` / `GET /api/pest-points` / `GET /api/pest-points/{id}` | 点位建档（编号/布放日期/坐标/责任区域）/ 列表 / 详情（含诱捕片与迁移记录） |
| `POST /api/pest-points/{id}/move` | 点位迁移（保留前后坐标） |
| `POST /api/pest-points/{id}/retire` | 点位停用（历史保留） |
| `POST /api/pest-points/{id}/cards` / `GET /api/pest-points/{id}/cards` | 布放/更换诱捕片（旧片 REPLACED 不删除）/ 诱捕片列表 |
| `POST /api/pest-records` | 提交监测记录（虫种数量；外包/馆内均可；超阈值生成处置任务） |
| `GET  /api/pest-records?pointId=&month=&conclusion=` | 监测记录列表 |
| `GET  /api/pest-records/{id}` | 记录报表：记录头（标明规则版本）+ 虫种明细（阈值快照）+ 现场照片 |
| `POST /api/pest-records/{id}/photos` | multipart 上传现场照片（内容寻址去重；月份冻结后拒收） |
| `GET  /api/pest-tasks?pointId=&status=&responsibleArea=` | 处置任务列表（可按责任区域过滤） |
| `POST /api/pest-tasks/{id}/close` | 关闭处置任务（仅馆内人员；外包人员拒绝） |
| `POST /api/pest-monitor/freeze` / `GET /api/pest-monitor/freezes` | 冻结监测月份（幂等）/ 已冻结月份列表 |
| `GET  /api/pest-ledgers?pointId=` | 虫害台账（不传点位为全馆） |

### 测试

`PestMonitorTest`（独立内存库）覆盖：展厅与库房点位建档（编号唯一/布放日期/坐标/责任区域快照）、
未布片拒交、外包人员提交正常记录不生成任务、同记录虫种重复拒绝、照片内容寻址去重；
更换诱捕片旧片 REPLACED 且旧监测记录仍指向旧片；点位迁移前后坐标留痕；
超阈值生成处置任务并关联责任区域、规则无条目虫种不判定；外包人员关闭任务拒绝、
馆内人员可关闭、重复关闭拒绝；月份冻结后记录与照片拒收、重复冻结幂等、已冻结事实可查；
规则 v2 发布后旧记录结论与阈值快照不改写、新记录按 v2 判定并关联更换后在布诱捕片；
台账全动作留痕（迁移台账含前后坐标、任务台账关联责任区域）。

## 修复工时与设备排程（museum-9）

修复师按工单（修复项目）填报**起止时间、工序和设备**；**设备同一时段不能被两个工单占用**；
**跨天作业按自然日拆分统计**；工时提交后由**负责人审核**，**退回只能补充新版本**；
**设备故障自动生成改期建议但不悄悄改变原排程**；文物（修复对象）置为无效后，
「修复工时与设备占用排程」的**页面、导出、后台统计三端同口径**。

### 工时与排程规则

- **设备档案**：`t_repair_equipment` 编号全库唯一；状态 `ACTIVE/FAULT`，故障只置状态，
  历史排程与工时事实永不删改。
- **工时按版本管理**：修复师每次填报形成一个新版本（`t_repair_timesheet`，
  工单内版本号递增，`uk(project_id, version_no)`），明细行（`t_repair_timesheet_line`）
  固化起止时间/工序/设备，起止可跨天且原样落库。同一工单同时只允许一个待审核版本。
- **负责人审核**：`SUBMITTED → APPROVED / RETURNED`；退回必填意见，退回/通过后版本
  **封存只读**，任何补充只能另开新版本（与会签版本同一口径）。
- **设备互斥**：`SUBMITTED/APPROVED` 版本的明细行参与占用；提交时按设备 id 升序加行锁
  串行化并发，锁内复查——同一设备时段重叠且属于**另一个修复中工单**即拒绝；
  退回版本的行不再占用设备。
- **跨天拆分统计**：原始行不拆库，后台统计按自然日把每行拆到每天，
  按（自然日+工单+设备）汇总工时分钟数。
- **设备故障改期**：故障登记（`t_repair_equipment_fault`，单号 `FT-000001` 序列取号）后
  设备置 `FAULT`（故障中不可填报新工时），并为受影响行（修复中工单的有效行、
  与故障窗口重叠）自动生成改期建议（`t_repair_reschedule_suggestion`，
  `uk(fault_id, timesheet_line_id)` 幂等）：建议起点=预计恢复时刻、时长与原行一致。
  **建议只追加，原排程（工时明细行）绝不被悄悄改写**；采纳/忽略只改建议状态留痕，
  实际改期只能走工时单新版本。故障恢复后设备回到 `ACTIVE`。
- **同口径查询**：页面列表 `GET /api/repair-schedule`、CSV 导出
  `GET /api/repair-schedule/export`、后台按日统计 `GET /api/repair-schedule/summary`
  三端共用同一查询方法与过滤参数。文物 `REVOKED`（置为无效）后其行默认不计入；
  `includeRevoked=true` 时三端同样保留并以 `relicStatus=REVOKED` 标明。
- 工时提交/审核通过/审核退回/改期建议生成全部只追加写入 `t_relic_ledger`。

### 工时与排程接口

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/repair-equipment` / `GET /api/repair-equipment` / `GET /api/repair-equipment/{id}` | 设备建档（编号唯一）/ 列表 / 详情（含故障单与改期建议） |
| `POST /api/repair-projects/{id}/timesheets` | 填报工时（起止/工序/设备；设备同时段不被两个工单占用） |
| `GET  /api/repair-projects/{id}/timesheets` | 工单的工时单版本列表 |
| `GET  /api/timesheets/{id}` | 工时单详情（版本+明细行） |
| `POST /api/timesheets/{id}/review` | 负责人审核：APPROVE 通过 / RETURN 退回（退回必填意见，封存只读） |
| `POST /api/repair-equipment/{id}/faults` | 故障登记：设备置故障中，自动生成改期建议（原排程不变） |
| `POST /api/repair-equipment/{id}/faults/{faultId}/close` | 故障恢复：关闭故障单，设备回到在用 |
| `GET  /api/repair-suggestions?equipmentId=&projectId=&status=` | 改期建议列表 |
| `POST /api/repair-suggestions/{id}/adopt` / `POST /api/repair-suggestions/{id}/dismiss` | 采纳/忽略建议（只留痕，不改写原排程） |
| `GET  /api/repair-schedule?projectId=&equipmentId=&from=&to=&includeRevoked=` | 页面查询：有效工时行与设备占用排程 |
| `GET  /api/repair-schedule/export`（同参数） | CSV 导出：与页面同一行集同一顺序 |
| `GET  /api/repair-schedule/summary`（同参数） | 后台统计：同一行集按自然日拆分汇总 |

### 测试

`RepairTimesheetScheduleTest`（独立内存库）覆盖：设备编号唯一；未开工工单不能填报；
起止时间校验；同一工单同时仅一个待审核版本；设备同时段跨工单占用拒绝、紧邻不重叠允许；
退回必填意见、封存只读、退回后设备占用释放、补充只能另开新版本（版本号递增）；
排程页面只含有效版本行；跨天作业按自然日拆分（4h/24h/9h）与总量一致；
设备故障自动生成改期建议（建议起点=预计恢复时刻）、故障中不可填报、
原排程不被故障/采纳悄悄改写、故障恢复设备回到在用；
文物置为无效（REVOKED）后页面/导出/后台统计默认同口径剔除、
`includeRevoked=true` 三端同样保留并标明 REVOKED、页面行数=导出行数、页面工时总量=统计总量；
台账全动作留痕（TS_SUBMIT/TS_RETURN/TS_APPROVE/RESCHEDULE_SUGGEST）。
