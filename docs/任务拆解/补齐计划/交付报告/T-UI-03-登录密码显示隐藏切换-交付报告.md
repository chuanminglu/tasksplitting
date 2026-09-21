# T-UI-03 交付报告 — 登录密码显示/隐藏切换

> 任务编号：T-UI-03
> 验收标准：无新增AC（阶段2 补充样本）；DoD=默认 `type="password"` / 点击切换为 `type="text"` / 再次点击恢复 `type="password"` / 组件测试覆盖以上三点
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述
在 `LoginForm` 的密码输入框旁新增一个纯文字切换按钮，点击可在 `type="password"` 与 `type="text"` 之间切换，让登录用户可以按需查看自己输入的密码。不引入图标库、不改动密码框以外的任何表单元素。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）
| 预计文件 | 状态 | 说明 |
|---|---|---|
| `client/src/App.tsx`（必须） | ✅ 已改 | `LoginForm` 新增 `const [showPassword, setShowPassword] = useState(false)`；密码输入框 `type` 绑定 `showPassword ? 'text' : 'password'`；密码行外层包一层 `.password-field` flex 容器，输入框旁新增 `<button type="button">` 切换按钮，文案在"显示"/"隐藏"间随 state 切换 |
| `client/src/styles.css`（可能，INFER） | ✅ 已改 | 新增 `.password-field`（flex 布局，让输入框与按钮同行对齐）与 `.password-toggle`（按钮配色，沿用现有登录配色）两条规则 |
| `client/src/App.test.tsx`（DoD 要求单测） | ✅ 已改 | 新增 T-UI-03 测试块 3 条，覆盖 DoD 三点 |

计划外新增文件：无。后端文件一律未动。

## 三、实际技术选型是否与说明一致
- **不引入图标库**：✅ 切换按钮为纯文字"显示"/"隐藏"，无任何 `svg`/`icon`/第三方图标依赖。
- **默认隐藏**：✅ `showPassword` 初始值 `false`，首屏即 `type="password"`。
- **双向切换**：✅ 按钮 `onClick` 用函数式更新 `setShowPassword((current) => !current)`，两次点击即可来回切换，不依赖外部 state。
- **`type="button"`**：✅ 显式声明，避免在 `<form>` 内被当作提交按钮。
- **未修改密码框以外的表单元素**：✅ 用户名输入框、登录按钮、错误提示结构均保持原样（见第六节核对）。

## 四、新增的测试用例（DoD 映射）
| DoD 断言 | 测试用例（`App.test.tsx`） |
|---|---|
| 默认 `type="password"` | `defaults the password input to type="password"`（渲染登录表单，断言密码输入框 `type` 属性为 `password`） |
| 点击后 `type="text"` | `switches the password input to type="text" when the toggle is clicked`（点击"显示"按钮后断言 `type` 变为 `text`，且按钮文案变为"隐藏"） |
| 再次点击恢复 `type="password"` | `switches back to type="password" on a second click`（连续点击"显示"→"隐藏"后断言 `type` 回到 `password`，按钮文案回到"显示"） |
| typecheck 通过 | `npm run typecheck`（无错误） |

## 五、测试运行结果（原样节选）
`cd client && npm run typecheck`（`tsc --noEmit`）：无输出、退出码 0（通过）。

`cd client && npx vitest run`：
```
 Test Files  1 passed (1)
      Tests  16 passed (16)
   Duration  3.30s
```
16 = 既有 13（登录 + T-UI-01 筛选 + T-UI-02 相对时间，含参数化用例）+ T-UI-03 密码切换 3。

> 备注：首跑时"再次点击恢复"用例失败，原因是把两次点击放进同一个 `act` 块、第一次状态未 flush 就去查询"隐藏"按钮；拆成两个独立 `act` 块后通过。属测试编排问题，非实现问题。

## 六、是否有偏离范围的地方
对照"明确不要做的事"逐条确认：
- 未引入图标库（无 `svg`、无 icon 依赖）✅
- 未修改密码输入框以外的其他表单元素（用户名输入框、登录按钮、错误提示均未改动）✅

## 七、交付物与后续事项
- 分支名：`t-ui-03-password-toggle`
- PR 链接：（合并后补充）
- 后续任务参考：`showPassword` 为 `LoginForm` 局部 state，不污染外层；`.password-field` / `.password-toggle` 两个样式类可被后续登录相关样式（如深色模式 T-UI-04）直接复用。
