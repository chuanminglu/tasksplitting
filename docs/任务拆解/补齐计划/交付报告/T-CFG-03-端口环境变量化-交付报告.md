# T-CFG-03 交付报告 — 端口环境变量化（验证优先）

- 任务编号：`T-CFG-03`
- 阶段：阶段 3（config · Copilot）
- 验收模式：PR-only
- 交付日期：2026-09-21
- 规格来源：`docs/任务拆解/补齐计划/补充包C-config-Copilot执行计划.md`

## 一、任务概述

确认服务端口是否已经可以通过环境变量 `SERVER_PORT` 覆盖。若可以，不修改生产代码，只补 README 说明；若不可以，才改 `application.yml`。

**验证结论：`SERVER_PORT` 已能通过 Spring Boot 原生 relaxed binding 覆盖 `server.port`，无需任何代码改动。** 因此本任务只新增验证测试 + 补 README，未修改 `application.yml`。

## 二、实际改动文件核对表

| 文件 | 计划状态 | 实际状态 | 说明 |
| --- | --- | --- | --- |
| `server/src/main/resources/application.yml` | 可能改动（仅当验证不通过） | ✅ **未改动** | 验证通过，无需修改 |
| `server/src/test/java/com/tasksplitting/api/PortEnvVarTest.java` | 未列出（验证证据） | ✅ 新增 | 用 `@SpringBootTest(DEFINED_PORT, server.port=41999)` 证明端口可被覆盖、应用可在非默认端口启动 |
| `README.md` | 规格要求（"只在README里补一句说明"） | ✅ 改动 | 补 `SERVER_PORT` 覆盖说明 |

## 三、实际技术选型是否与说明一致

一致。

- **验证方式**：规格允许"测试代码或手工验证步骤+结果"。采用测试代码（`PortEnvVarTest`）：`@SpringBootTest(webEnvironment = DEFINED_PORT, properties = "server.port=41999")` 启动真实 Tomcat 绑定 41999 端口，断言 `getPort()==41999`。这正是规格示例"Spring Boot Test 里通过 `@SpringBootTest(properties = "server.port=...")` 验证 relaxed binding 是否生效"。
- **未改 application.yml**：验证通过，符合规格"不修改 application.yml，只在 README.md 里补一句说明"。

## 四、新增测试 / 验证与 DoD 映射

本任务 DoD 共 3 条，逐条映射如下：

| DoD | 验证方式 | 结果 |
| --- | --- | --- |
| 有明确的验证记录，证明"环境变量能否覆盖端口"这一事实 | `PortEnvVarTest`：`DEFINED_PORT + server.port=41999`，断言 `getPort()==41999`，Tomcat 启动日志确认 "Tomcat started on port 41999" | ✅ Tests run: 1, Failures: 0, Errors: 0 |
| 若验证通过且未改代码，README.md 已补充说明 | `README.md` 已补：`SERVER_PORT` 环境变量覆盖说明 + 使用示例 | ✅ |
| 无论哪种结果，交付报告必须明确说明验证方法和结论 | 本报告第二节、第三节、第五节均记录了验证方法（DEFINED_PORT 测试）、结果（34 tests 全绿）、结论（无需改代码） | ✅ |

### `mvn test` 原始输出节选

```text
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  26.003 s
[INFO] Finished at: 2026-09-21T18:59:08+08:00
```

`PortEnvVarTest` 单独运行输出节选：

```text
o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 41999 (http)
o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 41999 (http) with context path '/'
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 五、原始命令输出节选

见「四」节 `mvn test` 全量输出和 `PortEnvVarTest` 单独运行输出。

## 六、是否有偏离范围的地方（逐条对照"明确不要做的事"）

- **不要在未验证的情况下就直接添加占位符语法**：✅ 先验证后决定——验证通过，未添加占位符。
- **不要修改端口以外的其他 `server.*` 配置**：✅ `application.yml` 未做改动。

无偏离范围之处。

## 七、交付物与后续事项

- 分支：`t-cfg-03-port-env`；提交：`PortEnvVarTest.java`（新增）+ `README.md`（补说明）+ 本报告 + 总执行计划跟踪表
- 结论：`SERVER_PORT` 环境变量可通过 Spring Boot 原生 relaxed binding 覆盖 `server.port`，无需代码改动，已补 README 说明。
- 下一步任务：`T-CFG-04`（测试专属配置文件，见补充包 C）。
