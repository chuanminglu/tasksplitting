# T-UI-05 交付报告 — 登录loading态输入框disable

> 任务编号：T-UI-05
> 验收标准：无新增AC（阶段2 补充样本）；DoD=submitting 期间用户名/密码输入框均 disabled / 请求结束（成功或失败）后恢复可编辑 / 组件测试模拟提交过程中检查输入框 disabled 状态
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述
登录请求进行中时，`LoginForm` 目前只 disable 了提交按钮，用户名/密码两个 `<input>` 仍可编辑，属交互一致性缺陷。本任务把既有的 `submitting` 状态同时绑定到这两个输入框的 `disabled` 属性上，请求结束（成功或失败）后自动恢复。不改动"记住我"复选框与提交按钮已有的 disable 逻辑。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）
| 预计文件 | 状态 | 说明 |
|---|---|---|
| `client/src/App.tsx`（必须） | ✅ 已改 | `LoginForm` 的用户名 `<input>` 与密码 `<input>` 各新增 `disabled={submitting}`；复用既有 `submitting` 状态，无新增 state 或依赖 |
| `client/src/App.test.tsx`（DoD 要求单测） | ✅ 已改 | 新增 T-UI-05 测试块 2 条：进行中 disabled、请求结束恢复 |

计划外新增文件：无。后端文件一律未动。"可能改动"标注为无，实际也未改 `styles.css` 等其它文件。

## 三、实际技术选型是否与说明一致
- **直接绑定既有 `submitting`**：✅ 用户名/密码两个输入框均 `disabled={submitting}`，与说明"无开放性决策，直接给两个输入框绑定既有的 submitting 状态"完全一致，无新增 state 或依赖。
- **请求结束恢复**：✅ `submitting` 在 `submit()` 的 `finally` 中置回 `false`，成功/失败均恢复，输入框随之解禁。

## 四、新增的测试用例（DoD 映射）
| DoD 断言 | 测试用例（`App.test.tsx`） |
|---|---|
| submitting 期间用户名/密码输入框均 disabled | `disables the username and password inputs while a submit is in flight`（用可控 `fetch` promise 让登录请求挂起，submit 后断言两个输入框 `disabled` 属性存在） |
| 请求结束后恢复可编辑 | `restores the inputs to enabled after a failed submit`（用 401 `INVALID_PASSWORD` 模拟失败提交，断言提交前后两个输入框 `disabled` 属性均为 `null`，即始终可编辑） |
| typecheck 通过 | `npm run typecheck`（无错误） |

> 测试要点：进行中用例用 `new Promise` 挂起 `fetch` 制造"提交进行中"窗口，断言后 resolve 成 200 触发登录完成，避免悬挂 promise；因成功登录会卸载表单，"恢复可编辑"由失败用例（表单保留）覆盖，两者合起来对应 DoD 的"提交中 disabled / 结束后恢复"。

## 五、测试运行结果（原样节选）
`cd client && npm run typecheck`（`tsc --noEmit`）：无输出、退出码 0（通过）。

`cd client && npx vitest run`：
```
 Test Files  1 passed (1)
      Tests  21 passed (21)
   Duration  3.87s
```
21 = 既有 19（登录 + T-UI-01 筛选 + T-UI-02 相对时间 + T-UI-03 密码切换 + T-UI-04 主题切换，含参数化用例）+ T-UI-05 提交禁用 2。

> 备注：首跑出现两处问题并均修复——(1) `getByPlaceholderText` 返回 `HTMLElement` 无 `disabled` 属性导致 typecheck 失败，改用 `getAttribute('disabled')` 断言；(2) 进行中用例的 mock 对所有请求返回同一已读 Response，登录成功卸载表单后 `TodoBoard` 再取 `/api/todos` 触发 `Body is unusable` unhandled rejection，按 URL 分支返回独立 Response 后消除。均属测试编排问题，非实现问题。

## 六、是否有偏离范围的地方
对照"明确不要做的事"逐条确认：
- 未修改"记住我"复选框的 disable 行为 ✅
- 未修改提交按钮已有的 disable 逻辑（仍为 `<button type="submit" disabled={submitting}>`）✅

## 七、交付物与后续事项
- 分支名：`t-ui-05-login-input-disable`
- PR 链接：（合并后补充）
- 后续任务参考：`submitting` 仍为 `LoginForm` 局部状态，未引入新状态；后续如需在请求期间禁用其它登录区控件（如"记住我"），可沿用同一 `submitting` 判定，保持交互一致。
