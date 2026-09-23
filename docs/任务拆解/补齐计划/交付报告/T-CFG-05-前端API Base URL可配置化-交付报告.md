# T-CFG-05 前端 API Base URL 可配置化 —— 交付报告

> 分支 `t-cfg-05-api-base-url-env`（base `af08476`，即 PR #28 合入后 main HEAD）
> PR #29 · 交付日期 2026-07-24 · 执行：`tsc --noEmit` + `npx vitest run`

---

## 一、任务概述

按补齐计划 T-CFG-05：在 `client/src/App.tsx` 顶层加 `const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';`，将所有三处 `fetch` 的 URL 加 `${API_BASE}` 前缀；新建 `client/.env.example` 作为模板（留空默认走 Vite dev-server proxy，可设为完整 origin 指向其他部署）。**不**引入 axios / 统一 API 客户端；**不**改 `vite.config.ts` 的 proxy 段。

## 二、预计文件 vs 实际改动

| 文件 | 预期 | 实际 | 备注 |
| --- | --- | --- | --- |
| `client/src/App.tsx` | 加 `API_BASE` 常量 + 三处 URL 前缀 | ✅ 已加（第 46 行 `const API_BASE = ...`；第 93 / 153 / 166 行三处 `fetch` 全部改用 `` `${API_BASE}/...` ``） | 三处覆盖 login POST / todos GET / todos POST |
| `client/.env.example` | 新建，含示例值与注释 | ✅ 已创建（中文注释说明默认留空、示例 `https://api.example.com`、结尾 `VITE_API_BASE_URL=` 空值） | 按补充包要求 |
| `client/src/vite-env.d.ts` | **spec 未列**，但 **typecheck 必需** | ✅ 已创建（声明 `ImportMetaEnv.VITE_API_BASE_URL?: string` 与 `ImportMeta.env` 类型） | **偏差**：原 `tsconfig.json` 的 `include: ["src"]`，`tsc --noEmit` 报 `TS2339 Property 'env' does not exist on type 'ImportMeta'`；在 `client/` 根下建 `vite-env.d.ts` 无效（被 exclude 在 include 外），只能放进 `client/src/` |
| `client/src/App.apiBase.test.tsx` | 新增 2 个测试 | ✅ 已创建 | 见下节 |

**说明**：`vite-env.d.ts` 是 `tsc` 类型系统的硬性要求（Vite 官方 `create-vite` 模板也在 `src/` 里放此文件），不算偷工减料，而是补齐计划漏列的必要支撑文件。

## 三、技术选型与方案一致性

- **未引入 axios 或任何统一 API 客户端**——仍用原生 `fetch`，仅在 URL 前加 `${API_BASE}` 字符串拼接。✅
- **未改 `vite.config.ts`**——proxy 段原样保留。✅
- **`API_BASE` 为模块顶层 `const`**——与 spec 指定的 `const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';` 完全一致（曾短暂试 `function apiBase()` 便于 stub，后 revert 回 spec 原样）。✅
- **环境读取方式**：沿用 `import.meta.env`（Vite 官方机制），未用 `process.env` 或其他方案。✅

## 四、新增测试与 DoD 映射

`client/src/App.apiBase.test.tsx` 共 2 个用例，全部通过：

| 测试 | 覆盖的 DoD |
| --- | --- |
| `sends relative /api/auth/login when the variable is not set` | 默认未设时请求 URL = `/api/auth/login`（相对路径，走 proxy） |
| `prefixes all fetch URLs when VITE_API_BASE_URL is set` | 设 `VITE_API_BASE_URL=https://api.example.com` 后 URL = `https://api.example.com/api/auth/login` |

**stub 方式说明**：`vi.stubEnv` 修改的是 `process.env`，对 `import.meta.env`（Vite 注入）不直接生效；因此每个用例采用 `vi.stubEnv(...) → vi.resetModules() → await import('./App')`，强制模块重求值，让顶层 `const API_BASE` 重新读 env。实测通过。

**既有 29 个测试**：`src/App.test.tsx` 原封未动，全部仍通过——证明既有断言对前缀无感知（它们用 `String(input).includes('/api/auth/login')` 匹配，前缀不会破坏子串匹配）。✅

## 五、原始命令输出节选

```
$ npm run typecheck   # 根目录
> tasksplitting-client@0.1.0 typecheck
> tsc --noEmit
(无输出 = 0 error)

$ npx vitest run      # client 目录
 RUN  v3.2.7 D:/Programs/tasksplitting/client
  ✓ src/App.apiBase.test.tsx (2 tests) 351ms
  ✓ src/App.test.tsx (29 tests) 1126ms
 Test Files  2 passed (2)
      Tests  31 passed (31)
```

## 六、"不要做的事"逐条核对

| spec 明确要求 | 实际 |
| --- | --- |
| 不引入 axios 或统一 API 客户端 | ✅ 未引入，仍为原生 fetch |
| 不改 `vite.config.ts` 的 proxy 段 | ✅ 未动 `client/vite.config.ts` |
| 不修改现有 29 个测试 | ✅ `App.test.tsx` 一行未改，29 个仍通过 |

## 七、交付物

- 分支：`t-cfg-05-api-base-url-env`
- 变更文件：
  - `client/src/App.tsx`（+`API_BASE` 常量，3 处 URL 前缀）
  - `client/src/vite-env.d.ts`（新增，ImportMetaEnv 类型声明）
  - `client/src/App.apiBase.test.tsx`（新增，2 个 env 前缀测试）
  - `client/.env.example`（新增，模板 + 注释）
- 验证：`tsc --noEmit` 0 error；`npx vitest run` 31/31 通过（29 既有 + 2 新增）
- 下一任务：**阶段4a T00201**（`forgot-password-request`）
