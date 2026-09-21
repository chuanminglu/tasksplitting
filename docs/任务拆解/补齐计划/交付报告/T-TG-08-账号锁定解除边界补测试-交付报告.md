# T-TG-08 交付报告 — 账号锁定解除边界补测试

> 任务编号：T-TG-08
> 验收标准：无新增AC，验证既有生产代码行为——`isAfter` 语义下 `lockedUntil == now` 不算仍锁定，用正确密码登录成功；登录成功后 `failedLoginAttempts` 归零、`lockedUntil` 清空
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述

为 `AuthService.login` 判断账号锁定时「`lockedUntil` 恰好等于当前时刻视为已解锁」这一边界行为补测试（与 T-TG-07 会话过期边界对称）。`login` 用 `user.lockedUntil().isAfter(now)` 才判仍锁定，`isAfter` 不含相等，故 `lockedUntil == now` 应允许用正确密码登录成功（200，非 423），且登录后 `failedLoginAttempts` 归零、`lockedUntil` 清空。既有 `correctPasswordAfterLockExpiresLogsInAndClearsFailures` 只覆盖了「时钟推进 15 分钟之后」（明确已过期），未锁定这个相等边界。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）

| 预计文件 | 状态 | 说明 |
|---|---|---|
| `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例） | ✅ 已修改 | 新增 1 个用例 |

"可能改动"清单：任务说明标注「无」，实际未改动任何其他文件（未改 `AuthService` 锁定判断逻辑，未新增任何 mock 机制）。

## 三、实际技术选型是否与说明一致

- 任务说明标注「无开放性决策——复用该文件已有的可控 Clock 基础设施（`TestConfiguration` + `advance`/`reset` 方法）」，实际实现与之一致：用 `clock.now()`（`LocalDateTime`）格式化 `yyyy-MM-dd HH:mm:ss` 直接 `UPDATE User SET lockedUntil = <now>`，使 `lockedUntil == now`。
- 与 T-TG-07 相同的边界对称写法：一个锁「明确过去/未来 → 锁定/解锁」，一个锁「恰好等于当前 → 解锁」。
- 实现细节：先把 `failedLoginAttempts` 一并置为 5（已锁定态），使登录成功后的 `resetLoginFailures` 清零行为可被 DoD `[INFER]` 断言观察到（若 `failedLoginAttempts` 本就为 0，归零断言无法区分「未清零」与「本就为 0」）。

## 四、新增的测试用例

| 测试位置 | 用例名 | 覆盖的DoD点 |
|---|---|---|
| `AuthIntegrationTest` | `correctPasswordWhenLockedUntilExactlyNowLogsInAndClearsFailures` | `[FACT]` `lockedUntil` 恰好等于当前时刻的账号，用正确密码登录 → 200 成功；`[INFER]` 登录成功后 `failedLoginAttempts` 归零、`lockedUntil` 清空 |

## 五、测试运行结果（原样节选）

命令：`cd d:\Programs\tasksplitting\server && mvn test`

```text
Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthDisabledIntegrationTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthIntegrationTest
Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventIntegrationTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventResilienceTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoAuthIntegrationTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoValidationTest
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 六、是否有偏离范围的地方

对照"明确不要做的事"逐条确认：

- 未修改 `AuthService` 的锁定判断逻辑（`isAfter` 语义保持原样）✅

无偏离。

## 七、交付物与后续事项

- 分支名：`t-tg-08-lockout-boundary-test`
- PR 链接：（合并后补充）
- 后续可参考约定：`clock.now()` + `jdbc` 直更 `User.lockedUntil`/`failedLoginAttempts` 是该类控制账号锁定态的既有方式；T-TG-07/T-TG-08 已构成「相等边界」的会话过期/账号锁定对称对。
