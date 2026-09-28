# 登录故事 · GitHub Copilot（qwen3.8:27b）执行计划

> 基于 `登录故事-任务清单v3-SpringBoot.md` + `登录故事-单任务实现清单v3-SpringBoot-PRonly.md` + `qwen本地模型Benchmark验证方案.md` 第九章直接生成，未经子agent。
> 用途：把6个任务逐一在 VS Code Copilot Chat（Agent模式，模型选 `qwen3.8:27b`）里跑通，每步的提示词已经组装好，复制粘贴即可，不需要再手动拼接背景材料。
> CI/CD验收模式：全部`PR-only`。本阶段不接入spec-verifier。

---

## 一、环境准备（一次性，跑Step 1前先做）

1. VS Code Copilot Chat 模型选择器里，通过 "Manage Models"（或对应版本菜单）把 Ollama endpoint 指向 `http://192.168.2.11:11434`，选中 `qwen3.8:27b`。接入后先问它"你是什么模型"确认真的在用这个模型响应。
2. 聊天模式切到 **Agent 模式**（不是Ask/Edit模式）——本计划要求它自己改文件、跑`mvn test`/`npm run typecheck`、看结果再修，只有Agent模式能自主做这些。
3. 确认 VS Code 打开的工作区根目录就是 `/mnt/d/Programs/tasksplitting`（这样Copilot Agent自带的文件读写/终端能力才能直接操作真实代码库，不需要额外配置REPO_PATH）。
4. 记录本次用的Copilot版本号（后续如果结果异常，需要能追溯是不是版本差异导致）。

## 二、执行总纪律（每个任务都遵守）

- **严格按 T00101 → T00106 顺序逐个执行，不要跳过、不要并行**——每个任务都依赖前一个任务的真实交付结果，跳过顺序会导致后面任务的"背景"描述与实际代码库不符。
- **每个任务开一个新的Copilot Chat会话**（不要在同一个连续对话里接着做下一个任务），避免上下文污染，也更贴近"独立评估单任务执行能力"的口径。
- 每个任务的提示词已经包含"必须自己跑测试、不允许假装完成"的强制要求，正常情况不需要你中途插话；如果Copilot中途向你提问且不是`NEEDS CLARIFICATION`开头的强制澄清，可以直接说"按你的合理假设继续，不用等我确认"。
- **任务做完、测试真的跑通后，你自己核对一遍"完成定义（DoD）"每一条是否真的满足**，不要只看Copilot自己说"完成了"就当真。
- 核对通过后执行 git 提交（不自动拉取远程仓库，只在本地提交后push，具体命令见每个任务末尾的"提交"部分）。
- 如果某个任务Copilot卡住、连续几轮都过不了测试，先如实记录卡在哪一步，不要一直重试到跑通为止再继续——卡住本身就是有用的信息（对照 `qwen本地模型Benchmark验证方案.md` 的失败归因思路）。

---

## Step 1 / T00101：端到端骨架——最小登录成功路径跑通

**这一步做什么**：从零搭建登录接口 + 登录表单，同时补上项目当前缺失的数据库schema管理机制（`schema.sql`）。这是6步里改动面最大的一步。

**复制以下全部内容，粘贴进新开的Copilot Chat（Agent模式）**：

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

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
````

**Copilot完成后你要做的事**：
1. 逐条核对上面"完成定义（DoD）"是否真的满足（自己看一眼测试代码和输出，不要只信汇报文字）。
2. 通过后提交（工作目录已是git仓库，origin指向`chuanminglu/tasksplitting`，不需要fetch/pull）：
   ```
   git add -A
   git commit -m "feat(T00101): 端到端骨架——最小登录成功路径跑通 (AC-1)"
   git push
   ```

---

## Step 2 / T00102：失败路径分支——账号不存在/密码错误分别提示

**这一步做什么**：把T00101笼统的401细化成"账号不存在"/"密码错误"两种可区分提示。

**复制以下全部内容，粘贴进新开的Copilot Chat（Agent模式）**：

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00102：失败路径分支——账号不存在/密码错误分别提示

