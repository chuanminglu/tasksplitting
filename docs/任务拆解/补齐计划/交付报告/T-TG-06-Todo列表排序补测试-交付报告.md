# T-TG-06 交付报告 — Todo列表排序补测试

> 任务编号：T-TG-06
> 验收标准：无新增AC，验证既有生产代码行为——`GET /api/todos` 按 `createdAt` 降序（最新的在最前）
> 验收模式：PR-only
> 交付日期：2026-09-21

## 一、任务概述

为 `TodoRepository.findAll` 既有的 `ORDER BY "createdAt" DESC` 排序行为补测试：登录拿 token 后依次创建 `A`/`B`/`C` 三个 Todo，断言 `GET /api/todos` 返回顺序为 `C`、`B`、`A`（最新在最前）。`TodoAuthIntegrationTest.getTodosWithValidTokenReturns200` 此前只断言 200 与数组类型，未锁定顺序。

## 二、实际改动的文件（与"预计涉及文件"逐项核对）

| 预计文件 | 状态 | 说明 |
|---|---|---|
| `server/src/test/java/com/tasksplitting/api/TodoAuthIntegrationTest.java`（修改，新增用例） | ✅ 已修改 | 新增 1 个用例 + 2 个私有辅助 + 1 个 import |

"可能改动"清单：任务说明标注「无」，实际未改动任何其他文件（未改 `TodoRepository.findAll` 排序逻辑，未给 Todo 新增 `?sort=` 等排序 API 参数）。

## 三、实际技术选型是否与说明一致

任务说明标注「无开放性决策——复用该文件已有的可控 Clock 基础设施」。实际实现中发现两处**比说明更细**的工程约束，均不改变验证的核心事实：

- **时钟不生效于本用例**：`TodoRepository` 的 `createdAt` 由 SQLite `CURRENT_TIMESTAMP`（**秒级精度**）生成，**不走**该测试类注入的 `TestClock`（2026-01-01），故 `advance`/`reset` 对 Todo 时间戳无作用。同一秒内连续插入会时间戳相同、排序不确定。按任务 `[ASSUME]` 条款，在 `A→B`、`B→C` 之间各插入 `Thread.sleep(1100)` 保证时间戳严格递增——属测试稳定性细节，不影响验证的核心事实。
- **`findAll` 不按用户过滤**：查询返回全部行，同类中 `postTodosWithValidTokenReturns201` 等也会插入。故断言只验证 `C`/`B`/`A` 三者**相对顺序**（`idxC < idxB < idxA`），不断言 `body.size()==3`，避免受其他用例插入行影响。

## 四、新增的测试用例

| 测试位置 | 用例名 | 覆盖的DoD点 |
|---|---|---|
| `TodoAuthIntegrationTest` | `getTodosReturnsLatestCreatedAtFirst` | `[FACT]` 依次创建 A/B/C 后，`GET /api/todos` 返回顺序为 C、B、A |
| `TodoAuthIntegrationTest`（私有辅助） | `createTodo(token,title)` | 非独立 DoD 点，POST 一条 Todo 并断言 201 |
| `TodoAuthIntegrationTest`（私有辅助） | `findIndex(body,title)` | 非独立 DoD 点，按标题定位下标（未找到返回 -1） |

## 五、测试运行结果（原样节选）

命令：`cd d:\Programs\tasksplitting\server && mvn test`

```text
Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthDisabledIntegrationTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.AuthIntegrationTest
Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventIntegrationTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.LoginEventResilienceTest
Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoAuthIntegrationTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in com.tasksplitting.api.TodoValidationTest
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 六、是否有偏离范围的地方

对照"明确不要做的事"逐条确认：

- 未修改 `TodoRepository.findAll` 的排序逻辑 ✅
- 未给 Todo 新增排序相关 API 参数（如 `?sort=`）✅

无偏离。

## 七、交付物与后续事项

- 分支名：`t-tg-06-todo-order-test`
- PR 链接：https://github.com/chuanminglu/tasksplitting/pull/12（已合并，main `1fe227b`）
- 后续可参考约定：`findAll` 当前不按用户过滤，本用例以「相对顺序」断言规避跨用例干扰；若后续 `findAll` 改为按用户/会话过滤，可把断言收紧为 `body.size()==3` + 绝对下标。
