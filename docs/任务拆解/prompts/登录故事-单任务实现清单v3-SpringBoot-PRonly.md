# 登录故事 · 单任务实现说明全集（v3：Spring Boot后端，CI/CD=PR-only，直接生成未经子agent）

> 由我（Claude）依据 `单任务实现-subagent提示词.md`（v2）方法论，对本地工作目录（`/mnt/d/Programs/tasksplitting`，origin为`chuanminglu/tasksplitting`）做真实自查后直接生成6个任务的完整实现说明，不经过Agent工具子agent调用。
> 材料3（REPO_PATH）统一为本地工作目录本身；材料4（前序任务实际交付结果）**全部为空**——虽然后端脚手架已从Node迁移到Spring Boot（Todo CRUD已完成迁移），但登录故事本身（User/AuthController等）在当前代码库里仍是零代码，T00101～T00105均未实际交付，因此每个任务的"背景"都如实注明这一点，不假装前序任务已完成。
> 材料5（CI/CD验收模式）统一为 `PR-only`：自查确认仓库无`.github/workflows`等CI配置，按`单任务实现-subagent提示词.md`的选择规则默认PR-only。
> 全局技术选型（schema管理/密码哈希/会话凭证/鉴权校验方式/Feature Flag实现/前端落点/时间可测试性）已在同目录 `登录故事-任务清单v3-SpringBoot.md` 中统一确立，下方各任务直接引用，不重复论证依据。

---

## T00101：端到端骨架——最小登录成功路径跑通