你要做的事：把T00101笼统的登录失败响应，细化为"账号不存在"与"密码错误"两类可区分的错误标识，前端据此展示不同文案。
对应验收标准：AC-2（账号不存在 / 密码错误 → 分别给出明确提示）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第2/6个任务。前置状态：T00101已合并，`AuthService`/`AuthController`/`User`表等已存在，登录成功路径已跑通，登录失败目前只有一个笼统的401。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。派发前请先用当前工作区实际代码核对`AuthService`的失败分支真实实现方式，若与下方"预计涉及文件/技术选型"的假设不同，以实际代码为准调整落点。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，在`login`方法内把笼统失败拆分为两类判断）
- `server/src/main/java/com/tasksplitting/api/AuthController.java`（修改，把Service层区分出的两类失败映射为不同响应体）
- `client/src/App.tsx`（修改，依赖T00101产出的登录表单，按错误码渲染不同文案）

**可能改动**：无。

### 技术选型

1. **失败传递机制** → 新增`server/src/main/java/com/tasksplitting/api/AuthException.java`（`RuntimeException`子类，携带`String code`字段），`AuthService`分别用`USER_NOT_FOUND`/`INVALID_PASSWORD`两个code抛出；`AuthController`新增`@ExceptionHandler(AuthException.class)`方法统一捕获并转成`ResponseEntity.status(401).body(Map.of("error", Map.of("code", ex.getCode(), "message", ...)))`。依据：仓库现有`TodoController`对失败的处理是直接在方法体内`if`判断后手写`ResponseEntity.badRequest()`，量级很小；本任务需要在Service和Controller两层之间传递结构化错误语义，异常+`@ExceptionHandler`是Spring MVC里表达"业务失败短路并统一转换响应"的标准做法。`[ASSUME]`
2. **错误码命名** → `USER_NOT_FOUND`（账号不存在）、`INVALID_PASSWORD`（密码错误）。依据：与AC-2原文"账号不存在/密码错误"逐字对应。`[INFER]`
3. **HTTP状态码** → 两类失败均返回`401`，只有响应体`code`不同。依据：401是认证失败的标准语义，业务上"是否分别提示"通过`code`字段区分即可。`[INFER]`

### 你要做什么

**后端**：
- `AuthService.login`：先按`username`查`UserRepository.findByUsername`，查不到抛`AuthException("USER_NOT_FOUND", ...)`；查到但`BCryptPasswordEncoder.matches`不通过，抛`AuthException("INVALID_PASSWORD", ...)`；密码校验通过的成功路径不变（沿用T00101逻辑）。
- `AuthController`新增`@ExceptionHandler(AuthException.class)`方法，返回`401` + 响应体`{ "error": { "code": ..., "message": ... } }`。

**前端**：
- 登录请求收到401响应后，解析`error.code`，按映射表渲染：`USER_NOT_FOUND`→"账号不存在"，`INVALID_PASSWORD`→"密码错误"；在表单下方单一提示区域展示，不逐字段展示。

### 从验收标准推导出的隐含规则
无额外隐含规则；AC-2字面已经要求"分别给出明确提示"，两类错误码互不相同即满足。（是否需要防时序攻击等安全加固不在AC字面要求内，已记入"假设与待确认项"而非强制隐含规则。）

### 明确不要做的事
- 不要实现失败次数计数/账号锁定（属于T00103）
- 不要实现"记住我"（属于T00104）
- 不要实现登录埋点（属于T00105）
- 不要修改flag状态
- 不要改动T00101已实现的登录成功路径
- 若发现T00101实际尚未合并（代码库里没有`AuthService`等文件），暂停并报告，不要绕过依赖自行搭建登录基础设施

### 完成定义（DoD）
- `[FACT]` 对应AC-2：集成测试传入不存在的用户名，响应401，`error.code == "USER_NOT_FOUND"`。
- `[FACT]` 对应AC-2：集成测试传入存在用户名+错误密码，响应401，`error.code == "INVALID_PASSWORD"`，且与上一条不同。
- `[FACT]` 对应AC-2：前端收到两类code时分别展示不同文案，有组件测试覆盖。

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `[ASSUME]` 未做时序攻击防护（两类失败响应耗时可能有差异），AC-2原文未提及安全合规要求，暂不处理。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后你要做的事**：
1. 核对DoD三条。
2. 提交：
   ```
   git add -A
   git commit -m "feat(T00102): 失败路径分支——账号不存在/密码错误分别提示 (AC-2)"
   git push
   ```

