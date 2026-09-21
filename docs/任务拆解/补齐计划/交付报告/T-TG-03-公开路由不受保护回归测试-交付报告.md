# T-TG-03 交付报告 — 公开路由不受保护回归测试

> 任务编号：T-TG-03
> 验收标准：无新增AC，验证既有行为——`/api/health` 与 `/api/auth/login` 无 `Authorization` 头亦可访问
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述

为"公开端点不被 `AuthInterceptor` 拦截"这一既有行为补回归测试，防止未来有人无意扩大 `WebConfig.addPathPatterns` 范围时无测试报警。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）

| 预计文件 | 状态 | 说明 |
|---|---|---|
| `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例） | ✅ 已修改 | 新增 2 个用例 + 1 个 `get` 静态导入 |

"可能改动"清单：任务说明标注"无"，实际未改动其他文件（未改 `WebConfig`、未新增公开端点）。

## 三、实际技术选型是否与说明一致

- **新增用例放入 `AuthIntegrationTest`（不新建文件）**：与说明一致。
- 复用了该文件既有的 `createUser` 辅助方法与 `@SpringBootTest` 上下文，未引入新依赖。
- 为支持 `GET /api/health` 补充了 `MockMvcRequestBuilders.get` 静态导入（该文件此前只用 `post`）。

## 四、新增的测试用例

| 测试位置 | 用例名 | 覆盖的DoD点 |
|---|---|---|
| `AuthIntegrationTest` | `healthEndpointIsReachableWithoutAuthorization` | `[FACT]` 无 Authorization 头访问 `/api/health` → 200 |
| `AuthIntegrationTest` | `loginEndpointIsReachableWithoutAuthorization` | `[FACT]` 无 Authorization 头调用 `/api/auth/login`（正确凭证）→ 200 + token 非空 |

## 五、测试运行结果（原样节选）

命令：`cd d:\Programs\tasksplitting\server && mvn test`

```text
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventResilienceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoAuthIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoValidationTest
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 六、是否有偏离范围的地方

对照"明确不要做的事"逐条确认：

- 未修改 `WebConfig` 的拦截路径配置 ✅
- 未新增其他公开端点 ✅

无偏离。

## 七、交付物与后续事项

- 分支名：`t-tg-03-public-routes-test`
- PR 链接：（合并后补充）
- 说明：本用例与既有的"登录成功"用例意图不同——前者锁定"零 token 情况下该端点可达"这一鉴权边界事实，后者验证登录业务逻辑本身。