你要做的事：搭建登录功能最薄的端到端骨架——用户输入正确的用户名密码后，能成功登录并进入工作台视图，同时补齐当前代码库缺失的数据库schema管理机制。
对应验收标准：AC-1（用户名密码正确 → 登录成功，跳转工作台）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第1/6个任务，也是本次迭代前的第一个任务。前置状态（已对本地工作目录自查确认）：`server/`已从Node+Express+Prisma迁移为Spring Boot 3.4.4（Java 17），`com.tasksplitting.api`包下已有`HealthController`/`Todo`/`TodoController`/`TodoRepository`/`TodoRequest`五个文件，采用手写SQL+`JdbcTemplate`风格（无ORM），无任何鉴权相关代码、依赖或数据库schema管理机制（`server/src/main/resources`下只有`application.yml`，无`schema.sql`）。本任务完成后，整体功能仍由 `app.feature.login-auth-enabled`（`application.yml`属性，Release Toggle）包裹，默认`false`，对用户不可见。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/schema.sql`（新增，`[FACT]`自查确认该文件当前不存在）
- `server/src/main/resources/application.yml`（修改，`[FACT]`当前只有`server.port`/`spring.application.name`/`spring.datasource`三项，需新增`spring.sql.init.mode: always`与`app.feature.login-auth-enabled: false`）
- `server/pom.xml`（修改，`[FACT]`当前依赖列表已确认无密码哈希相关库，需新增`org.springframework.security:spring-security-crypto`）
- `server/src/main/java/com/tasksplitting/api/User.java`（新增，`[INFER]`参照`Todo.java`的record风格）
- `server/src/main/java/com/tasksplitting/api/UserRepository.java`（新增，`[INFER]`参照`TodoRepository.java`的JdbcTemplate手写SQL风格）
- `server/src/main/java/com/tasksplitting/api/SessionRepository.java`（新增，`[ASSUME]`承载全局技术选型确定的自建会话表）
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（新增，`[ASSUME]`引入Service层，理由见"技术选型"）
- `server/src/main/java/com/tasksplitting/api/AuthController.java`（新增，`[INFER]`参照`TodoController.java`的`@RestController`+`@RequestMapping`+`@CrossOrigin`风格）
- `server/src/main/java/com/tasksplitting/api/LoginRequest.java`（新增，`[INFER]`参照`TodoRequest.java`的record风格，本阶段仅含`username`/`password`）
- `server/src/main/java/com/tasksplitting/api/LoginResponse.java`（新增，`[ASSUME]`，含`token`字段）
- `server/src/main/java/com/tasksplitting/TasksplittingApplication.java`（修改，`[ASSUME]`新增一个`Clock`类型的`@Bean`方法，供`AuthService`注入）
- `client/src/App.tsx`（修改，`[FACT]`当前是单一Todo视图，需新增登录表单与已登录/未登录条件渲染）

**可能改动**：
- `client/src/styles.css`（是否需要新增CSS class取决于登录表单是否复用现有`.todo-form`等既有样式，`[INFER]`，视实现时的具体表单结构而定）

### 技术选型

1. **数据库schema管理** → 新增`schema.sql`（`CREATE TABLE IF NOT EXISTS`）+ `application.yml`设置`spring.sql.init.mode: always`。依据：自查确认仓库当前无任何schema管理机制，是结构性缺口，必须补；不引入Flyway等迁移框架，因为pom.xml依赖极简，属于脚手架阶段，过度设计。`[FACT]`+`[ASSUME]`
2. **密码哈希** → 新增`spring-security-crypto`依赖，使用`BCryptPasswordEncoder`（**不**引入完整`spring-boot-starter-security`）。依据：避免引入默认登录页/CSRF/全局端点保护等副作用，与Walking Skeleton渐进开放节奏冲突。`[ASSUME]`
3. **会话凭证载体** → 自建`Session`表（`token`主键/`userId`/`expiresAt`/`createdAt`），`token`为服务端生成的随机不透明字符串（`UUID.randomUUID().toString()`），经响应体返回，后续请求经`Authorization: Bearer <token>`头携带。依据：直接复用`TodoRepository`已确立的JdbcTemplate手写SQL风格，不引入JWT等新技术类别；Header方案避免修改现有`TodoController`未开放`allowCredentials`的CORS配置。`[ASSUME]`
4. **是否引入Service层** → 引入`AuthService`，`AuthController`只做HTTP层转换，业务逻辑（密码校验、token生成、写Session）放在Service。依据：仓库现有`TodoController`把逻辑直接写在Controller里（无Service先例），但登录涉及的分支/状态判断复杂度明显高于Todo的单条件校验，且后续T00102～T00105都会在Service层继续叠加逻辑，提前分层可以避免`AuthController`很快膨胀；这是本任务范围内新增的最小必要分层，不引入额外抽象（如不引入Repository接口/事件总线）。`[ASSUME]`
5. **Feature Flag实现** → `application.yml`新增`app.feature.login-auth-enabled: false`，用`@Value("${app.feature.login-auth-enabled:false}")`直接注入`AuthController`。依据：仓库无配置中心先例，直接注入比新增`@ConfigurationProperties`类更贴合现有"没有额外配置封装类"的风格。`[ASSUME]`
6. **Flag关闭时的接口行为** → 返回`404 Not Found`（视为路由不存在，而非返回一个"功能已禁用"的语义化响应）。依据：`AuthController`可以在方法开头判断flag，为false时直接`return ResponseEntity.notFound().build()`，是Spring MVC里最少代码量的实现方式，且不向未授权探测者泄露"这个功能即将上线"的信息（对比返回503/自定义禁用提示会泄露更多信息）。`[ASSUME]`
7. **时间可测试性** → `AuthService`不直接调用`LocalDateTime.now()`，改为注入`Clock`（`TasksplittingApplication`新增`@Bean public Clock clock() { return Clock.systemDefaultZone(); }`），通过`LocalDateTime.now(clock)`取时间。依据：T00103需要在测试中模拟"15分钟后"这种时间流逝，Java无JS`vi.useFakeTimers()`式全局mock，标准做法是从一开始就用可替换的`Clock`；虽然T00101本身不需要mock时间，但必须在这里就落地，否则T00103要重构已写好的`AuthService`。`[ASSUME]`
8. **测试用户数据来源** → 不新增`data.sql`（避免生产环境也执行测试数据初始化），集成测试内部直接调用`UserRepository`的创建方法插入种子用户。依据：`data.sql`与`schema.sql`共用`spring.sql.init.mode`开关，无法只在测试环境生效而不影响开发环境；测试自己插入数据是更干净的隔离方式。`[ASSUME]`
9. **本阶段会话有效期** → 统一按2小时签发（不做"记住我"差异化，那是T00104的范围），避免提前实现AC-4。`[FACT]`（材料明确T00104才处理该差异化，本任务只需一个不区分的兜底值）

### 你要做什么

**后端**：
1. `schema.sql`新增：
   ```sql
   CREATE TABLE IF NOT EXISTS "User" (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     username TEXT NOT NULL UNIQUE,
     passwordHash TEXT NOT NULL,
     createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
   );
   CREATE TABLE IF NOT EXISTS "Session" (
     token TEXT PRIMARY KEY,
     userId INTEGER NOT NULL,
     expiresAt DATETIME NOT NULL,
     createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
   );
   ```
2. `application.yml`新增`spring.sql.init.mode: always`（顶层`spring:`下）与`app.feature.login-auth-enabled: false`（新增顶层`app.feature:`节点）。
3. `pom.xml`的`<dependencies>`新增`spring-security-crypto`（无需版本号，由`spring-boot-starter-parent`统一管理）。
4. `User.java`：`public record User(int id, String username, String passwordHash, LocalDateTime createdAt) {}`。
5. `UserRepository.java`：提供`findByUsername(String username)`（返回`Optional<User>`）与`create(String username, String passwordHash)`（INSERT后RETURNING id，再SELECT一次，与`TodoRepository.create`同构）两个方法。
6. `SessionRepository.java`：提供`create(String token, int userId, LocalDateTime expiresAt)`方法写入Session表。
7. `TasksplittingApplication.java`新增`Clock`的`@Bean`方法。
8. `AuthService.java`：`login(String username, String rawPassword)`方法——查`UserRepository.findByUsername`，不存在或密码校验（`BCryptPasswordEncoder.matches`）不通过，本阶段统一抛出一个笼统的登录失败异常（具体错误码细分留给T00102）；校验通过则生成token、写入Session（`expiresAt = LocalDateTime.now(clock).plusHours(2)`），返回token字符串。
9. `AuthController.java`：`@RestController @RequestMapping("/api/auth") @CrossOrigin(origins = "http://localhost:5173")`，`POST /login`接口，方法开头判断`loginAuthEnabled`为false时返回404；否则调用`AuthService.login`，成功返回`200` + `LoginResponse(token)`，失败返回`401`（本阶段错误体可以只含笼统message，不要求结构化错误码，那是T00102的范围）。
10. `LoginRequest.java`：`public record LoginRequest(String username, String password) {}`。
11. `LoginResponse.java`：`public record LoginResponse(String token) {}`。

**前端**（`client/src/App.tsx`）：
1. 新增组件级状态（如`token: string | null`，初始为`null`），未登录（`token`为`null`）时渲染登录表单（用户名+密码输入框+提交按钮），已登录时渲染现有Todo工作台视图（即把当前`App.tsx`已有内容整体挪到"已登录"分支下）。
2. 登录表单提交时`fetch('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })`，成功后把响应体的`token`存入state，触发视图切换。

### 从验收标准推导出的隐含规则
无，AC-1原文即为最简happy path，字面即完整，不需要额外推导。

### 明确不要做的事
- 不要实现账号不存在/密码错误的错误码细分（属于T00102）
- 不要实现连续失败锁定（属于T00103）
- 不要实现"记住我"与会话时长差异化（属于T00104，本任务的2小时是唯一档，不接受`rememberMe`参数）
- 不要实现登录埋点（属于T00105）
- 不要把鉴权校验套用到`TodoController`（属于T00106，本任务只新建接口，不影响既有Todo接口的可访问性）
- 不要引入完整`spring-boot-starter-security`（技术选型已排除）
- 不要在`User`/`Session`表之外新增业务表结构

### 完成定义（DoD）
- `[FACT]` 对应AC-1：集成测试内先用`UserRepository.create`插入一条测试用户，`POST /api/auth/login`携带其正确用户名密码（flag=true环境下），响应状态码200，响应体`token`字段非空字符串。
- `[FACT]` 对应AC-1：前端组件测试验证收到成功响应后，视图从登录表单切换为工作台视图（可用现有Todo列表容器是否渲染作为判定依据）。
- `[ASSUME]` flag=false时，`POST /api/auth/login`返回404（技术选型第6点选定的行为），有集成测试覆盖。
- `[INFER]` 登录成功后，`Session`表中存在一条`token`与响应体一致、`expiresAt`约为签发时刻+2小时的记录，有测试断言。
- `mvn test`（server）与`npm run typecheck`（client）均通过，不引入新的编译/类型错误。

**CI/CD验收模式：PR-only**（不追加额外断言，DoD到"改动通过现有测试、能独立形成PR"为止）

### 假设与待确认项
- `[ASSUME]` 密码哈希/会话凭证/Service分层/Flag实现方式/时间可测试性等技术选型，均基于代码库现状与业界惯例选定，材料未直接指定，如实现方计划采用其他方案（如换JWT、换完整Spring Security）需开工前反馈，避免T00102～T00106连锁返工
- `[ASSUME]` flag关闭时返回404而非503/自定义禁用响应，如产品期望更明确的"功能未开放"提示，需调整

### 交付要求
改动应能独立形成一个PR，范围只覆盖本任务。完成后返回：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00102：失败路径分支——账号不存在/密码错误分别提示

你要做的事：把T00101笼统的登录失败响应，细化为"账号不存在"与"密码错误"两类可区分的错误标识，前端据此展示不同文案。
对应验收标准：AC-2（账号不存在 / 密码错误 → 分别给出明确提示）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第2/6个任务。**前序任务T00101尚未实际交付**——已对本地工作目录自查确认，`com.tasksplitting.api`包下不存在`User.java`/`AuthController.java`等任何登录相关文件，`schema.sql`不存在。本说明基于T00101的规划预判展开，**派发前必须先确认T00101已合并**，并用其真实交付结果（`AuthService`失败分支的实际抛出方式、错误响应的实际结构）校准本说明，若结构不同以T00101实际交付为准。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，`[INFER]`基于T00101预判，在`login`方法内把笼统失败拆分为两类判断）
- `server/src/main/java/com/tasksplitting/api/AuthController.java`（修改，`[INFER]`需要把Service层区分出的两类失败映射为不同响应体）
- `client/src/App.tsx`（修改，`[INFER]`依赖T00101产出的登录表单，按错误码渲染不同文案）

**可能改动**：无。

### 技术选型

1. **失败传递机制** → 新增`server/src/main/java/com/tasksplitting/api/AuthException.java`（`RuntimeException`子类，携带`String code`字段），`AuthService`分别用`USER_NOT_FOUND`/`INVALID_PASSWORD`两个code抛出；`AuthController`新增`@ExceptionHandler(AuthException.class)`方法统一捕获并转成`ResponseEntity.status(401).body(Map.of("error", Map.of("code", ex.getCode(), "message", ...)))`。依据：仓库现有`TodoController`对失败的处理是直接在方法体内`if`判断后手写`ResponseEntity.badRequest()`，量级很小；本任务需要在Service和Controller两层之间传递结构化错误语义，异常+`@ExceptionHandler`是Spring MVC里表达"业务失败短路并统一转换响应"的标准做法，比让`AuthService`返回一个自定义Result包装类更贴合Spring生态惯例。`[ASSUME]`，与`TodoController`的极简先例不同，是因为登录场景的错误分支比Todo的单一校验更复杂，值得引入这一层标准化机制。
2. **错误码命名** → `USER_NOT_FOUND`（账号不存在）、`INVALID_PASSWORD`（密码错误）。依据：与AC-2原文"账号不存在/密码错误"逐字对应。`[INFER]`
3. **HTTP状态码** → 两类失败均返回`401`，只有响应体`code`不同。依据：401是认证失败的标准语义，业务上"是否分别提示"通过`code`字段区分即可，不需要通过状态码区分。`[INFER]`

### 你要做什么

**后端**：
- `AuthService.login`：先按`username`查`UserRepository.findByUsername`，查不到抛`AuthException("USER_NOT_FOUND", ...)`；查到但`BCryptPasswordEncoder.matches`不通过，抛`AuthException("INVALID_PASSWORD", ...)`；密码校验通过的成功路径不变（沿用T00101逻辑）。
- `AuthController`新增`@ExceptionHandler(AuthException.class)`方法，返回`401` + 响应体`{ "error": { "code": ..., "message": ... } }`。

**前端**：
- 登录请求收到401响应后，解析`error.code`，按映射表渲染：`USER_NOT_FOUND`→"账号不存在"，`INVALID_PASSWORD`→"密码错误"；在表单下方单一提示区域展示，不逐字段展示。

### 从验收标准推导出的隐含规则
无额外隐含规则；AC-2字面已经要求"分别给出明确提示"，两类错误码互不相同即满足，不需要额外推导分支。（是否需要防时序攻击等安全加固不在AC字面要求内，已记入"假设与待确认项"而非强制隐含规则，避免超出本任务范围的过度设计。）

### 明确不要做的事
- 不要实现失败次数计数/账号锁定（属于T00103）
- 不要实现"记住我"（属于T00104）
- 不要实现登录埋点（属于T00105）
- 不要修改flag状态
- 不要改动T00101已实现的登录成功路径
- **若派发时T00101实际尚未合并，暂停并报告，不要绕过依赖自行搭建登录基础设施**

### 完成定义（DoD）
- `[FACT]` 对应AC-2：集成测试传入不存在的用户名，响应401，`error.code == "USER_NOT_FOUND"`。
- `[FACT]` 对应AC-2：集成测试传入存在用户名+错误密码，响应401，`error.code == "INVALID_PASSWORD"`，且与上一条不同。
- `[FACT]` 对应AC-2：前端收到两类code时分别展示不同文案，有组件测试覆盖。

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00101实际交付后，`AuthService`失败分支的真实抛出方式需要核对，若T00101采用了不同于"直接return一个笼统异常"的实现（比如返回`Optional`/自定义Result类），本任务需要相应调整落点，而非硬套本说明的`AuthException`机制。
- `[ASSUME]` 未做时序攻击防护（两类失败响应耗时可能有差异，理论上可被用于判断账号是否存在），AC-2原文未提及安全合规要求，暂不处理，如有合规要求需另立任务。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致（尤其是异常机制与T00101真实交付的衔接方式）、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00103：业务规则——连续失败锁定

你要做的事：实现连续5次密码错误后锁定账号15分钟、锁定期满自动恢复的规则。
对应验收标准：AC-3（连续5次密码错误 → 锁定账号15分钟）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第3/6个任务。**前序任务T00101、T00102均尚未实际交付**（自查同T00102的结论）。本说明基于规划预判展开，派发前需先确认两者已合并，尤其要核对`AuthService`的失败分支结构与`User`表的真实字段。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/schema.sql`（修改，`[INFER]`在T00101建立的`User`表定义之后追加`ALTER TABLE`语句）
- `server/src/main/java/com/tasksplitting/api/User.java`（修改，`[INFER]`record新增`failedLoginAttempts`/`lockedUntil`字段）
- `server/src/main/java/com/tasksplitting/api/UserRepository.java`（修改，`[INFER]`新增更新失败计数/锁定字段的方法）
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，`[INFER]`新增锁定态判断与计数递增逻辑）

**可能改动**：无。

### 技术选型

1. **schema变更方式** → 在`schema.sql`追加：
   ```sql
   ALTER TABLE "User" ADD COLUMN IF NOT EXISTS failedLoginAttempts INTEGER NOT NULL DEFAULT 0;
   ALTER TABLE "User" ADD COLUMN IF NOT EXISTS lockedUntil DATETIME;
   ```
   依据：延续T00101确立的"schema.sql累加式演进"全局约定（sqlite-jdbc 3.49.1.0捆绑的SQLite版本支持`ADD COLUMN IF NOT EXISTS`），不引入迁移工具。`[FACT]`引用T00101已确立的约定。
2. **锁定响应格式** → `423 Locked`状态码 + 响应体`{ "error": "ACCOUNT_LOCKED", "lockedUntil": "<ISO8601>" }`。依据：423是HTTP状态码注册表中精确对应"资源当前锁定"的语义，与T00102已用于区分账号不存在/密码错误的401互不冲突；返回绝对时间戳而非预先算好的剩余秒数，避免网络延迟/客户端时钟漂移造成偏差，调用方自行用`lockedUntil - 当前时间`推算剩余时长。`[ASSUME]`

### 你要做什么

**后端**：
- `AuthService.login`中，在查到`User`之后、密码比对之前，先判断`lockedUntil`是否非空且晚于`LocalDateTime.now(clock)`：是则直接抛`AuthException("ACCOUNT_LOCKED", ...)`（`AuthController`需要新增对该code返回423而非401的分支，或在`AuthException`里携带期望状态码），不进行密码比对，不修改计数字段。
- 密码比对：错误时`failedLoginAttempts += 1`并写回`UserRepository`；若递增后达到5，在**同一次请求**内同时把`lockedUntil`设为`LocalDateTime.now(clock).plusMinutes(15)`并写回，本次响应直接返回`ACCOUNT_LOCKED`（而非`INVALID_PASSWORD`）；未达到5则按T00102逻辑返回`INVALID_PASSWORD`。
- 密码比对成功：登录成功前先把`failedLoginAttempts`重置为0（`lockedUntil`此时应为空，因为锁定态已在前面被拦截，不会走到这里）。

### 从验收标准推导出的隐含规则
1. `[INFER]` 连续失败次数被一次成功登录打断后清零——AC-3使用"连续"一词，若成功登录不清零，"连续5次"会退化为历史累计，与字面语义矛盾。
2. `[INFER]` 锁定检查必须发生在密码比对之前——避免对已锁定账号做无意义的哈希比对运算，且避免给出可用于旁路判断的时序信号。
3. `[INFER]` 锁定期内的重复请求不会延长/重置`lockedUntil`，也不会继续递增`failedLoginAttempts`——否则锁定期内的持续尝试（含攻击者）会让账号永远无法按期恢复，与AC-3"锁定15分钟"这一确定时长矛盾。
4. `[INFER]` 触发锁定的第5次失败请求本身，响应应直接是`ACCOUNT_LOCKED`而非`INVALID_PASSWORD`——计数递增到达阈值与转入锁定状态在同一次请求处理中完成。

### 明确不要做的事
- 不要实现"记住我"与会话时长差异化（属于T00104）
- 不要实现登录埋点，包括锁定事件的埋点（属于T00105）
- 不要修改flag状态
- 不要改动T00102已建立的`USER_NOT_FOUND`/`INVALID_PASSWORD`判断逻辑，只新增`ACCOUNT_LOCKED`这一条新分支
- 不要对`User`表做锁定字段之外的schema重构
- 若T00101、T00102尚未合并，暂停并报告

### 完成定义（DoD）
- `[FACT]` 连续5次密码错误后，第5次错误请求本身即返回423 + `ACCOUNT_LOCKED`（而非普通密码错误），有集成测试覆盖。
- `[FACT]` 锁定状态下第6次及之后请求（无论密码是否正确）均返回423 + `ACCOUNT_LOCKED`，不执行密码比对，有测试覆盖。
- `[FACT]` 锁定响应体`lockedUntil`字段值约为触发锁定时刻+15分钟（15分钟取自AC-3原文），有测试断言。
- `[INFER]` 用注入的固定`Clock`模拟"15分钟后"，正确密码可重新登录成功且`failedLoginAttempts`归零，有测试覆盖，不真实等待15分钟。
- `[INFER]` 错2次后登录成功，之后新的错误从0重新计数，有测试覆盖。
- `[INFER]` 锁定期内二次请求，`lockedUntil`与首次触发锁定时一致（未被推迟），有测试覆盖。

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00101、T00102真实交付后，`AuthService`内部结构（尤其是异常携带状态码的方式）需要核对，若与本说明假设不同需调整落点，但423状态码、字段命名、四条隐含规则本身不受影响。
- `[ASSUME]` 锁定相关字段命名（`failedLoginAttempts`/`lockedUntil`）与响应格式为本说明选定，材料未规定具体命名规范。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00104：业务规则——记住我/会话时长差异化

你要做的事：实现"记住我"勾选后会话保持7天、不勾选则2小时过期的差异化会话时长。
对应验收标准：AC-4（勾选"记住我" → 7天内免登录；不勾选 → 2小时会话）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第4/6个任务。**前序任务T00101～T00103均尚未实际交付**（自查同前）。T00101已按规划为"记住我"预留了统一2小时的兜底会话时长，本任务在其基础上引入差异化。派发前需先确认T00101～T00103已合并，并核对`SessionRepository`/`LoginRequest`的真实字段结构。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/LoginRequest.java`（修改，`[INFER]`新增`rememberMe`布尔字段）
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，`[INFER]`按`rememberMe`设置不同`expiresAt`）
- `client/src/App.tsx`（修改，`[INFER]`新增"记住我"勾选框并随登录请求提交）

**可能改动**：无。

### 技术选型

1. **"记住我"参数透传方式** → `LoginRequest`新增`rememberMe`（布尔，默认`false`），随登录请求体一并提交。依据：登录请求本身就是JSON body，新增一个布尔字段是最小侵入的做法。`[INFER]`
2. **过期时间取值** → `rememberMe=true`时`expiresAt = now.plusDays(7)`；`false`或未传时`expiresAt = now.plusHours(2)`（与T00101兜底值一致，只是从"唯一档"变成"其中一档"）。依据：AC-4原文直接给出7天/2小时两个数值。`[FACT]`

### 你要做什么

**后端**：`AuthService.login`新增`boolean rememberMe`参数，登录成功分支根据其值计算`expiresAt`（7天 or 2小时）后再调用`SessionRepository.create`。

**前端**：登录表单新增"记住我"复选框，默认不勾选，随登录请求的`rememberMe`字段一并提交。

### 从验收标准推导出的隐含规则
`[INFER]` AC-4字面只定义了"勾选"与"不勾选"两种输入对应的两个数值，未定义"未传该字段"时的行为；按照"不勾选"对应"2小时"的表述，推导出未传字段时同样归入2小时分支（与T00101已确立的默认值天然一致，不需要额外处理）。

### 明确不要做的事
- 不要实现登录埋点（属于T00105）
- 不要修改flag状态
- 不要改动T00103已实现的账号锁定逻辑
- 不要新增除"记住我"外的其他登录表单字段
- 若T00101～T00103尚未合并，暂停并报告

### 完成定义（DoD）
- `[FACT]` 对应AC-4：`rememberMe=true`登录成功后，`Session`记录的`expiresAt`与签发时间之差约为7天，有测试断言。
- `[FACT]` 对应AC-4：`rememberMe=false`或不传，`expiresAt`与签发时间之差约为2小时，有测试断言。
- `[INFER]` 前端"记住我"勾选框默认未勾选，勾选后提交的请求体含`rememberMe: true`。

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00101～T00103真实交付后，`AuthService.login`方法签名与`SessionRepository.create`的真实参数需要核对。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00105：非核心工作——登录成功/失败埋点

你要做的事：为登录成功、账号不存在、密码错误、账号锁定四类结果分支分别打点，供运营看板统计。
对应验收标准：AC-5（登录成功/失败需要埋点，供运营看板统计）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第5/6个任务。**前序任务T00101～T00104均尚未实际交付**（自查同前）。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/schema.sql`（修改，`[INFER]`追加`LoginEvent`表定义）
- `server/src/main/java/com/tasksplitting/api/LoginEventRepository.java`（新增，`[FACT]`可直接复用`TodoRepository.create`已确立的"INSERT后RETURNING"写法）
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，`[INFER]`在成功/账号不存在/密码错误/锁定四个分支各自追加埋点调用）

**可能改动**：无。

### 技术选型

1. **埋点投递方式** → 落库到新增`LoginEvent`表（不引入日志采集/消息队列等外部依赖）。依据：直接复用仓库现有的`JdbcTemplate`手写SQL能力，不新增技术类别，与`TodoRepository`风格一致。`[FACT]`引用既有代码模式。
2. **事件字段** → `id`（自增主键）、`eventType`（TEXT，取值`LOGIN_SUCCESS`/`USER_NOT_FOUND`/`INVALID_PASSWORD`/`ACCOUNT_LOCKED`，与T00102/T00103已使用的错误码保持一致命名）、`username`（TEXT，可为空——账号不存在场景下仍记录用户尝试输入的用户名，便于风控分析）、`createdAt`。依据：AC-5只要求"供运营看板统计"，最基本需要能区分结果类型+发生时间，字段设计取满足这个最小需求的集合，不做过度设计。`[UNKNOWN]`运营看板实际消费所需的完整字段清单材料未说明，只保证最基本字段齐备。
3. **埋点失败不阻塞主流程** → `LoginEventRepository`的写入调用包裹在`try/catch`内，捕获所有异常仅记录日志（如`System.err`或后续接入的日志框架），不向上抛出。依据：AC-5的验收细项明确要求"埋点写入失败不阻塞登录主流程返回"。`[FACT]`

### 你要做什么

**后端**：
1. `schema.sql`追加：
   ```sql
   CREATE TABLE IF NOT EXISTS "LoginEvent" (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     eventType TEXT NOT NULL,
     username TEXT,
     createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
   );
   ```
2. `LoginEventRepository.java`：`record(String eventType, String username)`方法，内部INSERT一条记录；调用方（`AuthService`）负责包裹`try/catch`。
3. `AuthService.login`：在成功返回前、`USER_NOT_FOUND`/`INVALID_PASSWORD`/`ACCOUNT_LOCKED`三类异常抛出前，分别调用一次`LoginEventRepository.record`，四处调用均包裹`try/catch`。

**前端**：本任务不涉及。依据：AC-5的验收细项描述的均是后端埋点行为，材料1"预计涉及文件概要"未列出前端文件。

### 从验收标准推导出的隐含规则
`[INFER]` AC-5原文只说"登录成功/失败需要埋点"，未逐类列举失败的细分类型；结合T00102/T00103已把失败拆成"账号不存在/密码错误/账号锁定"三种，推导出四类分支（成功+三类失败）各自都需要打点，而不是笼统的"成功/失败"两类——因为运营看板要统计的通常是"哪类失败最多"这种细粒度信息，只打"成功/失败"两类会丢失T00102/T00103已经建立的细分能力。

### 明确不要做的事
- 不要修改flag状态
- 不要改动T00101～T00104已实现的登录判断逻辑本身，只在其结果分支上追加埋点调用
- 不要实现运营看板前端页面（不在本故事范围内）
- 若T00101～T00104尚未合并，暂停并报告

### 完成定义（DoD）
- `[FACT]` 登录成功、账号不存在、密码错误、账号锁定四种场景，各自有测试断言对应`LoginEvent`记录被写入一次，且`eventType`可区分。
- `[FACT]` 模拟`LoginEventRepository`写入抛异常时，登录接口本身仍正常返回原有响应（不因埋点失败而500），有测试覆盖。

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `[UNKNOWN]` 运营看板实际消费`LoginEvent`所需的完整字段清单，材料未说明，当前只保证`eventType`/`username`/`createdAt`三个最基本字段。
- `NEEDS CLARIFICATION`：T00101～T00104真实交付后，`AuthService`四个分支的真实代码位置需要核对。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00106：发布收尾——工作台访问保护全量开启 + Flag清理

你要做的事：在T00101～T00105全部合并后，把登录态校验套用到既有`TodoController`，并从代码库中物理删除`app.feature.login-auth-enabled`开关。
对应验收标准：AC-1～AC-5（回归，本任务不改变其行为，仅要求flag关闭前后行为一致，外加"未登录不可访问工作台"这一新增的默认行为）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第6/6个任务，也是最后一个任务。**前序任务T00101～T00105均尚未实际交付**（自查同前，代码库中不存在任何`login-auth-enabled`或鉴权相关代码）。这意味着本任务当前不具备可执行条件——"删除开关"这件事目前无对象可删，本说明是**T00101～T00105合并后应如何执行**的预案。本任务完成后，`app.feature.login-auth-enabled`将从代码库中物理移除，登录与工作台访问保护成为系统默认行为。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/AuthInterceptor.java`（新增于T00101～T00105期间，本任务修改，`[INFER]`删除内部flag判断使其常态生效）——若T00101～T00105实际交付时未按本系列说明建立独立`AuthInterceptor`而是采用了其他鉴权校验方式，以其实际实现为准
- Spring MVC配置类（`[INFER]`，可能是新增的`WebMvcConfig implements WebConfigurer`，也可能内联在`TasksplittingApplication`——需以T00101～T00105实际交付为准；本任务需要无条件把该拦截器注册到`/api/todos/**`路径）
- `server/src/main/resources/application.yml`（修改，删除`app.feature.login-auth-enabled`属性）
- `client/src/App.tsx`（修改，删除flag判断分支，未登录访问工作台视图默认重定向/拦截）

**可能改动**：
- 若T00101～T00105期间为flag专门写过针对性单元测试（如"flag关闭时保持旧行为"的测试用例），这些测试需要一并删除或改写，`[UNKNOWN]`具体是否存在此类测试需在真实交付后确认

### 技术选型
本任务范围内不涉及新的技术选型决策——拦截器的拦截方式、前端重定向方式均沿用T00101～T00105已落地的具体实现，本任务只是把"flag开启才生效"改为"始终生效"并删除判断代码本身。

### 你要做什么

前提：确认T00101～T00105均已合并到主干。

**后端**：
1. 在`AuthInterceptor`（或T00101～T00105实际采用的鉴权校验组件）中，删除内部对`app.feature.login-auth-enabled`的读取与判断分支，使鉴权校验始终生效。
2. 在Spring MVC配置中，把该拦截器/校验逻辑无条件应用到`/api/todos/**`路径（即`TodoController`当前的`GET`/`POST`接口，届时如有更多工作台路由一并覆盖）。
3. 删除`application.yml`中的`app.feature.login-auth-enabled`属性。

**前端**：
1. 在`App.tsx`中删除对该flag的判断分支，未登录（无有效token）访问工作台视图时默认展示登录表单（即让"是否展示工作台"完全由"是否有token"决定，不再有flag这个额外维度）。

**通用**：
2. 全仓库全文搜索`login-auth-enabled`（含配置文件、代码、注释、测试用例中的引用），确认清理完成。

### 从验收标准推导出的隐含规则
1. `[INFER]` AC-1～AC-5"回归"意味着flag删除前后对同一操作序列的响应必须逐条比对一致，不能只跑一遍测试通过就算数。
2. `[INFER]` "未登录状态下访问工作台相关路由被拒绝"这条要求`TodoController`现有的`GET`/`POST /api/todos`必须被完全纳入保护范围，不能遗漏——清理完成后需要对这两个既有接口逐一验证保护生效。
3. `[INFER]` "全文搜索不再有任何引用"包括测试代码中为该flag写过的针对性测试，这些测试本身也应删除或改写，不能留下引用已删除flag的死测试代码。

### 明确不要做的事
- 不要在本任务中新增/修改AC-1～AC-5已实现的业务逻辑本身（密码校验规则、锁定时长、会话时长等均不在本任务范围）
- 不要顺带清理与本故事无关的其他历史配置
- 不要跳过"确认无引用"这一步就直接删除
- 本任务在T00101～T00105实际合并前不应被派发执行

### 完成定义（DoD）
- 仓库全文搜索`login-auth-enabled`命中数为0（本说明文档本身除外）。
- `TodoController`相关的`/api/todos`路由（GET/POST）在无flag代码的情况下，未携带有效`Authorization`头访问时返回401/403拒绝，有集成测试覆盖。
- AC-1～AC-5对应的既有测试用例，在删除flag代码后全部通过，测试结果与T00101～T00105交付时记录的验收结果一致。
- `application.yml`中不再包含该flag声明。

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00101～T00105实际交付后，拦截器/校验组件的确切实现方式与文件位置需要重新核实，本说明"预计涉及文件"部分列出的具体路径不能直接照搬，需以当时代码库为准重新自查。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方，以及开关清理的确认方式（全文搜索命中数为0的实际执行记录）。
