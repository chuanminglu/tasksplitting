# T-CFG-02 交付报告 — 数据库路径环境变量化

- 任务编号：`T-CFG-02`
- 阶段：阶段 3（config · Copilot）
- 验收模式：PR-only
- 交付日期：2026-09-21
- 规格来源：`docs/任务拆解/补齐计划/补充包C-config-Copilot执行计划.md`

## 一、任务概述

把 `server/src/main/resources/application.yml` 里硬编码的 SQLite 数据库路径 `jdbc:sqlite:server/prisma/dev.db` 改为可通过环境变量 `DB_PATH` 覆盖、默认值保持不变。使用 Spring Boot 原生 `${DB_PATH:server/prisma/dev.db}` 占位符语法，不引入额外配置库或 profile 机制。

## 二、实际改动文件核对表

| 文件 | 计划状态 | 实际状态 | 说明 |
| --- | --- | --- | --- |
| `server/src/main/resources/application.yml` | 必须改动 | ✅ 已改动 | 仅改 `datasource.url` 一行 |

改动内容（唯一改动，单行）：

```diff
   datasource:
-    url: jdbc:sqlite:server/prisma/dev.db
+    url: jdbc:sqlite:${DB_PATH:server/prisma/dev.db}
     driver-class-name: org.sqlite.JDBC
```

`driver-class-name`、`hikari.maximum-pool-size`、`sql.init.mode` 等其余数据源配置均未改动。

## 三、实际技术选型是否与说明一致

一致。

- **占位符语法**：采用 Spring Boot 原生 `${DB_PATH:server/prisma/dev.db}`，与规格技术选型一致。
- **环境变量名**：`DB_PATH`，与规格 `[ASSUME]` 选定值一致。
- **不引入额外机制**：未引入配置库、profile、`@PropertySource` 等，与规格一致。

## 四、新增测试 / 验证与 DoD 映射

本任务规格"完成定义（DoD）"共 2 条，逐条映射如下：

| DoD | 验证方式 | 结果 |
| --- | --- | --- |
| 不设置 `DB_PATH` 时 `mvn test` 全部通过（默认值未变，无回归） | `cd server; mvn test` | ✅ `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS` |
| `DB_PATH` 覆盖生效性的实测说明（做则附证据；未做则如实说明，不得断言"已验证生效"） | 见下「生效性验证的如实说明」 | ✅ 已如实说明（未做启动级验证，理由与边界见下） |

### `mvn test` 原始输出节选

```text
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:14 min
[INFO] Finished at: 2026-09-21T18:43:33+08:00
```

### 生效性验证的如实说明（DoD 第 2 条）

**本次未做"启动应用后确认连接到新路径"的手工验证，因此不宣称"已验证生效"。** 原因与边界如下：

1. **`mvn test` 覆盖不到该点**：仓库中全部 5 个集成测试类（`AuthIntegrationTest`、`LoginEventIntegrationTest`、`LoginEventResilienceTest`、`TodoAuthIntegrationTest`、`TodoValidationTest`）均通过 `@TestPropertySource("spring.datasource.url=jdbc:sqlite:target/*.db")` 直接指定数据源 URL，**绕过**了 `application.yml` 里的占位符。因此无论是否设置 `DB_PATH`，这些测试连接的都是各自的 `target/*.db`，`mvn test` 的结果无法证明占位符在真实应用上下文里被解析成 `DB_PATH` 的值。
2. **启动级验证在本工作区风险高**：本地有常驻开发服务器（`concurrently` + `vite` + `esbuild`，且占用 4000 服务端口、持有 `node_modules` 原生文件锁），再 `spring-boot:run` 启动一个真实应用做路径观察，存在端口冲突、文件锁与残留 `.db` 的副作用，且会超出本任务"只改 `application.yml`"的文件范围。
3. **占位符语法本身为 Spring Boot 标准行为**：`${PROP:default}` 的解析由 Spring 的 `PropertyPlaceholder` 机制保证，默认值场景已由 `mvn test`（33 全绿）覆盖证明未引入回归。

**结论（如实、不夸大）**：已确认 (1) `application.yml` 占位符语法正确、(2) 默认值（未设 `DB_PATH`）场景 `mvn test` 33 全绿；**`DB_PATH` 覆盖生效性未做启动级手工验证**。规格 DoD 第 2 条对此的允许口径为"如未做手工验证需如实说明……不能断言已验证生效"，本交付按此口径处理。

## 五、原始命令输出节选

见「四、新增测试 / 验证与 DoD 映射」中 `mvn test` 输出节选（本任务唯一的验证命令；未新增其它验证命令）。

## 六、是否有偏离范围的地方（逐条对照"明确不要做的事"）

- **不要改变数据库引擎（仍是 SQLite）**：✅ 仍为 `jdbc:sqlite:` + `org.sqlite.JDBC`，仅路径部分引入占位符，引擎未变。
- **不要修改 `spring.sql.init.mode` 等其他数据源相关配置**：✅ `sql.init.mode: always`、`hikari.maximum-pool-size: 5`、`driver-class-name` 均未改动。

无偏离范围之处。

## 七、交付物与后续事项

- 分支：`t-cfg-02-db-path-env`；提交：`application.yml` 单行改动 + 本报告 + 总执行计划跟踪表
- CI：`.github/workflows/ci.yml`（T-CFG-01 已合并）会在本 PR 触发 `server mvn test` + `client typecheck`；本任务本地 `mvn test` 已 33 全绿。
- 下一步任务：`T-CFG-03`（端口环境变量化，验证优先，见补充包 C）。
