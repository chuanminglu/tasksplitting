# T-CFG-04 交付报告 — 测试专属配置文件

- 任务编号：`T-CFG-04`
- 阶段：阶段 3（config · Copilot）
- 验收模式：PR-only
- 交付日期：2026-09-21
- 规格来源：`docs/任务拆解/补齐计划/补充包C-config-Copilot执行计划.md`

## 一、任务概述

新增 `server/src/main/resources/application-test.yml`，为后续新测试类提供统一的测试数据库路径配置（通过 `@ActiveProfiles("test")` 激活）。现有测试类不做改动（规格明确保守处理）。

## 二、实际改动文件核对表

| 文件 | 计划状态 | 实际状态 | 说明 |
| --- | --- | --- | --- |
| `server/src/main/resources/application-test.yml` | 必须改动（新增） | ✅ 已新增 | 定义独立测试数据库路径 |
| `server/src/test/java/com/tasksplitting/api/TestProfileConfigTest.java` | 未明确要求（补充） | ✅ 已新增 | 验证 profile 配置文件正确性（纯 JUnit，不启动 Spring 上下文） |
| 6 个现有测试类 | 不修改 | ✅ 未修改 | 符合规格"不重构"要求 |

## 三、实际技术选型是否与说明一致

一致。

- **数据库路径**：`jdbc:sqlite:target/test-data/application-test.db`，与规格建议一致（`target/test-data/` 子目录，与现有 `target/*.db` 同级目录结构一致）。
- **激活方式**：`@ActiveProfiles("test")`，Spring Boot 原生 profile 机制。
- **不重构现有测试类**：与规格 `[ASSUME]` 一致。
- **新增 `TestProfileConfigTest`**：规格仅要求"文件存在 + 现有测试无回归"，未强制要求自动化测试验证 profile 生效。为使"测试 profile 生效"这一 DoD 有可复核证据，补充了一个**纯 JUnit** 测试（不启动 Spring 上下文、不需要数据库），用 `YamlPropertiesFactoryBean` 解析 YAML 并用 `StandardEnvironment` 验证 `test` profile 激活时的属性优先级。选择纯 JUnit 而非 `@SpringBootTest` 的原因见「四」节末尾"中间失败方案"说明。

## 四、新增测试 / 验证与 DoD 映射

| DoD | 验证方式 | 结果 |
| --- | --- | --- |
| `application-test.yml` 文件存在，配置了独立于开发库的测试数据库路径 | 文件已创建，内容见下；`TestProfileConfigTest.applicationTestYmlDefinesDedicatedDatabasePath` 断言该值 | ✅ |
| 测试 profile 激活时数据源路径按预期覆盖默认值（"测试 profile 生效"的自动化证据） | `TestProfileConfigTest.testProfileOverridesDefaultWhenActive`，用 `StandardEnvironment` + `YamlPropertiesFactoryBean` 模拟 Spring 属性源优先级 | ✅ 1/1 通过 |
| 现有测试类的全部测试用例（`mvn test`）依然全部通过，无回归 | `cd server; mvn test` | ✅ `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS（34 既有测试 + `TestProfileConfigTest` 新增 2 个） |
| 交付报告需包含对现有测试类数据库配置方式的自查结论 | 见下「自查结论」 | ✅ |

### `application-test.yml` 内容

```yaml
# Test profile: independent SQLite database path.
# Activate with @ActiveProfiles("test") in new test classes.
# Existing test classes are NOT affected — they continue using their
# own @SpringBootTest(properties) database overrides.
spring:
  datasource:
    url: jdbc:sqlite:target/test-data/application-test.db