---

## Step 3 / T00103：业务规则——连续失败锁定

**这一步做什么**：连续5次密码错误锁定15分钟，锁定期满自动恢复。这是隐含规则最多、风险最高的一步。

**复制以下全部内容，粘贴进新开的Copilot Chat（Agent模式）**：

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00103：业务规则——连续失败锁定

你要做的事：实现连续5次密码错误后锁定账号15分钟、锁定期满自动恢复的规则。
对应验收标准：AC-3（连续5次密码错误 → 锁定账号15分钟）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第3/6个任务。前置状态：T00101、T00102已合并，登录接口已能区分"账号不存在"/"密码错误"两类失败，尚无失败次数记录与锁定机制。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。派发前请核对`AuthService`真实的失败分支结构与`User`表真实字段，以实际代码为准。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/schema.sql`（修改，在T00101建立的`User`表定义之后追加`ALTER TABLE`语句）
- `server/src/main/java/com/tasksplitting/api/User.java`（修改，record新增`failedLoginAttempts`/`lockedUntil`字段）
- `server/src/main/java/com/tasksplitting/api/UserRepository.java`（修改，新增更新失败计数/锁定字段的方法）
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，新增锁定态判断与计数递增逻辑）

**可能改动**：无。

### 技术选型

1. **schema变更方式** → 在`schema.sql`追加：
   ```sql
   ALTER TABLE "User" ADD COLUMN IF NOT EXISTS failedLoginAttempts INTEGER NOT NULL DEFAULT 0;
   ALTER TABLE "User" ADD COLUMN IF NOT EXISTS lockedUntil DATETIME;
   ```
   依据：延续T00101确立的"schema.sql累加式演进"约定（sqlite-jdbc捆绑的SQLite版本支持`ADD COLUMN IF NOT EXISTS`），不引入迁移工具。
2. **锁定响应格式** → `423 Locked`状态码 + 响应体`{ "error": "ACCOUNT_LOCKED", "lockedUntil": "<ISO8601>" }`。依据：423精确对应"资源当前锁定"语义；返回绝对时间戳而非预算好的剩余秒数，避免时钟漂移偏差。

### 你要做什么

**后端**：
- `AuthService.login`中，在查到`User`之后、密码比对之前，先判断`lockedUntil`是否非空且晚于`LocalDateTime.now(clock)`：是则直接抛`AuthException("ACCOUNT_LOCKED", ...)`，不进行密码比对，不修改计数字段。
- 密码比对：错误时`failedLoginAttempts += 1`并写回；若递增后达到5，在**同一次请求**内同时把`lockedUntil`设为`LocalDateTime.now(clock).plusMinutes(15)`并写回，本次响应直接返回`ACCOUNT_LOCKED`（而非`INVALID_PASSWORD`）；未达到5则按T00102逻辑返回`INVALID_PASSWORD`。
- 密码比对成功：登录成功前先把`failedLoginAttempts`重置为0。

### 从验收标准推导出的隐含规则
1. 连续失败次数被一次成功登录打断后清零——"连续"一词若不清零会退化为历史累计，与字面语义矛盾。
2. 锁定检查必须发生在密码比对之前——避免对已锁定账号做无意义哈希比对，也避免时序信号泄露。
3. 锁定期内的重复请求不会延长/重置`lockedUntil`，也不会继续递增`failedLoginAttempts`——否则持续尝试会让账号永远无法按期恢复。
4. 触发锁定的第5次失败请求本身，响应应直接是`ACCOUNT_LOCKED`而非`INVALID_PASSWORD`——计数到阈值与转入锁定在同一次请求内完成。

### 明确不要做的事
- 不要实现"记住我"与会话时长差异化（属于T00104）
- 不要实现登录埋点，包括锁定事件的埋点（属于T00105）
- 不要修改flag状态
- 不要改动T00102已建立的`USER_NOT_FOUND`/`INVALID_PASSWORD`判断逻辑，只新增`ACCOUNT_LOCKED`这一条新分支
- 不要对`User`表做锁定字段之外的schema重构
- 若T00101、T00102实际尚未合并，暂停并报告

### 完成定义（DoD）
- `[FACT]` 连续5次密码错误后，第5次错误请求本身即返回423 + `ACCOUNT_LOCKED`（而非普通密码错误），有集成测试覆盖。
- `[FACT]` 锁定状态下第6次及之后请求均返回423 + `ACCOUNT_LOCKED`，不执行密码比对，有测试覆盖。
- `[FACT]` 锁定响应体`lockedUntil`字段值约为触发锁定时刻+15分钟，有测试断言。
- `[INFER]` 用注入的固定`Clock`模拟"15分钟后"，正确密码可重新登录成功且`failedLoginAttempts`归零，有测试覆盖，不真实等待15分钟。
- `[INFER]` 错2次后登录成功，之后新的错误从0重新计数，有测试覆盖。
- `[INFER]` 锁定期内二次请求，`lockedUntil`与首次触发锁定时一致，有测试覆盖。

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后你要做的事**：
1. 重点核对DoD后三条（打断清零/时钟mock/锁定期内不延长）——这些是隐含规则，最容易被漏掉。
2. 提交：
   ```
   git add -A
   git commit -m "feat(T00103): 业务规则——连续失败锁定 (AC-3)"
   git push
   ```

---

## Step 4 / T00104：业务规则——记住我/会话时长差异化

**这一步做什么**："记住我"勾选7天、不勾选2小时。

**复制以下全部内容，粘贴进新开的Copilot Chat（Agent模式）**：

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00104：业务规则——记住我/会话时长差异化

你要做的事：实现"记住我"勾选后会话保持7天、不勾选则2小时过期的差异化会话时长。
对应验收标准：AC-4（勾选"记住我" → 7天内免登录；不勾选 → 2小时会话）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第4/6个任务。前置状态：T00101～T00103已合并，登录接口已处理成功、账号不存在、密码错误、账号锁定四种情况，会话统一为2小时（T00101兜底值）。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。派发前请核对`SessionRepository`/`LoginRequest`真实字段结构。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/LoginRequest.java`（修改，新增`rememberMe`布尔字段）
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，按`rememberMe`设置不同`expiresAt`）
- `client/src/App.tsx`（修改，新增"记住我"勾选框并随登录请求提交）

