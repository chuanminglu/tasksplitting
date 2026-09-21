# T-TG-01 交付报告 — Todo空标题校验补测试

> 任务编号：T-TG-01
> 验收标准：无新增AC，验证既有行为（空/空白 title → 400 且不落库）
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述

为 `TodoController.create` 已有的"空标题拒绝"行为补充集成测试：空字符串与纯空白字符串 `title` 提交均返回 400 + `message` 字段，且数据库中 todo 数量不增加。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）

| 预计文件 | 状态 | 说明 |
|---|---|---|
| `server/src/test/java/com/tasksplitting/api/TodoValidationTest.java`（新增） | ✅ 已新增 | 独立测试文件，2 个用例 |

"可能改动"清单：任务说明标注"无"，实际未改动任何其他文件（未改 `TodoController`、`TodoRepository`，未动 `TodoAuthIntegrationTest`）。

## 三、实际技术选型是否与说明一致

- **测试文件归属** → 新建独立文件 `TodoValidationTest`，未并入 `TodoAuthIntegrationTest`，与说明一致。
- 搭建方式完全参照 `TodoAuthIntegrationTest`：`@SpringBootTest` + `@AutoConfigureMockMvc`，独立 SQLite 库 `target/test-todo-validation.db`，`new BCryptPasswordEncoder()` 实例化（容器无此 bean，`AuthService` 内部自建）。
- 本用例不涉及时钟注入（校验分支在鉴权通过后立即短路，不触及时效逻辑），未引入 `TestClock`，属最小化搭建。

## 四、新增的测试用例

| 测试位置 | 用例名 | 覆盖的DoD点 |
|---|---|---|
| `TodoValidationTest` | `createTodoWithEmptyTitleReturns400AndDoesNotPersist` | `[FACT]` 空字符串 → 400 + `message` 字段；`[INFER]` todo 总数未增加 |
| `TodoValidationTest` | `createTodoWithBlankTitleReturns400AndDoesNotPersist` | `[FACT]` 纯空白 → 400 + `message` 字段；`[INFER]` todo 总数未增加 |

## 五、测试运行结果（原样节选）

命令：`cd d:\Programs\tasksplitting\server && mvn test`

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventResilienceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoAuthIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoValidationTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 六、是否有偏离范围的地方

对照"明确不要做的事"逐条确认：

- 未修改 `TodoController.create` 的校验逻辑 ✅
- 未修改 `TodoRepository` ✅
- 未顺带给 `GET /api/todos` 补充其他无关测试（`countTodos` 辅助方法仅用于 DoD 第 3 条断言，非独立测试）✅

无偏离。

## 七、交付物与后续事项

- 分支名：`t-tg-01-todo-empty-title-test`
- PR 链接：（合并后补充）
- 后续任务可参考的约定：`TodoValidationTest` 的独立库文件名为 `target/test-todo-validation.db`；登录辅助方法在测试类内私有实现（与 `TodoAuthIntegrationTest` 一致），后续如多个校验类测试文件共用，可考虑抽取 `AuthTestSupport`，但本任务范围内不做。
