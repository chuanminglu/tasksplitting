# T-UI-02 交付报告 — Todo创建时间相对化展示

> 任务编号：T-UI-02
> 验收标准：无新增AC（阶段2 补充样本）；要点=新创建的 todo 展示合理的相对时间文案
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述
把 `TodoBoard` 待办项原本写死的 `<small>待处理</small>` 替换为基于 `createdAt` 的相对时间文案（"刚刚 / X分钟前 / X小时前 / YYYY-MM-DD"），纯前端、手写最小实现，不引入日期库、不加自动刷新定时器。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）
| 预计文件 | 状态 | 说明 |
|---|---|---|
| `client/src/App.tsx`（必须） | ✅ 已改 | `Todo` 类型新增 `createdAt: string`；新增导出纯函数 `formatRelativeTime(createdAt, now?)`；渲染处 `<small>` 改为 `{formatRelativeTime(todo.createdAt)}` |
| `client/src/App.test.tsx`（DoD 要求单测） | ✅ 已改 | 新增 T-UI-02 测试块 3 条；为兼容 `Todo` 类型补 `createdAt`，同步更新 T-UI-01 用例里的 todo 对象 |
| `client/src/styles.css`（可能） | ⛔ 未改 | 相对时间无需专属样式类，沿用现有 `.todo small` 样式 |

计划外新增文件：无。后端文件一律未动。

## 三、实际技术选型是否与说明一致
- **相对时间计算**：手写最小实现，不引入 `date-fns`/`dayjs` ✅。档位：`<1分钟→刚刚`、`<60分钟→X分钟前`、`<24小时→X小时前`、否则 `YYYY-MM-DD`，与说明一致。
- **不自动刷新**：✅ 未用 `setInterval`；`now` 参数仅在函数内取一次，随组件重渲染重算（符合 [ASSUME]）。
- **不修改后端 `createdAt` 格式**：✅ 只做前端解析与展示。
- **函数签名说明**：`formatRelativeTime(createdAt, now = new Date())` 的第二个 `now` 参数是可选的注入点，默认即"当前时间"，运行时无行为差异；它让"X分钟前/X小时前"档位可以被**确定性**单测覆盖（否则这些档位依赖真实时钟，难以稳定断言），不属额外复杂度或过度设计。

## 四、新增的测试用例（DoD 映射）
| DoD 断言 | 测试用例（`App.test.tsx`） |
|---|---|
| 新创建的 todo 展示"刚刚" | `labels a just-created todo as 刚刚`（渲染一条 `createdAt=now` 的 todo，断言 `<small>` 文案为"刚刚"） |
| `formatRelativeTime` 覆盖至少 3 档（刚刚/X分钟前/X小时前） | `formats minutes/hours/date buckets correctly`（断言 5分钟前 / 3小时前 / 日期三档） |
| typecheck 通过 | `npm run typecheck`（无错误） |
| （补充健壮性）未知时间格式 | `treats an unknown time format as its raw value` |

## 五、测试运行结果（原样节选）
`cd client && npm run typecheck`（`tsc --noEmit`）：无输出、退出码 0（通过）。

`cd client && npx vitest run`：
```
 RUN  v3.2.7 D:/Programs/tasksplitting/client

✓ src/App.test.tsx (13 tests) 707ms

 Test Files  1 passed (1)
      Tests  13 passed (13)
   Start at  17:31:13
   Duration  3.39s (transform 101ms, setup 30ms, collect 293ms, tests 707ms, environment 1.49s, prepare 165ms)
```
13 = 既有登录 4 + T-UI-01 筛选 5 + T-UI-02 相对时间 3 + …（含 T-UI-02 新增 3 条）。

## 六、是否有偏离范围的地方
对照"明确不要做的事"逐条确认：
- 未引入新的日期处理依赖 ✅
- 未新增自动刷新定时器（`setInterval`）✅
- 未修改后端返回的 `createdAt` 字段格式 ✅

## 七、交付物与后续事项
- 分支名：`t-ui-02-relative-time`
- PR 链接：（人工合并后补充）
- 后续任务参考：`formatRelativeTime` 已 `export`，如需在其它组件复用可直接 import；`now` 参数缺省即当前时间，正常渲染调用只需传 `createdAt` 一个参数。