**可能改动**：无。

### 技术选型

1. **"记住我"参数透传方式** → `LoginRequest`新增`rememberMe`（布尔，默认`false`），随登录请求体一并提交。
2. **过期时间取值** → `rememberMe=true`时`expiresAt = now.plusDays(7)`；`false`或未传时`expiresAt = now.plusHours(2)`。依据：AC-4原文直接给出7天/2小时两个数值。

### 你要做什么

**后端**：`AuthService.login`新增`boolean rememberMe`参数，登录成功分支根据其值计算`expiresAt`（7天 or 2小时）后再调用`SessionRepository.create`。

**前端**：登录表单新增"记住我"复选框，默认不勾选，随登录请求的`rememberMe`字段一并提交。

### 从验收标准推导出的隐含规则
AC-4字面只定义了"勾选"与"不勾选"两种输入对应的两个数值，未定义"未传该字段"时的行为；按照"不勾选"对应"2小时"的表述，推导出未传字段时同样归入2小时分支。

### 明确不要做的事
- 不要实现登录埋点（属于T00105）
- 不要修改flag状态
- 不要改动T00103已实现的账号锁定逻辑
- 不要新增除"记住我"外的其他登录表单字段
- 若T00101～T00103实际尚未合并，暂停并报告

### 完成定义（DoD）
- `[FACT]` 对应AC-4：`rememberMe=true`登录成功后，`Session`记录的`expiresAt`与签发时间之差约为7天，有测试断言。
- `[FACT]` 对应AC-4：`rememberMe=false`或不传，`expiresAt`与签发时间之差约为2小时，有测试断言。
- `[INFER]` 前端"记住我"勾选框默认未勾选，勾选后提交的请求体含`rememberMe: true`。

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后你要做的事**：
1. 核对DoD三条。
2. 提交：
   ```
   git add -A
   git commit -m "feat(T00104): 业务规则——记住我/会话时长差异化 (AC-4)"
   git push
   ```

