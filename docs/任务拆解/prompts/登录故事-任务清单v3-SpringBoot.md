# 登录故事 · 任务清单总览（v3：后端改为Spring Boot，直接生成，未经子agent）

> 由我（Claude）依据改版后的 `迭代任务分解-subagent提示词.md`（v2）方法论，对**本地工作目录**（`/mnt/d/Programs/tasksplitting`，即origin为`https://github.com/chuanminglu/tasksplitting.git`的同一个仓库，无需clone/fetch）做真实自查后直接生成，不经过Agent工具子agent调用（用户明确要求"任务拆解这类工作直接自己做"）。
>
> 与v2版（`登录故事-任务清单v2-真实仓库自查.md`）的唯一实质差异：**后端技术栈从 Node.js+Express+Prisma 改为 Spring Boot**，前端（React+TS）与故事/验收标准本身不变。v2文档保留作为历史快照，不做回溯修改。

---

## 自查过程（本次实际执行的检查，非模板占位）

- `git remote -v` / `git status` / `git log --oneline`：确认本地工作目录就是目标仓库本身（origin指向`chuanminglu/tasksplitting`，main分支与origin同步），`server/`目录下Node相关文件（`package.json`/`prisma/schema.prisma`/`src/index.ts`/`tsconfig.json`/`.env.example`）已在工作区被删除（git status显示为deleted，未提交）。`[FACT]`
- 读 `server/pom.xml`：Spring Boot 3.4.4，Java 17，依赖仅 `spring-boot-starter-web`、`spring-boot-starter-jdbc`、`org.xerial:sqlite-jdbc:3.49.1.0`、`spring-boot-starter-test`（测试域）。**没有**任何ORM（无JPA/Hibernate）、**没有**任何鉴权/安全依赖（无spring-boot-starter-security、无spring-security-crypto、无jjwt等）、**没有**任何数据库迁移工具（无Flyway/Liquibase）。`[FACT]`
- 读 `server/src/main/java/com/tasksplitting/**`（6个文件全部读取）：包结构为`com.tasksplitting`（启动类）+ `com.tasksplitting.api`（扁平放置Controller/Repository/DTO，无Service层先例）。数据访问方式是**手写SQL + JdbcTemplate**（非ORM），DTO用Java `record`，写操作模式固定为"INSERT ... RETURNING id → 再SELECT一次返回完整对象"（见`TodoRepository.create`）。`[FACT]`
- 读 `server/src/main/resources/application.yml`：`server.port: 4000`；SQLite数据源`jdbc:sqlite:server/prisma/dev.db`（路径沿用旧Prisma遗留路径，未迁移）；HikariCP连接池maximum-pool-size=5。**没有**`spring.sql.init.mode`配置（默认值`embedded`，对文件型SQLite不会自动执行`schema.sql`）。`[FACT]`
- `find server/src/main/resources`：只有`application.yml`一个文件，**不存在`schema.sql`/`data.sql`**，说明当前`Todo`表能被查到纯粹是复用了`server/prisma/dev.db`这个旧Prisma建表遗留下来的文件（该文件被`.gitignore`忽略，不会随仓库分发）——这是本次自查发现的一个**结构性缺口**：新拉取仓库、删除dev.db后，Spring Boot应用本身没有任何建表能力。`[FACT]`，影响T00101的技术选型（必须补schema管理机制，否则连Todo表带来的开发体验都无法复现，更不用说新增User表）。
- 读 `client/src/App.tsx`、`client/package.json`：与v2自查时一致，未变——单文件组件，无路由库，无独立api/目录，fetch调用内联在组件里。`[FACT]`
- 读根 `package.json`：`workspaces`已从`["server","client"]`改为`["client"]`（server不再是npm workspace成员），`dev`脚本改为`concurrently "mvn -f server/pom.xml spring-boot:run" "npm run dev --workspace client"`。`[FACT]`
- Grep `login|auth|password|session|token|jwt|bcrypt` 于 `server/src/main` 与 `client/src`：零命中。`[FACT]`，与v2结论一致，仍是Walking Skeleton前提。

**结论**：技术栈自查结果与v2版本对"无既有鉴权实现"的判断一致（模式判断不变，仍为Walking Skeleton），但具体落地方式因后端语言/框架整体切换而全部需要重新设计——不是简单的"Express路由改Spring Controller"字面翻译，因为**连接层（JdbcTemplate手写SQL vs Prisma ORM）、依赖生态、schema管理机制都不同**。

---

## 全局技术选型（跨任务共享，T00101确立，T00102起复用，不重复决策）

