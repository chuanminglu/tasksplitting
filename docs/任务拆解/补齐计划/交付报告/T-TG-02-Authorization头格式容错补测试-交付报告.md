# T-TG-02 交付报告 — Authorization头格式容错补测试

> 任务编号：T-TG-02
> 验收标准：无新增AC，验证既有行为（header 存在但 scheme/格式错误 → 401 + `error.code == "UNAUTHORIZED"`）
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述

为 `AuthInterceptor.extractBearerToken` 已有的"格式错误请求头拒绝"分支补充 2 个集成测试：错误 scheme（`Basic`）与 `Bearer ` 后为空两种情况，此前 `TodoAuthIntegrationTest` 只覆盖"完全无 header / token 不存在 / token 过期"三种情况。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）

| 预计文件 | 状态 | 说明 |
|---|---|---|
| `server/src/test/java/com/tasksplitting/api/TodoAuthIntegrationTest.java`（修改，新增用例） | ✅ 已修改 | 新增 2 个用例 + 2 个静态导入（`jsonPath`、`equalTo`），共 8 个用例 |

"可能改动"清单：任务说明标注"无"，实际未改动任何其他文件（未改 `AuthInterceptor`，未改错误响应格式）。

## 三、实际技术选型是否与说明一致

- **复用既有测试搭建方式** → 完全一致：直接使用 `TodoAuthIntegrationTest` 既有的 MockMvc 上下文，两个新用例无需登录/建用户（401 分支在进入会话查询前就短路），仅复用该文件已导入的静态方法与新增的 `jsonPath`/`equalTo` 断言导入。
- 断言方式补充：任务要求"401 + `error.code == "UNAUTHORIZED"`"，故在既有 `status()` 断言之外追加 `jsonPath("$.error.code", equalTo("UNAUTHORIZED"))`，比仅断言状态码更精确地锁定错误结构。

## 四、新增的测试用例

| 测试位置 | 用例名 | 覆盖的DoD点 |
|---|---|---|
| `TodoAuthIntegrationTest` | `getTodosWithWrongSchemeReturns401` | `[FACT]` 错误 scheme（`Basic dXNlcjpwYXNz`）→ 401 + `error.code == "UNAUTHORIZED"` |
| `TodoAuthIntegrationTest` | `getTodosWithEmptyBearerReturns401` | `[FACT]` `Bearer ` 后为空 → 401 + `error.code == "UNAUTHORIZED"` |

## 五、测试运行结果（原样节选）

命令：`cd d:\Programs\tasksplitting\server && mvn test`

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventResilienceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoAuthIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoValidationTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 六、是否有偏离范围的地方

对照"明确不要做的事"逐条确认：

- 未修改 `AuthInterceptor` 本身的判断逻辑 ✅
- 未修改错误响应格式（测试断言的正是既有格式 `{"error":{"code":"UNAUTHORIZED",...}}`）✅

无偏离。

## 七、交付物与后续事项

- 分支名：`t-tg-02-auth-header-format-test`
- PR 链接：（合并后补充）
- 后续任务可参考的约定：`TodoAuthIntegrationTest` 现在已导入 `jsonPath` 断言工具，后续 T-TG 系列在同类文件断言错误结构时可直接复用。