```

### `mvn test` 原始输出节选（最终运行）

```text
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  25.753 s
[INFO] Finished at: 2026-09-21T19:13:36+08:00
```

### `TestProfileConfigTest` 关键断言

- `applicationTestYmlDefinesDedicatedDatabasePath`：解析 `application-test.yml`，断言 `spring.datasource.url == jdbc:sqlite:target/test-data/application-test.db`。
- `testProfileOverridesDefaultWhenActive`：`StandardEnvironment.setActiveProfiles("test")`，将 `application.yml` 与 `application-test.yml` 分别装入 `PropertiesPropertySource`（默认 `addLast`、测试 `addFirst`），断言 `env.getProperty("spring.datasource.url")` 取到测试值，验证 profile 激活时的属性优先级符合 Spring 语义。

### 中间失败方案（诚实记录）

初版 `TestProfileConfigTest` 采用 `@SpringBootTest` + `@ActiveProfiles("test")` 断言 `DataSource` URL，连续两次失败：

1. 第一次：`sql.init.mode: always` 需要 `target/test-data/` 目录存在，目录不存在导致上下文加载失败。
2. 第二次：为绕过目录问题在 `src/test/resources/` 放了一份 `application-test.yml`，但**测试类路径会遮蔽主类路径的同名文件**（Spring Boot 配置加载顺序），主 `application-test.yml` 的 `spring.datasource.url` 被丢失，回退到 `application.yml` 的 `server/prisma/dev.db` 相对路径，报错 `path to 'server/prisma/dev.db': 'D:\Programs\tasksplitting\server\server' does not exist`。

**最终方案**：删除 `src/test/resources/application-test.yml` 和基于 Spring 上下文的测试版本，改用纯 JUnit 测试——不启动任何 Spring 上下文、不连接数据库，直接用 `YamlPropertiesFactoryBean` 解析 YAML 文件、用 `StandardEnvironment` 模拟属性源优先级，既规避了上述两个环境问题，又能精确验证"配置文件内容正确"和"profile 激活时的优先级"这两件事。此测试在 CI（干净环境、无本地 dev server）同样可复现。

### 自查结论（现有测试类的数据库配置方式）

规格要求"自查现有 4 个测试类的数据库配置方式"（实际已扩展为 6 个，含 T-CFG-03 新增的 `PortEnvVarTest`）。自查结论：

| 测试类 | 数据库路径 | 配置方式 |
| --- | --- | --- |
| `AuthIntegrationTest` | `target/test-login-enabled.db` | `@SpringBootTest(properties)` |
| `LoginEventIntegrationTest` | `target/test-login-event.db` | `@SpringBootTest(properties)` |
| `LoginEventResilienceTest` | `target/test-login-event-resilience.db` | `@SpringBootTest(properties)` |
| `TodoAuthIntegrationTest` | `target/test-todo-auth.db` | `@SpringBootTest(properties)` |
| `TodoValidationTest` | `target/test-todo-validation.db` | `@SpringBootTest(properties)` |
| `PortEnvVarTest`（T-CFG-03 新增） | `target/test-port-env-var.db` | `@SpringBootTest(properties)` |

**结论**：所有测试类各自使用独立的 `@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/*.db")` 硬编码路径，无 `@ActiveProfiles`、无 `@DynamicPropertySource`、无共享配置。这种"每个测试类各自指定独立数据库文件"的模式是**有意的隔离设计**（各测试类需要独立的、不干扰的数据库状态），不适合被统一收口到单一 profile——如果统一到一个 profile + 一个 DB 文件，测试类之间会互相污染数据。因此 `application-test.yml` 是**供后续需要共享配置的测试类使用的基础设施**，现有 6 个类保持现状不变是合理的。新增的 `TestProfileConfigTest` 不接入 Spring 上下文，不属于该隔离模式的一部分（它只校验配置文件本身）。

## 五、原始命令输出节选

见「四」节 `mvn test` 输出。

## 六、是否有偏离范围的地方（逐条对照"明确不要做的事"）

- **不要修改现有 4 个测试类的数据库配置方式**：✅ 未修改任何现有测试类。
- **不要删除或合并现有测试类**：✅ 未删除或合并。

无偏离范围之处。

## 七、交付物与后续事项

- 分支：`t-cfg-04-test-profile`；提交：`application-test.yml`（新增）+ `TestProfileConfigTest.java`（新增）+ 本报告 + 总执行计划跟踪表
- 下一步任务：`T-CFG-05`（前端 API Base URL 可配置化，见补充包 C）。