| 决策点 | 选定方案 | 选择依据 |
|---|---|---|
| 数据库schema管理 | 新增 `server/src/main/resources/schema.sql`（`CREATE TABLE IF NOT EXISTS`），并在`application.yml`设置 `spring.sql.init.mode: always` 使其对SQLite文件数据源也生效；后续任务新增字段用 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 追加到同一`schema.sql`（sqlite-jdbc 3.49.1.0捆绑的SQLite版本≥3.35，支持该语法） | `[FACT]`+`[ASSUME]`：自查确认无任何schema管理机制（无Flyway、无schema.sql），这是当前技术栈的结构性缺口，必须补；选Spring Boot内置的`schema.sql`自动执行机制而非引入Flyway，是因为pom.xml当前依赖极简，引入迁移框架对一个仍处脚手架阶段的项目是过度设计 |
| 密码哈希 | 新增依赖 `org.springframework.security:spring-security-crypto`（**不是**完整的`spring-boot-starter-security`），使用其中的`BCryptPasswordEncoder` | `[ASSUME]`：仓库无任何哈希库先例；只引入crypto子模块而非完整Security starter，是为了避免引入自动生效的默认登录页/CSRF/全局端点保护等Spring Security默认行为，与Walking Skeleton"用Feature Flag渐进开放"的节奏冲突，最小化引入面 |
| 会话凭证载体 | 自建 `Session` 表（token主键、userId、expiresAt），token为服务端生成的随机不透明字符串，通过响应体返回，客户端后续请求携带 `Authorization: Bearer <token>` 头 | `[ASSUME]`：仓库无JWT库、无Spring Security session机制先例；选择"自建表+JdbcTemplate手写SQL"是直接复用`TodoRepository`已经确立的实现风格（自查`[FACT]`得出的既有约定），不引入新的技术类别（JWT库）；用Header传token而非Cookie，可以避免修改`TodoController`现有`@CrossOrigin(origins = "http://localhost:5173")`未开放`allowCredentials`这一现状 |
| 鉴权校验方式 | 新增 `AuthInterceptor implements HandlerInterceptor`，通过`WebMvcConfigurer`注册；拦截器内部自行判断Feature Flag是否开启（关闭时直接放行，不校验），不使用Spring Security的Filter Chain | `[ASSUME]`：与"不引入完整Security starter"的决策一致；拦截器是Spring MVC原生机制，足以满足"校验Header中token是否有效"这一需求，不需要框架级别的认证体系 |
| Feature Flag实现 | `application.yml`新增属性 `app.feature.login-auth-enabled: false`，通过 `@Value("${app.feature.login-auth-enabled:false}")` 直接注入到需要判断的类（AuthController、AuthInterceptor） | `[ASSUME]`：仓库无配置中心/现成flag框架，属于最小实现；直接`@Value`注入比新增`@ConfigurationProperties`类更精简，符合当前项目"能不加类就不加"的既有风格（对照现有代码全部是扁平的Controller+Repository，没有额外的配置封装类） |
| 前端登录表单落点 | 直接扩展 `client/src/App.tsx`（不新建组件/页面文件） | `[FACT]`：自查确认`client/src`目前只有`App.tsx`/`main.tsx`/`styles.css`，无`components`/`pages`目录，无路由库；延续既有"单文件"约定，不引入新的目录结构决策 |
| 时间可测试性 | `AuthService`内部一律通过注入的 `java.time.Clock`（Spring可直接`@Bean`提供`Clock.systemDefaultZone()`）获取当前时间，不直接调用`LocalDateTime.now()`/`Instant.now()` | `[ASSUME]`：T00103"锁定满15分钟后自动恢复"这类断言需要测试时能控制时间流逝；Java没有类似JS `vi.useFakeTimers()`的全局mock，标准做法是从一开始就通过依赖注入的`Clock`间接取时间，测试时注入固定`Clock`替换；这个决策必须在T00101就落地，否则T00103要重构已写好的`AuthService` |

---

## US-AUTH-01：用户名密码登录

**故事描述**：作为一名系统用户，我希望能够使用用户名和密码登录系统，登录成功后进入我的工作台，以便安全地访问只属于我的功能和数据。目前系统还没有任何登录/鉴权机制。

**模式判断**：**Walking Skeleton**。依据：`[FACT]` 自查确认无任何既有登录/鉴权代码、无相关依赖、`git log`无相关历史提交。与v2版本结论一致，技术栈切换不影响此判断。

- [ ] **T00101：端到端骨架——最小登录成功路径跑通**
  对应验收标准：AC-1
  路由建议标签：`novel-design`（`[FACT]`——不仅无鉴权先例，且发现schema管理机制本身也缺失，需要同时建立"如何管理数据库schema"这一基础设施决策，复杂度高于单纯补一个登录接口）
  预计涉及文件（概要）：
  - `server/src/main/resources/schema.sql`（新增，`[FACT]`目前不存在此文件）
  - `server/src/main/resources/application.yml`（修改，加`spring.sql.init.mode`与flag属性）
  - `server/pom.xml`（修改，加`spring-security-crypto`依赖）
  - `com/tasksplitting/api/User.java`、`UserRepository.java`、`AuthController.java`、`AuthService.java`、`LoginRequest.java`、`LoginResponse.java`、`SessionRepository.java`（均新增）
  - `client/src/App.tsx`（修改，加登录表单与已登录/未登录条件渲染）
  验收标准：
  - [ ] 用户名密码正确 → `POST /api/auth/login`返回2xx，响应体含token
  - [ ] 前端拿到token后切换到工作台视图
  - [ ] 有集成测试覆盖该路径
  - [ ] Feature Flag关闭时行为与合并前一致

