# T-TG-05 交付报告 — 用户名大小写敏感补测试

> 任务编号：T-TG-05
> 验收标准：无新增AC，验证既有生产代码行为——大小写不同的用户名视为不同账号，登录走 `USER_NOT_FOUND` 分支（锁定 `findByUsername` 精确大小写匹配的现状）
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述

为 `UserRepository.findByUsername` 当前「精确匹配、区分大小写」的行为补测试：SQLite `TEXT` 比较默认区分大小写（未用 `COLLATE NOCASE`），注册小写用户名后用其混合/全大写变体登录应查不到用户，返回 401 + `USER_NOT_FOUND`（非 500，也非 `INVALID_PASSWORD`）。本任务只锁定现状，不判断「是否应该」区分大小写。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）

| 预计文件 | 状态 | 说明 |
|---|---|---|
| `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例） | ✅ 已修改 | 新增 1 个用例 + 1 个私有辅助 |

"可能改动"清单：任务说明标注「无」，实际未改动任何其他文件（未改 `UserRepository.findByUsername` 的 SQL 或匹配方式，未改 `AuthService`）。

## 三、实际技术选型是否与说明一致

- 无开放性决策。直接复用 `AuthIntegrationTest` 既有 `users`/`mvc`/`objectMapper` 基础设施与 `users.create(username, bcryptHash)` 直插方式。
- 用户名用 `casecheck-<nanoTime>` 生成，变体由 `substring` 切出数字后缀再拼 `CaseCheck-`/`CASECHECK-` 前缀得到，避免硬编码固定用户名（如 `alice`）在并发/重复运行时互相污染。

## 四、新增的测试用例

| 测试位置 | 用例名 | 覆盖的DoD点 |
|---|---|---|
| `AuthIntegrationTest` | `loginWithDifferentCaseUsernameTreatsAsDifferentAccount` | `[FACT]` `CaseCheck-<ns>`（混合）登录 → 401 + `USER_NOT_FOUND`；`[FACT]` `CASECHECK-<ns>`（全大写）登录 → 401 + `USER_NOT_FOUND` |
| `AuthIntegrationTest`（私有辅助） | `assertUserNotFound(username)` | 非独立 DoD 点，供上述用例发送登录请求并断言 401 + `USER_NOT_FOUND` |

## 五、测试运行结果（原样节选）

命令：`cd d:\Programs\tasksplitting\server && mvn test`

```text
Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthDisabledIntegrationTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthIntegrationTest
Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventIntegrationTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventResilienceTest
Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoAuthIntegrationTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoValidationTest
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 六、是否有偏离范围的地方

对照"明确不要做的事"逐条确认：

- 未修改 `UserRepository.findByUsername` 的 SQL 或匹配方式 ✅
- 未讨论「是否应该」大小写不敏感，只验证现状 ✅

无偏离。

## 七、交付物与后续事项

- 分支名：`t-tg-05-username-case-test`
- PR 链接：https://github.com/chuanminglu/tasksplitting/pull/11（已合并，main `0c10125`）
- 后续可参考约定：`assertUserNotFound` 私有辅助在 `AuthIntegrationTest` 内实现；后续若新增更多「登录查不到用户」类断言可复用它，跨文件共用时再考虑抽取 `AuthTestSupport`，本任务范围内不做。
