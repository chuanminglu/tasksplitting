# T-UI-06 交付报告 — Todo列表统计展示

- 任务编号：T-UI-06
- 验收标准：TodoBoard 展示"共 X 项，已完成 Y 项"；新增/加载 todo 后统计准确更新；组件测试覆盖统计数字正确性
- 验收模式：PR-only
- 交付日期：2026-09-21

## 一、任务概述

在 TodoBoard 的筛选按钮下方、列表上方加一行统计"共 X 项，已完成 Y 项"。X 为 `todos` 总数、Y 为 `completed === true` 的数量，均基于未筛选的全量数组（与筛选无关），前端计算，`aria-live="polite"` 便于屏幕阅读器。无状态库、无后端聚合接口。

## 二、实际改动的文件（核对表）

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `client/src/App.tsx` | 修改 | `TodoBoard` 在筛选区与列表区之间加 `<p className="todo-stats" aria-live="polite">`，X=`todos.length`、Y=`todos.filter(completed).length`（全量数组） |
| `client/src/styles.css` | 修改 | 新增 `.todo-stats` 类（margin-top 14px、muted 色、0.9rem） |
| `client/src/App.test.tsx` | 修改 | 新增 `describe('TodoBoard stats (T-UI-06)')`，3 个测试 |

未改：服务端任何文件（纯前端派生值）、`Todo` 类型、`formatRelativeTime`、`api`、路由/状态库。

## 三、实际技术选型是否与说明一致

- **完全一致**。React 派生值内联计算，无独立统计 API、无状态库、无额外依赖。统计基于 `todos` 全量数组（不是 `visibleTodos`），因此不受筛选影响——直接落实 DoD 的"统计口径"约束。

## 四、新增的测试用例（DoD 映射）

| 测试 | 映射 DoD |
|---|---|
| `shows total and completed counts for the full todo set` | X=总数、Y=完成数正确（3 项 / 2 完成） |
| `reflects the unfiltered total even when a filter is active` | 统计口径不受筛选影响（切"未完成"仍显示"共2项，已完成1项"） |
| `updates the completed count after a new todo is added` | 新增 todo 后统计准确更新（1/0 → 2/1） |

## 五、测试运行结果（原样节选）

命令：`cd client; npm run typecheck; npx vitest run`

```
> tasksplitting-client@0.1.0 typecheck
> tsc --noEmit

 ✓ src/App.test.tsx (24 tests) 1090ms
   ✓ TodoBoard stats (T-UI-06) > shows total and completed counts for the full todo set 34ms
   ✓ TodoBoard stats (T-UI-06) > reflects the unfiltered total even when a filter is active 50ms
   ✓ TodoBoard stats (T-UI-06) > updates the completed count after a new todo is added 50ms

 Test Files  1 passed (1)
      Tests  24 passed (24)
```

说明：24 个 = 既有 21 + T-UI-06 新增 3。typecheck 无错误输出。

## 六、是否有偏离范围的地方（逐条对照"明确不要做的事"）

- **不改变统计口径（不受筛选影响）**：✅ 统计基于 `todos` 全量数组，且用"切筛选后仍显示全量"的测试显式验证。
- **不新增后端聚合接口**：✅ 无任何服务端改动，纯前端派生值。
- **不引入状态管理库**：✅ 无新依赖。

## 七、交付物与后续事项

- 分支：`t-ui-06-todo-stats`；提交 1（feat）：App.tsx 统计行 + styles.css `.todo-stats` + App.test.tsx 3 个测试 + 本报告
- 待办：阶段 2 下一任务 **T-UI-07 记住用户名**。
