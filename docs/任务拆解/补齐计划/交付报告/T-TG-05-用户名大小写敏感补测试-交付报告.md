# T-TG-05 用户名大小写敏感补测试 交付报告

**阶段**：阶段 1 · 5/8　**分支**：`t-tg-05-username-case-test`　**基线**：`main`（PR #10 合并后 `8736e9c`）

## 1. 任务概述

为「用户名大小写敏感」补测试：同一用户名 `casecheck-<ns>`（小写）注册后，登录其大小写变体 `CaseCheck-<ns>`（混合）与 `CASECHECK-<ns>`（全大写），均期望 401 `USER_NOT_FOUND`（而非 500，也非 INVALID_PASSWORD）。

## 2. 实际改动的文件

| 文件 | 改动 |
|---|---|
| `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java` | 新增 `loginWithDifferentCaseUsernameTreatsAsDifferentAccount`；新增私有辅助 `assertUserNotFound(username)` |

## 3. 技术选型

复用 `AuthIntegrationTest` 现有 `users`/`mvc`/`objectMapper` 基础设施与 `users.create(username, bcrypt)` 直插方式；大小写变体由 `substring` 拼前缀得到，避免硬编码固定用户名导致的并发/重复风险。

## 4. 新增测试用例

- `loginWithDifferentCaseUsernameTreatsAsDifferentAccount`：`users.create("casecheck-<ns>", …)` → `POST /api/auth/login`（`CaseCheck-<ns>` / `CASECHECK-<ns>`）→ 断言 401 且 `error.code=USER_NOT_FOUND`。
- 辅助 `assertUserNotFound(String username)`：发送登录请求并断言 401 + `USER_NOT_FOUND`。

## 5. 测试运行结果

`cd server; mvn test`（Maven 3.9.9，Java 17.0.15）：

| 测试类 | 结果 |
|---|---|
| AuthDisabledIntegrationTest | 1 |
| **AuthIntegrationTest** | **13（新增 1）** |
| LoginEventIntegrationTest | 5 |
| LoginEventResilienceTest | 2 |
| TodoAuthIntegrationTest | 8 |
| TodoValidationTest | 2 |

**合计：Tests run: 31, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS。**

## 6. 偏离范围

无。仅新增 1 个测试方法与 1 个私有辅助方法，未触碰任何主代码。

## 7. 交付物与后续事项

- 交付物：本报告、`AuthIntegrationTest` 新增用例、分支 `t-tg-05-username-case-test`、PR。
- 待人工确认：验收后合并 PR，继续 T-TG-06「Todo 顺序测试」。
