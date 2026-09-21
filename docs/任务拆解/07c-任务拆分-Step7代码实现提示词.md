# 订单案例代码实现提示词

本文件仅保存 Step 7 生成的 Subagent 代码实现提示词，不执行代码开发。

文件路径已在 Step 6.8 规划完成（见 `08-执行规约/文件规划.md` 与 `specs.json` 中 `binding_status=planned_pending_verification` 的 `relatedFiles`/`testRef`），不再要求 subagent 在开发阶段临时确定或回填路径。subagent 的职责是按规划路径实现，并在真实实现偏离规划时显式报告偏差。

## TASK-ORDER-001：创建订单

```text
任务：实现 TASK-ORDER-001（US-ORDER-001 创建订单）。
需求规则：BR-ORDER-001、BR-ORDER-002、BR-ORDER-003。
执行规约：spec-order-create-required-v1、spec-order-create-idempotency-v1、spec-order-create-initial-status-v1。

先阅读 docs/order-flow-case/04-用户故事.md、05-软件设计.md、06-业务规则.md、08-执行规约/specs.json、08-执行规约/文件规划.md。
已规划路径（均以仓库根为基准，标注"新增"的文件当前不存在，需要新建）：
- spec-order-create-required-v1 → relatedFiles: spec-verifier-poc/src/order/createOrder.ts, spec-verifier-poc/src/order/orderValidation.ts（新增）；testRef: spec-verifier-poc/tests/order-create-required.spec.ts（新增）
- spec-order-create-idempotency-v1 → relatedFiles: spec-verifier-poc/src/order/createOrder.ts, spec-verifier-poc/src/order/orderCreationKeys.ts（新增）；testRef: spec-verifier-poc/tests/order-create-idempotency.spec.ts（新增）
- spec-order-create-initial-status-v1 → relatedFiles: spec-verifier-poc/src/order/createOrder.ts, spec-verifier-poc/src/order/orderState.ts（新增）；testRef: spec-verifier-poc/tests/order-create-initial-status.spec.ts（新增）
按上述规划路径实现；不要另行臆造路径。`spec-verifier-poc/src/order/createOrder.ts` 现有代码是 Step 6.5 最小闭环骨架，其配套测试 `tests/r03-idempotency.spec.ts` 引用的 `InMemoryOrderService` 类与当前函数式导出不一致（既有偏差），实现前需先确认延续函数式 API 还是切换为类式 API，并在报告中说明选择。
若实现中发现必须偏离规划路径（需要新增计划外文件、拆分模块等），必须在报告中显式说明偏差原因，不得直接写入计划外路径后当作已完成收尾。
允许范围：创建订单输入校验、幂等处理（仅内容一致重试分支）、商品明细写入、初始状态和对应测试。
禁止范围：订单修改、BR-ORDER-007 权限策略、BR-ORDER-008 内容冲突响应策略、无关业务和规则库语义改写。

实现并补充 BDD 对应测试。每次写入后等待 Hook；若返回 FAIL，依据 violated_spec_ids 和 evidence 修复后重试。
完成后报告实际 changed_files、测试命令和结果、checked_spec_ids、规划路径与实际路径的一致性核对结果（含任何偏差说明）以及未决风险。真实变更与规划路径一致后，把 binding_status 由 planned_pending_verification 确认为 bound；不得臆造或提前声称规则通过。
```

## TASK-ORDER-002：修改订单

```text
任务：实现 TASK-ORDER-002（US-ORDER-002 修改订单）。
需求规则：BR-ORDER-004、BR-ORDER-005、BR-ORDER-006。
执行规约：spec-order-update-pending-only-v1、spec-order-update-terminal-immutable-v1、spec-order-update-audit-v1。

先阅读 docs/order-flow-case/04-用户故事.md、05-软件设计.md、06-业务规则.md、08-执行规约/specs.json、08-执行规约/文件规划.md。
已规划路径（均以仓库根为基准，标注"新增"的文件当前不存在，需要新建）：
- spec-order-update-pending-only-v1 → relatedFiles: spec-verifier-poc/src/order/updateOrder.ts（新增）, spec-verifier-poc/src/order/orderState.ts（新增）；testRef: spec-verifier-poc/tests/order-update-pending-only.spec.ts（新增）
- spec-order-update-terminal-immutable-v1 → relatedFiles: spec-verifier-poc/src/order/updateOrder.ts, spec-verifier-poc/src/order/orderState.ts；testRef: spec-verifier-poc/tests/order-update-terminal-immutable.spec.ts（新增）
- spec-order-update-audit-v1 → relatedFiles: spec-verifier-poc/src/order/updateOrder.ts, spec-verifier-poc/src/order/orderAudit.ts（新增）；testRef: spec-verifier-poc/tests/order-update-audit.spec.ts（新增）
按上述规划路径实现；不要另行臆造路径。`orderState.ts` 与 TASK-ORDER-001 共用，实现前先确认该文件是否已由 TASK-ORDER-001 创建，避免重复定义状态枚举。
若实现中发现必须偏离规划路径（需要新增计划外文件、拆分模块等），必须在报告中显式说明偏差原因，不得直接写入计划外路径后当作已完成收尾。
允许范围：待确认订单修改、终态拒绝、修改人/时间审计和对应测试。
禁止范围：创建订单幂等语义、BR-ORDER-007 权限策略、无关业务和规则库语义改写。

实现并补充 BDD 对应测试。每次写入后等待 Hook；若返回 FAIL，依据 violated_spec_ids 和 evidence 修复后重试。
完成后报告实际 changed_files、测试命令和结果、checked_spec_ids、规划路径与实际路径的一致性核对结果（含任何偏差说明）以及未决风险。真实变更与规划路径一致后，把 binding_status 由 planned_pending_verification 确认为 bound；不得臆造或提前声称规则通过。
```
