# T-UI-04 交付报告 — 深色模式切换

> 任务编号：T-UI-04
> 验收标准：无新增AC（阶段2 补充样本）；DoD=默认浅色 / 点击切换按钮后立即变为深色（背景/文字色可观察变化）/ 组件测试验证切换后 localStorage 主题值被更新 + 重新挂载能从 localStorage 恢复
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述
为单页应用新增手动深色/浅色主题切换：`styles.css` 引入 CSS 变量并把既有浅色配色改挂到变量上，新增 `[data-theme='dark']` 深色覆盖集；`App` 顶层新增 `theme` state（`'light' | 'dark'`），挂载时从 `localStorage`（键 `tasksplitting-theme`）读初始值，切换时同步写 `document.documentElement.dataset.theme` 与 `localStorage`；页面右上角新增一个切换按钮。不实现"跟随系统偏好"，不引入 CSS-in-JS 或第三方主题库。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）
| 预计文件 | 状态 | 说明 |
|---|---|---|
| `client/src/App.tsx`（必须） | ✅ 已改 | 新增 `type Theme`、`THEME_STORAGE_KEY='tasksplitting-theme'`、纯函数 `readStoredTheme()`（`localStorage` 读不到或非 `'dark'` 一律回退 `'light'`）；`App` 顶层新增 `theme` state 与 `useEffect`（写 `dataset.theme` + `localStorage`）；登录/工作台外层包一层 fragment，右上角渲染 `.theme-toggle` 切换按钮（`aria-label="切换主题"`，文案随主题显示"深色"/"浅色"） |
| `client/src/styles.css`（必须） | ✅ 已改 | `:root` 新增 11 个 `--color-*` 变量（浅色值=原硬编码色），并让 `color`/`background` 挂到 `--color-text`/`--color-bg`；各选择器（`.eyebrow`/`.intro`/`input`/`button`/`.todo-filter`/`.todo`/`.password-toggle`/`.logout-button` 等）改用 `var(--color-*)`；新增 `[data-theme='dark']` 深色变量集；新增 `.theme-toggle`（右上角固定定位）样式 |
| `client/src/App.test.tsx`（DoD 要求单测） | ✅ 已改 | 新增 T-UI-04 测试块 3 条覆盖 DoD 三点；`afterEach` 追加 `delete documentElement.dataset.theme` 与 `localStorage.clear()`，`beforeEach`（T-UI-04 块内）清空 storage，保证用例隔离 |

计划外新增文件：无。后端文件一律未动。

## 三、实际技术选型是否与说明一致
- **CSS 变量方案**：✅ `:root` 定义浅色变量、`[data-theme='dark']` 覆盖，`document.documentElement.dataset.theme` 驱动，无 CSS-in-JS、无第三方主题库。
- **持久化 key**：✅ `localStorage` 键名 `tasksplitting-theme`（`[ASSUME]` 采纳）。
- **默认浅色**：✅ `readStoredTheme()` 无记录或非法值均回退 `'light'`。
- **立即生效**：✅ `useEffect` 在 `theme` 变化时同步写 `dataset.theme`，浏览器即时按变量重绘，无异步延迟。
- **不实现跟随系统偏好**：✅ 未使用 `prefers-color-scheme`，仅手动按钮。
- **配色覆盖**：✅ 原文件中用到的 11 个颜色值全部变量化，深色集逐一对应覆盖。

## 四、新增的测试用例（DoD 映射）
| DoD 断言 | 测试用例（`App.test.tsx`） |
|---|---|
| 默认浅色主题 | `defaults to the light theme`（首屏断言 `dataset.theme==='light'` 且 `localStorage` 写入 `'light'`） |
| 点击切换按钮后立即变为深色 | `switches to dark and persists the choice when the toggle is clicked`（点击"切换主题"后断言 `dataset.theme==='dark'`） |
| 切换后 localStorage 中主题值被更新 | 同上（断言 `localStorage['tasksplitting-theme']==='dark'`） |
| 重新挂载组件时能从 localStorage 恢复上次选择 | `restores the stored theme on remount`（预置 `localStorage='dark'` 后 `render(<App/>)`，断言 `dataset.theme==='dark'`） |
| typecheck 通过 | `npm run typecheck`（无错误） |

## 五、测试运行结果（原样节选）
`cd client && npm run typecheck`（`tsc --noEmit`）：无输出、退出码 0（通过）。

`cd client && npx vitest run`：
```
 Test Files  1 passed (1)
      Tests  19 passed (19)
   Duration  3.35s
```
19 = 既有 16（登录 + T-UI-01 筛选 + T-UI-02 相对时间 + T-UI-03 密码切换，含参数化用例）+ T-UI-04 主题切换 3。

> 备注：首跑"切换为深色"用例失败，原因是按钮带 `aria-label="切换主题"`，其 accessible name 为"切换主题"而非可见文本"深色"，按可见文本查询不到；改用 `getByRole('button', { name: '切换主题' })` 后通过。属测试查询方式问题，非实现问题。

## 六、是否有偏离范围的地方
对照"明确不要做的事"逐条确认：
- 未实现"跟随系统偏好"（无 `prefers-color-scheme` 媒体查询），仅手动切换按钮 ✅
- 未引入 CSS-in-JS 方案或第三方主题库（纯 CSS 变量 + `dataset` + 原生 `localStorage`）✅

## 七、交付物与后续事项
- 分支名：`t-ui-04-dark-mode`
- PR 链接：（合并后补充）
- 后续任务参考：主题由 `App` 顶层单一 state 驱动，登录页与工作台共享同一套变量，后续新增页面无需各自处理主题；`--color-*` 变量集可作为后续 UI 微调（如 T-UI-05~08）的统一入口。