---

## Step 5 / T00105：非核心工作——登录成功/失败埋点

**这一步做什么**：四类结果分支各打一条埋点，供运营看板统计。

**复制以下全部内容，粘贴进新开的Copilot Chat（Agent模式）**：

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00105：非核心工作——登录成功/失败埋点

你要做的事：为登录成功、账号不存在、密码错误、账号锁定四类结果分支分别打点，供运营看板统计。
对应验收标准：AC-5（登录成功/失败需要埋点，供运营看板统计）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第5/6个任务。前置状态：T00101～T00104已合并，登录接口已完整覆盖成功、账号不存在、密码错误、账号锁定、记住我差异化会话等行为分支，尚无任何埋点上报。本任务完成后，整体功能仍由`app.feature.login-auth-enabled`包裹，对用户不可见。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/schema.sql`（修改，追加`LoginEvent`表定义）
- `server/src/main/java/com/tasksplitting/api/LoginEventRepository.java`（新增，复用`TodoRepository.create`已确立的"INSERT后RETURNING"写法）
- `server/src/main/java/com/tasksplitting/api/AuthService.java`（修改，在成功/账号不存在/密码错误/锁定四个分支各自追加埋点调用）

**可能改动**：无。

### 技术选型

1. **埋点投递方式** → 落库到新增`LoginEvent`表（不引入日志采集/消息队列等外部依赖）。依据：直接复用仓库现有的`JdbcTemplate`手写SQL能力，与`TodoRepository`风格一致。
2. **事件字段** → `id`、`eventType`（TEXT，取值`LOGIN_SUCCESS`/`USER_NOT_FOUND`/`INVALID_PASSWORD`/`ACCOUNT_LOCKED`）、`username`（TEXT，可为空）、`createdAt`。
3. **埋点失败不阻塞主流程** → `LoginEventRepository`的写入调用包裹在`try/catch`内，捕获所有异常仅记录日志，不向上抛出。

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

**前端**：本任务不涉及。

### 从验收标准推导出的隐含规则
AC-5原文只说"登录成功/失败需要埋点"，未逐类列举失败的细分类型；结合T00102/T00103已把失败拆成三种，推导出四类分支（成功+三类失败）各自都需要打点，而不是笼统的"成功/失败"两类。

### 明确不要做的事
- 不要修改flag状态
- 不要改动T00101～T00104已实现的登录判断逻辑本身，只在其结果分支上追加埋点调用
- 不要实现运营看板前端页面（不在本故事范围内）
- 若T00101～T00104实际尚未合并，暂停并报告

### 完成定义（DoD）
- `[FACT]` 登录成功、账号不存在、密码错误、账号锁定四种场景，各自有测试断言对应`LoginEvent`记录被写入一次，且`eventType`可区分。
- `[FACT]` 模拟`LoginEventRepository`写入抛异常时，登录接口本身仍正常返回原有响应（不因埋点失败而500），有测试覆盖。

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后你要做的事**：
1. 核对DoD两条。
2. 提交：
   ```
   git add -A
   git commit -m "feat(T00105): 非核心工作——登录成功/失败埋点 (AC-5)"
   git push
   ```

---

## Step 6 / T00106：发布收尾——工作台访问保护全量开启 + Flag清理

**这一步做什么**：把鉴权套用到既有`/api/todos`，物理删除flag。**必须等前5步都已提交完成后再做。**

**复制以下全部内容，粘贴进新开的Copilot Chat（Agent模式）**：

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方，以及开关清理的确认方式。

---

## T00106：发布收尾——工作台访问保护全量开启 + Flag清理

你要做的事：在T00101～T00105全部合并后，把登录态校验套用到既有`TodoController`，并从代码库中物理删除`app.feature.login-auth-enabled`开关。
对应验收标准：AC-1～AC-5（回归，本任务不改变其行为，仅要求flag关闭前后行为一致，外加"未登录不可访问工作台"这一新增的默认行为）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"用户名密码登录"纵向安全切片计划中的第6/6个任务，也是最后一个任务。前置状态：T00101～T00105均已合并，登录功能已完整覆盖AC-1～AC-5全部行为。本任务完成后，`app.feature.login-auth-enabled`将从代码库中物理移除，登录与工作台访问保护成为系统默认行为。请先自查当前代码库，找到T00101～T00105实际建立的鉴权校验组件（可能是拦截器/过滤器等，以实际实现为准）。

### 预计涉及文件

**必须改动**：
- 鉴权校验组件（T00101～T00105期间建立，具体文件以自查结果为准，修改：删除内部flag判断使其常态生效）
- Spring MVC配置（修改：无条件把该鉴权组件注册到`/api/todos/**`路径）
- `server/src/main/resources/application.yml`（修改，删除`app.feature.login-auth-enabled`属性）
- `client/src/App.tsx`（修改，删除flag判断分支，未登录访问工作台视图默认重定向/拦截）

### 你要做什么

前提：确认T00101～T00105均已合并到主干（自查当前代码库确认）。

**后端**：
1. 在鉴权校验组件中，删除内部对`app.feature.login-auth-enabled`的读取与判断分支，使鉴权校验始终生效。
2. 在Spring MVC配置中，把该校验逻辑无条件应用到`/api/todos/**`路径。
3. 删除`application.yml`中的`app.feature.login-auth-enabled`属性。

**前端**：
1. 在`App.tsx`中删除对该flag的判断分支，未登录（无有效token）访问工作台视图时默认展示登录表单。

**通用**：
2. 全仓库全文搜索`login-auth-enabled`（含配置文件、代码、注释、测试用例中的引用），确认清理完成。

### 从验收标准推导出的隐含规则
1. AC-1～AC-5"回归"意味着flag删除前后对同一操作序列的响应必须逐条比对一致，不能只跑一遍测试通过就算数。
2. "未登录状态下访问工作台相关路由被拒绝"这条要求`TodoController`现有的`GET`/`POST /api/todos`必须被完全纳入保护范围，不能遗漏。
3. "全文搜索不再有任何引用"包括测试代码中为该flag写过的针对性测试，这些测试本身也应删除或改写。

### 明确不要做的事
- 不要在本任务中新增/修改AC-1～AC-5已实现的业务逻辑本身
- 不要顺带清理与本故事无关的其他历史配置
- 不要跳过"确认无引用"这一步就直接删除
- 若T00101～T00105实际尚未全部合并，暂停并报告

### 完成定义（DoD）
- 仓库全文搜索`login-auth-enabled`命中数为0（本说明文档本身除外）。
- `TodoController`相关的`/api/todos`路由（GET/POST）在无flag代码的情况下，未携带有效`Authorization`头访问时返回401/403拒绝，有集成测试覆盖。
- AC-1～AC-5对应的既有测试用例，在删除flag代码后全部通过。
- `application.yml`中不再包含该flag声明。

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方，以及开关清理的确认方式（全文搜索命中数为0的实际执行记录）。
````

**Copilot完成后你要做的事**：
1. 核对DoD四条，尤其是"全仓库搜索命中数为0"这条要自己手动跑一遍确认，不要只信Copilot的转述。
2. 提交：
   ```
   git add -A
   git commit -m "feat(T00106): 发布收尾——工作台访问保护全量开启 + Flag清理 (AC-1~AC-5回归)"
   git push
   ```

---

## 三、六步全部完成后

- 六个commit都push完成后，登录故事US-AUTH-01在Spring Boot后端的实现就完整了。
- 按 `qwen本地模型Benchmark验证方案.md` 第七节"数据记录模板"把六个任务各自的通过情况（是否一次通过、经过几轮、有没有需要你介入纠正）记录下来，这批数据可以直接作为该benchmark方案的Step 2实验组数据使用。
