# T-UI-01 交付报告 — Todo完成状态筛选

> 任务编号：T-UI-01
> 验收标准：无新增AC（阶段2 补充样本）；要点=筛选切换后展示的 Todo 集合正确，且切换不产生新的 API 请求
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述
在 `TodoBoard` 工作台新增"全部 / 未完成 / 已完成"三态筛选，纯前端本地过滤已加载的 todos 列表；切换筛选只改本地状态，不重新发起 `/api/todos` 请求。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）
| 预计文件 | 状态 | 说明 |
|---|---|---|
| `client/src/App.tsx`（必须） | ✅ 已改 | `TodoBoard` 内新增 `filter` 状态、3 个筛选按钮、渲染前按 `filter` 过滤；空态判定从 `todos.length===0` 改为 `visibleTodos.length===0` |
| `client/src/styles.css`（可能） | ✅ 已改 | 新增 `.todo-filters` / `.todo-filter` / `.todo-filter.active` 三个类，选中态用主色填充以视觉区分 |
| `client/src/App.test.tsx`（ASSUME：归入此文件） | ✅ 已改 | 新增 5 条组件测试覆盖 4 条 DoD（见第四节映射） |

计划外新增文件：无。后端文件一律未动。

## 三、实际技术选型是否与说明一致
- **筛选实现方式**：与说明一致——`useState<'all'|'active'|'completed'>('all')` 管理筛选态 + 3 个按钮切换 + 渲染时 `.filter()` 得展示列表。✅
- **不发起新网络请求**：✅ 切换只调用 `setFilter`，`useEffect` 依赖仍为 `[token]`，不触发 fetch。
- **不引入路由库 / 状态管理库**：✅ 维持现有纯 hooks 架构，仅用了已存在的 `react` / `@testing-library/react` / `vitest`。

## 四、新增的测试用例（DoD 映射）
| DoD 断言 | 测试用例（`App.test.tsx`） |
|---|---|
| 默认展示全部 todo | `shows all todos by default` |
| 点"未完成"只展示 `completed === false` | `shows only incomplete todos when "未完成" is selected` |
| 点"已完成"只展示 `completed === true` | `shows only completed todos when "已完成" is selected` |
| 切换筛选不触发新的 `fetch('/api/todos')` | `does not refetch /api/todos when switching filters`（断言筛选前后 `/api/todos` 调用次数不变） |
| 筛选后结果为空时空态文案仍生效 | `shows the empty state when the active filter leaves no visible todos` |

## 五、测试运行结果（原样节选）
`cd client && npm run typecheck`（`tsc --noEmit`）：无输出、退出码 0（通过）。

`cd client && npx vitest run`：
```
 RUN  v3.2.7 D:/Programs/tasksplitting/client

✓ src/App.test.tsx (10 tests) 623ms

 Test Files  1 passed (1)
      Tests  10 passed (10)
   Start at  17:21:54
   Duration  2.74s (transform 97ms, setup 21ms, collect 324ms, tests 623ms, environment 1.07s, prepare 167ms)
```
10 = 既有 5 条登录/工作台测试 + T-UI-01 新增 5 条。

## 六、是否有偏离范围的地方
对照"明确不要做的事"逐条确认：
- 未修改后端 `TodoController` / `TodoRepository` ✅
- 未新增"标记完成"等改变 `completed` 状态的交互 ✅（待办项 `small` 文案保持原样"待处理"，未引入按 `completed` 分化的展示）
- 未引入路由库把筛选态放进 URL ✅

## 七、交付物与后续事项
- 分支名：`t-ui-01-todo-filter`
- PR 链接：（人工合并后补充）
- 后续任务参考：筛选态仅存在于 `TodoBoard` 本地，未写入任何持久化/URL；T-UI-06（Todo 统计）若需"共 X 项"，应基于**未筛选的完整 `todos`** 计算，与本任务的 `visibleTodos` 语义区分，避免统计口径被筛选态影响。