- [ ] **T00102：失败路径分支——账号不存在/密码错误分别提示**
  对应验收标准：AC-2
  路由建议标签：`business-logic`（`[INFER]`，复用T00101基础设施，仅新增条件分支，范围内聚在AuthController/AuthService内）
  预计涉及文件（概要）：`AuthService.java`（修改，新增两类失败判断）、`client/src/App.tsx`（修改，错误码转文案）
  验收标准：
  - [ ] 账号不存在与密码错误返回不同错误标识，前端展示不同文案

- [ ] **T00103：业务规则——连续失败锁定**
  对应验收标准：AC-3
  路由建议标签：`business-logic`（`[INFER]`，状态迁移判断，内聚在User表+AuthService）
  预计涉及文件（概要）：`schema.sql`（修改，User表`ALTER TABLE ADD COLUMN`新增失败计数与锁定截止字段）、`User.java`/`UserRepository.java`/`AuthService.java`（修改）
  验收标准：
  - [ ] 连续5次错误后第6次拒绝且不再校验密码；15分钟后自动恢复

- [ ] **T00104：业务规则——记住我/会话时长差异化**
  对应验收标准：AC-4
  路由建议标签：`business-logic`（`[INFER]`，仅影响Session表expiresAt写入值的条件分支）
  预计涉及文件（概要）：`LoginRequest.java`（修改，加`rememberMe`字段）、`AuthService.java`/`SessionRepository.java`（修改）、`client/src/App.tsx`（修改，加勾选框）
  验收标准：
  - [ ] 勾选→7天有效期；不勾选→2小时有效期

- [ ] **T00105：非核心工作——登录成功/失败埋点**
  对应验收标准：AC-5
  路由建议标签：`crud`（`[FACT]`，可直接复用`TodoRepository.create`已确立的"INSERT后RETURNING"写法）
  预计涉及文件（概要）：`schema.sql`（修改，新增LoginEvent表）、新增`LoginEventRepository.java`、`AuthService.java`（修改，各分支追加埋点调用）
  验收标准：
  - [ ] 四类结果分支各产生一条可区分事件类型的记录；埋点异常不阻塞登录主流程

- [ ] **T00106：发布收尾——工作台访问保护全量开启 + Flag清理**
  对应验收标准：AC-1～AC-5（回归）
  路由建议标签：`refactor`（`[INFER]`，需把鉴权拦截套用到既有`TodoController`，跨越认证模块与既有业务模块）
  预计涉及文件（概要）：`AuthInterceptor.java`（修改，删除flag判断使其常态生效）、`WebMvcConfigurer`配置类（修改，无条件注册拦截器到`/api/todos/**`）、`application.yml`（修改，删除flag属性）、`client/src/App.tsx`（修改，删除flag判断）
  验收标准：
  - [ ] 无flag代码情况下AC-1～AC-5行为一致；仓库内`login-auth-enabled`/`login_auth_enabled`无残留引用；未登录访问`/api/todos`被拒绝

**特性开关设计**：类型Release Toggle；命名建议 `login-auth-enabled`（`[ASSUME]`，改用kebab-case匹配Spring Boot `application.yml`属性命名惯例，与v2版本的`login_auth_enabled`snake_case命名不同，因为这次要写进YAML属性而非环境变量，遵循Spring Boot官方属性命名风格）；翻转条件与v2版一致（T00101~T00105合并验证通过后开启，观察后执行T00106删除）。

**故事内反模式自检**：与v2版本一致，未触犯（技术栈切换不影响拆分粒度本身）。

---

## 假设与待确认项汇总

- `[ASSUME]` schema管理机制选型（`schema.sql`+`spring.sql.init.mode: always`）：如实现方计划改用Flyway等更正式的迁移工具，需在T00101开工前反馈
- `[ASSUME]` 密码哈希库选型（`spring-security-crypto`而非完整Security starter）：如实现方认为后续必然要上完整Spring Security（如未来要做多角色权限），可以提前改选完整starter，但需相应调整T00106拦截器的实现方式（改用Security Filter Chain而非自建Interceptor）
- `[ASSUME]` 会话凭证选型（自建Session表+Header token）：如实现方更熟悉JWT无状态方案，可替换，但需同步更新T00103锁定判断、T00106拦截器的具体实现细节
- `[FACT]` `server/prisma/dev.db`路径与目录名沿用旧Prisma遗留命名，未清理迁移到更贴切的路径（如`server/data/dev.db`），本次拆解不处理这项命名遗留问题，不在任何任务范围内，仅记录以免误认为是待办

---

## 如何生成单任务实现说明

见同目录下 `登录故事-单任务实现清单v3-SpringBoot-PRonly.md`，已按`单任务实现-subagent提示词.md`（v2）颗粒度直接生成全部6个任务的完整实现说明，CI/CD验收模式均为`PR-only`（自查确认仓库无`.github/workflows`等CI配置，按选择规则默认PR-only）。
