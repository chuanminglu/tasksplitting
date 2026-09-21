# T-UI-08 交付报告 — 退出登录二次确认

- 任务编号：T-UI-08
- 验收标准：点击"退出登录"先弹出确认，确认后才真正退出，取消则保持登录状态
- 验收模式：PR-only
- 交付日期：2026-09-21

## 一、任务概述

`TodoBoard` 的"退出登录"按钮当前点击即直接调用 `onLogout()`，无任何确认步骤。本任务在该按钮的 `onClick` 中加一道原生 `window.confirm('确定要退出登录吗？')` 关卡：返回 `true` 才调用原有 `onLogout()`，返回 `false` 不做任何操作，保持当前登录状态与 token 不变。**不引入自定义 Modal/弹窗库**，`onLogout` 自身逻辑（清 token）不变，只是新增了一道确认关卡。

## 二、实际改动的文件（核对表）

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `client/src/App.tsx` | 修改 | "退出登录"按钮 `onClick` 由 `onLogout` 改为 `() => { if (window.confirm('确定要退出登录吗？')) { onLogout(); } }` |
| `client/src/App.test.tsx` | 修改 | 新增 `describe('logout confirmation (T-UI-08)')`，2 个测试（mock `window.confirm` 返回 false / true 两个分支） |

未改：`onLogout` 函数本身、服务端任何文件、样式、路由/状态库、无新增依赖。

## 三、实际技术选型是否与说明一致

- **完全一致**。按规格用浏览器原生 `window.confirm()`，未引入任何自定义 Modal 组件或弹窗库（仓库本无任何弹窗依赖）。确认文案 `确定要退出登录吗？` 取自 `[ASSUME]`。`onLogout`（`setToken(null)`）逻辑未改动，仅在调用前加了确认判断。

## 四、新增的测试用例（DoD 映射）

| 测试 | 映射 DoD |
|---|---|
| `keeps the logged-in board when the user cancels the confirm` | 取消（confirm→false）不执行退出逻辑，登录状态保持（`todo-list` 仍在、无用户名输入框）；并断言确认文案 |
| `logs out (clearing the token) when the user confirms` | 确认（confirm→true）执行原有退出逻辑，token 清空（`todo-list` 消失、回到登录表单）；并断言确认文案 |

## 五、测试运行结果（原样节选）

命令：`cd client; npm run typecheck; npx vitest run`

```
> tasksplitting-client@0.1.0 typecheck
> tsc --noEmit

 ✓ src/App.test.tsx (29 tests) 1207ms

 Test Files  1 passed (1)
      Tests  29 passed (29)
```

说明：29 个 = 既有 27 + T-UI-08 新增 2。typecheck 无错误输出。两个分支均 mock `window.confirm` 返回值覆盖（`vi.spyOn(window, 'confirm')`，`afterEach` 的 `vi.restoreAllMocks()` 自动还原）。

## 六、是否有偏离范围的地方（逐条对照"明确不要做的事"）

- **不要引入自定义 Modal 组件或弹窗库**：✅ 使用原生 `window.confirm()`，无新依赖。
- **不要修改 `onLogout` 函数本身的逻辑**：✅ `onLogout` 及其调用（清 token）保持原样，仅在其外层新增确认关卡。

## 七、交付物与后续事项

- 分支：`t-ui-08-logout-confirm`；提交 1（feat）：App.tsx 确认关卡 + App.test.tsx 2 个测试 + 本报告
- 阶段 2（T-UI-01~08）全部完成。待办：进入 **阶段 3 T-CFG-01**。
