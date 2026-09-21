# T-UI-07 交付报告 — 记住用户名

- 任务编号：T-UI-07
- 验收标准：登录成功后用户名被记住，刷新页面重新填充（不记住密码）
- 验收模式：PR-only
- 交付日期：2026-09-21

## 一、任务概述

`LoginForm` 每次挂载 `username` 都是空字符串。本任务让登录成功后把用户名写入 `localStorage`（键名 `tasksplitting-remembered-username`），下次打开登录表单自动填充。`username` 改为懒初始化从该键读取初始值（无记录则空串）。**不记住密码**（明文入 `localStorage` 属安全排除项，AC 亦未要求）。与 `rememberMe`（控制会话 token 有效期）完全独立，不共用 state/UI。

## 二、实际改动的文件（核对表）

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `client/src/App.tsx` | 修改 | 新增 `REMEMBERED_USERNAME_KEY` 常量；`username` state 改懒初始化从 `localStorage` 读取；登录成功（`onLogin` 前）写入该键（仅当 `username` 非空） |
| `client/src/App.test.tsx` | 修改 | 新增 `describe('LoginForm remembered username (T-UI-07)')`，3 个测试 |

未改：服务端任何文件、`rememberMe` 相关逻辑、密码字段、`api`、样式、路由/状态库。

## 三、实际技术选型是否与说明一致

- **完全一致**。按规格：`username` 初始值 `window.localStorage.getItem(REMEMBERED_USERNAME_KEY) ?? ''`（懒初始化函数，避免 SSR/重复求值）；登录成功时（`onLogin(data.token)` 之前）`window.localStorage.setItem(REMEMBERED_USERNAME_KEY, username)`。密码全程不持久化。键名 `tasksplitting-remembered-username` 取自 `[ASSUME]`。
- 与 `rememberMe` 零耦合：独立 key、独立逻辑，未触碰 `rememberMe` state 或 checkbox。

## 四、新增的测试用例（DoD 映射）

| 测试 | 映射 DoD |
|---|---|
| `stores the submitted username in localStorage after a successful login` | 登录成功后 localStorage 存在记住的用户名，值与刚提交的一致（`alice`） |
| `prefills the username input from localStorage on remount` | 重新挂载（模拟刷新）后用户名输入框自动填充为上次记住的值（`bob`） |
| `never prefills the password input` | 密码输入框任何情况都不会被自动填充非空值（即使存在记住的用户名，密码仍为空串） |

## 五、测试运行结果（原样节选）

命令：`cd client; npm run typecheck; npx vitest run`

```
> tasksplitting-client@0.1.0 typecheck
> tsc --noEmit

 ✓ src/App.test.tsx (27 tests) 1055ms

 Test Files  1 passed (1)
      Tests  27 passed (27)
```

说明：27 个 = 既有 24 + T-UI-07 新增 3。typecheck 无错误输出。

## 六、是否有偏离范围的地方（逐条对照"明确不要做的事"）

- **不要记住密码**：✅ 密码字段初始值始终为 `''`，代码与测试均未对密码做任何持久化。
- **不要跟 `rememberMe` 字段共用 state/UI 控件**：✅ 独立 `REMEMBERED_USERNAME_KEY` 与独立填充逻辑，`rememberMe` 原逻辑未改动。
- **不要修改后端任何逻辑**：✅ 无任何服务端改动。

## 七、交付物与后续事项

- 分支：`t-ui-07-remember-username`；提交 1（feat）：App.tsx 懒初始化 + 登录成功写入 + App.test.tsx 3 个测试 + 本报告
- 待办：阶段 2 下一任务 **T-UI-08 退出确认**。
