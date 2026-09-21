# T-TG-04 交付报告 — 登录空用户名/密码补测试

> 任务编号：T-TG-04
> 验收标准：无新增AC，验证既有行为——空/缺失用户名密码不会500，而是走 `USER_NOT_FOUND` 分支
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述

为 `AuthService.login` 已有的 null 兜底行为（`safeUsername = username == null ? "" : username`）补测试：空字符串用户名与 JSON 完全缺失 `username` 字段两种情况，均应返回 401 + `USER_NOT_FOUND`，而非 500/NPE。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）

| 预计文件 | 状态 | 说明 |
|---|---|---|
| `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例） | ✅ 已修改 | 新增 2 个用例 |

"可能改动"清单：任务说明标注"无"，实际未改动其他文件（未改 `AuthService` 的 null 处理逻辑、未新增任何输入校验）。

## 三、实际技术选型是否与说明一致

- 无开放性决策，直接复用 `AuthIntegrationTest` 既有 `MockMvc` + `ObjectMapper` 断言风格（与 `loginWithUnknownUsernameReturnsUserNotFoundCode` 一致）。

## 四、新增的测试用例

| 测试位置 | 用例名 | 覆盖的DoD点 |
|---|---|---|
| `AuthIntegrationTest` | `loginWithEmptyUsernameReturnsUserNotFoundNot500` | `username` 为空字符串 → 401 + `error.code == USER_NOT_FOUND`（非500） |
| `AuthIntegrationTest` | `loginWithMissingUsernameFieldReturnsUserNotFoundNot500` | JSON 完全不传 `username` 字段（反序列化为 null）→ 401 + `error.code == USER_NOT_FOUND`（非500） |

## 五、测试运行结果（原样节选）

命令：`cd d:\Programs\tasksplitting\server && mvn test`

```text
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventResilienceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoAuthIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoValidationTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 六、是否有偏离范围的地方

对照"明确不要做的事"逐条确认：

- 未修改 `AuthService.login` 的 null 处理逻辑 ✅
- 未新增输入校验（如"用户名不能为空"这类显式 400 校验）——本任务只验证现状 ✅

无偏离。

## 七、交付物与后续事项

- 分支名：`t-tg-04-empty-credentials-test`
- PR 链接：（合并后补充）
