# T-TG-06 Todo列表排序补测试 交付报告

**阶段**：阶段 1 · 6/8　**分支**：`t-tg-06-todo-order-test`　**基线**：`main`（PR #11 合并后 `0c10125`）

## 1. 任务概述

为 `TodoRepository.findAll` 既有的 `ORDER BY "createdAt" DESC` 排序行为补测试：登录拿 token 后依次创建 `A`/`B`/`C` 三个 Todo，断言 `GET /api/todos` 返回顺序为 `C`、`B`、`A`（最新在最前）。无新增 AC，仅验证既有生产代码行为。

## 2. 实际改动的文件

| 文件 | 改动 |
|---|---|
| `server/src/test/java/com/tasksplitting/api/TodoAuthIntegrationTest.java` | 新增 `getTodosReturnsLatestCreatedAtFirst`；新增私有辅助 `createTodo(token,title)`、`findIndex(body,title)`；新增 `assertTrue` import |

## 3. 技术选型

关键约束：`TodoRepository` 的 `createdAt` 由 SQLite `CURRENT_TIMESTAMP`（**秒级精度**）生成，与该类注入的 `TestClock`（2026-01-01）无关。同一秒内连续插入会导致时间戳相同、排序不确定。按任务 `[ASSUME]` 条款，在创建 `A`→`B`、`B`→`C` 之间各插入 `Thread.sleep(1100)` 保证时间戳严格递增。

另一约束：`findAll` 不按用户过滤（`WHERE token` 仅校验会话有效，查询本身返回全部行），且同类中 `postTodosWithValidTokenReturns201` 等也会插入行。因此断言只验证 `C`/`B`/`A` 三者的**相对顺序**（`idxC < idxB < idxA`），不断言 `body.size()==3`，避免受其他用例插入行影响。

## 4. 新增测试用例

- `getTodosReturnsLatestCreatedAtFirst`：登录 → 依次创建 A/B/C（间隔 1.1s）→ `GET /api/todos` 断言 C、B、A 相对顺序。
- 辅助 `createTodo(String token, String title)`：POST 一条 Todo 并断言 201。
- 辅助 `findIndex(JsonNode body, String title)`：按标题定位下标，未找到返回 -1。

## 5. 测试运行结果

`cd server; mvn test`（Maven 3.9.9，Java 17.0.15）：

| 测试类 | 结果 |
|---|---|
| AuthDisabledIntegrationTest | 1 |
| AuthIntegrationTest | 13 |
| LoginEventIntegrationTest | 5 |
| LoginEventResilienceTest | 2 |
| **TodoAuthIntegrationTest** | **9（新增 1）** |
| TodoValidationTest | 2 |

**合计：Tests run: 32, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS。**

## 6. 偏离范围

无。仅新增 1 个测试方法与 2 个私有辅助方法、1 个 import，未触碰任何主代码，未修改 `findAll` 排序逻辑，未新增排序 API 参数。

## 7. 交付物与后续事项

- 交付物：本报告、`TodoAuthIntegrationTest` 新增用例、分支 `t-tg-06-todo-order-test`、PR。
- 待人工确认：验收后合并 PR，继续 T-TG-07「Session 过期边界补测试」。
