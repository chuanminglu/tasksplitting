# 候选故事：忘记密码找回（US-AUTH-02）+ 头像上传（US-PROFILE-01）· Copilot执行计划

> 两个故事各3个任务，共6个任务，对`tasksplitting`当前真实状态（登录故事+样本包A/B/C已合并或待合并，取当时最新状态为准）自查后直接生成，未经子agent。
> **与补充包A/B/C不同，这6个任务内部有顺序依赖**（同一故事内后一个任务依赖前一个任务的真实交付结果），必须按T00201→T00202→T00203、T00301→T00302→T00303的顺序执行，两个故事之间互不依赖，可以并行推进。
> CI/CD验收模式：全部`PR-only`。本阶段不接入spec-verifier。

---

## 一、US-AUTH-02 全局技术选型（T00201～T00203共享，不重复论证）

| 决策点 | 选定方案 | 依据 |
|---|---|---|
| 邮件发送方式 | 新增`EmailSender`接口 + MVP实现`LoggingEmailSender`（只记日志，不真实发SMTP邮件；日志需含收件邮箱+验证码） | `[ASSUME]`仓库当前无任何SMTP配置/凭据，引入`spring-boot-starter-mail`需要真实邮件服务器才能端到端验证，超出当前MVP范围；接口设计留出未来替换真实实现的空间 |
| 验证码存储 | 新增`PasswordResetCode`表（`schema.sql`累加式新增）：`id`/`email`/`code`/`expiresAt`/`used`/`createdAt` | 延续T00103建立的schema.sql累加式演进约定 |
| 验证码生成 | `SecureRandom`生成6位数字字符串（`String.format("%06d", ...)`，允许前导零） | `[ASSUME]`安全实践，不用`Math.random()` |
| 验证码有效期 | 10分钟 | `[FACT]`沿用本故事原始验收标准设定的数值 |
| 未注册邮箱的响应策略 | 无论邮箱是否注册，`POST /api/auth/forgot-password`都返回**相同的成功提示**，只有已注册邮箱才真正生成验证码并"发送"（记日志），未注册邮箱不生成任何记录 | `[ASSUME]`防止用户枚举攻击的标准安全实践，是本次选定的方案，不再作为待确认项留白 |
| User表新增字段 | `schema.sql`累加式`ALTER TABLE "User" ADD COLUMN email TEXT` | 延续既有约定 |
| 旧会话失效机制 | `SessionRepository`新增`deleteAllForUser(int userId)`方法，密码重置成功后调用 | 密码重置的安全要求是"重置后所有旧会话失效"，不只是失效发起重置请求的那一个 |
| API路径 | `POST /api/auth/forgot-password`（发起）、`POST /api/auth/reset-password`（验证码校验+改密） | `[ASSUME]`延续`/api/auth/*`既有路径风格 |

**验收标准（AC）**：
- AC-1：输入已注册邮箱 → 生成6位验证码并"发送"（日志记录）；未注册邮箱 → 不生成验证码，返回相同的成功提示（不暴露账号是否存在）
- AC-2：验证码10分钟内提交且正确 → 允许设置新密码；超时或错误 → 拒绝，不允许继续
- AC-3：新密码设置成功后，旧密码立即失效（无法再登录），该用户此前所有会话失效（需重新登录）

---

## 二、US-PROFILE-01 全局技术选型（T00301～T00303共享，不重复论证）

| 决策点 | 选定方案 | 依据 |
|---|---|---|
| 文件存储方式 | 本地文件系统，目录`server/uploads/avatars/`（新增，加入`.gitignore`避免上传内容进版本库） | `[ASSUME]`仓库无云存储SDK依赖，引入S3/OSS需要真实凭据，超出MVP范围 |
| 文件名策略 | `UUID.randomUUID()` + 原始扩展名，不使用用户上传的原始文件名 | 避免文件名冲突与路径穿越风险 |
| 访问方式 | 新增`GET /api/avatars/{filename}`端点，读取本地文件字节流返回，`Content-Type`按扩展名推断 | 不使用Spring静态资源映射（`server/uploads`在classpath之外，动态挂载需要额外配置）；沿用仓库"每个资源一个Controller"的既有风格 |
| 格式校验 | 依据`MultipartFile.getContentType()`声明值判断（仅接受`image/jpeg`/`image/png`），不做魔数字节嗅探 | `[ASSUME]`简化范围，真实生产场景可能需要更严格校验，本次MVP先以声明值为准 |
| 大小校验 | `MultipartFile.getSize() > 2*1024*1024`判定超限 | AC原文直接给出2MB数值 |
| User表新增字段 | `schema.sql`累加式`ALTER TABLE "User" ADD COLUMN avatarUrl TEXT` | 延续既有约定 |
| 前端"个人中心"视图落地方式 | 复用`App.tsx`现有state驱动的视图切换模式（`'login' \| 'workboard' \| 'profile'`三态），不引入react-router | 仓库当前无任何路由库依赖，维持现有架构风格 |
| "导航栏同步更新"的具体落地 | 原故事描述的"导航栏"在当前代码库里不存在传统意义上的持久化导航栏，转译为："Todo工作台顶部固定展示当前用户头像缩略图"，头像URL状态提升到`App`顶层组件，个人中心和工作台共享同一状态源 | `[INFER]`基于真实代码库结构对原故事目标的合理落地，不生搬硬套原始措辞 |

**验收标准（AC）**：
- AC-1：上传jpg/png格式、≤2MB的图片 → 成功，返回可访问的头像URL，个人中心页面展示新头像
- AC-2：上传成功后，工作台顶部头像缩略图同步更新为最新头像（不只是个人中心页面，两处都要反映最新状态）
- AC-3：上传非jpg/png格式或>2MB → 明确错误提示，不写入`avatarUrl`

---

## 任务清单顺序

1. T00201 → T00202 → T00203（忘记密码，串行）
2. T00301 → T00302 → T00303（头像上传，串行）
3. 两条串行链之间互不依赖，可以并行推进（比如T00201和T00301同时开始）

---

## T00201：发起找回密码流程

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令 `cd server && mvn test`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00201：发起找回密码流程

你要做的事：用户输入注册邮箱后，若已注册则生成验证码并记录"发送"日志，若未注册则不生成任何记录，两种情况都返回相同的成功提示。
对应验收标准：AC-1（输入已注册邮箱→生成验证码；未注册→不生成，提示统一）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"忘记密码找回"纵向安全切片计划中的第1/3个任务，也是本故事的第一个任务。前置状态：`User`表当前无`email`字段（`[FACT]`自查确认）；无`PasswordResetCode`表；无`EmailSender`相关代码。本任务完成后，此功能暂不受任何Feature Flag包裹（`[ASSUME]`——本故事不像登录故事那样是"从零到有"的强制门槛类功能，是否需要flag取决于产品判断，本次先不加，如需要可另行补充，不在本任务强制引入）。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/schema.sql`（修改，追加`User.email`字段的`ALTER TABLE`语句 + 新增`PasswordResetCode`表定义）
- `server/src/main/java/com/tasksplitting/api/User.java`（修改，record新增`email`字段）
- `server/src/main/java/com/tasksplitting/api/UserRepository.java`（修改，`findByUsername`查询语句需要带出`email`字段；新增按email查找的方法）
- `server/src/main/java/com/tasksplitting/api/EmailSender.java`（新增，接口）
- `server/src/main/java/com/tasksplitting/api/LoggingEmailSender.java`（新增，MVP实现，`@Component`）
- `server/src/main/java/com/tasksplitting/api/PasswordResetCodeRepository.java`（新增）
- `server/src/main/java/com/tasksplitting/api/ForgotPasswordController.java`（新增）
- `server/src/main/java/com/tasksplitting/api/ForgotPasswordService.java`（新增）
- `server/src/main/java/com/tasksplitting/api/ForgotPasswordRequest.java`（新增，record，含`email`字段）

**可能改动**：无。

### 技术选型
见文档开头"一、US-AUTH-02 全局技术选型"表，本任务直接落地其中的"邮件发送方式""验证码存储""验证码生成""验证码有效期""未注册邮箱的响应策略""User表新增字段""API路径"共7项，不重复论证。

### 你要做什么
**后端**：
1. `schema.sql`追加`ALTER TABLE "User" ADD COLUMN IF NOT EXISTS email TEXT;`（若当前SQLite JDBC版本不支持`IF NOT EXISTS`语法——参照T00103已踩过的坑——改用`PRAGMA table_info`探测+条件`ALTER TABLE`的兼容写法）
2. `schema.sql`新增`PasswordResetCode`表：`id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT NOT NULL, code TEXT NOT NULL, expiresAt DATETIME NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE, createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`
3. `EmailSender`接口：`void send(String toEmail, String subject, String body)`；`LoggingEmailSender`实现：仅调用日志（如`log.info`）记录参数，不做真实网络请求
4. `ForgotPasswordService.requestReset(String email)`：查`UserRepository`是否存在该email对应的用户；存在→生成6位验证码（`SecureRandom`）、写入`PasswordResetCode`（`expiresAt = now(clock).plusMinutes(10)`）、调用`EmailSender.send`；不存在→什么都不做；**两种情况方法都正常返回，不抛异常**，调用方统一返回成功响应
5. `ForgotPasswordController`：`POST /api/auth/forgot-password`，接收`{email}`，调用Service，无论内部是否真实发送，响应体统一为`200 + {"message": "如果该邮箱已注册，验证码已发送"}`（`[ASSUME]`具体文案措辞）

### 从验收标准推导出的隐含规则
`[INFER]` "未注册邮箱不暴露账号是否存在"这条已在全局技术选型里收敛为选定方案，不是留白隐含规则，但落地时要注意：响应的HTTP状态码、响应体结构、响应耗时都不应该有可观察的差异（本任务不做刻意的时序防护，只保证状态码和响应体一致；时序层面的防护不在本任务范围，如需要应另立任务，材料未要求）。

### 明确不要做的事
- 不要实现验证码校验与设置新密码逻辑（属于T00202）
- 不要实现旧密码失效逻辑（属于T00203）
- 不要引入真实SMTP集成（技术选型已明确选定MVP日志方案）
- 不要在响应体或日志之外的任何用户可见位置暴露验证码本身（验证码只应出现在`LoggingEmailSender`的日志里和数据库里，不能出现在HTTP响应体中）

### 完成定义（DoD）
- `[FACT]` 已注册邮箱调用`POST /api/auth/forgot-password` → 200，`PasswordResetCode`表新增一条对应记录，`code`为6位数字字符串，`expiresAt`约为当前时刻+10分钟
- `[FACT]` 未注册邮箱调用同一接口 → 200，响应体与已注册邮箱场景**结构相同**（不能有额外字段暴露差异），且`PasswordResetCode`表未新增任何记录
- `[INFER]` `LoggingEmailSender`被调用时，日志中可观察到收件邮箱与验证码（可通过mock该bean验证被调用及参数，而非真的解析日志输出）

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `[ASSUME]` 是否需要Feature Flag包裹本功能，本次判断不需要，如产品要求灰度发布需另行补充
- `[ASSUME]` 成功响应文案的具体措辞

### 交付要求
改动应能独立形成一个PR，范围只覆盖本任务。完成后返回：实际改了哪些文件（与"预计涉及文件"逐项核对）、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方——这份汇报将作为T00202生成实现说明时的材料，请如实填写。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "feat(T00201): 发起找回密码流程 (AC-1)" && git push`

---

## T00202：验证码校验与新密码设置

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令 `cd server && mvn test`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00202：验证码校验与新密码设置

你要做的事：用户提交邮箱+验证码+新密码，验证码在有效期内且正确则更新密码，否则拒绝。
对应验收标准：AC-2（验证码10分钟内提交且正确→允许设置新密码；超时或错误→拒绝）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"忘记密码找回"纵向安全切片计划中的第2/3个任务。**前序任务T00201尚未实际交付**——自查当前代码库若发现`PasswordResetCode`表、`ForgotPasswordService`等文件不存在，说明T00201确实未合并，本说明基于其规划预判展开，派发前需先确认T00201已合并，并核对其真实的类/方法签名。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/PasswordResetCodeRepository.java`（修改，新增按email+code查询的方法，新增标记`used`的方法）
- `server/src/main/java/com/tasksplitting/api/ForgotPasswordService.java`（修改，新增`resetPassword`方法）
- `server/src/main/java/com/tasksplitting/api/ForgotPasswordController.java`（修改，新增`/reset-password`端点）
- `server/src/main/java/com/tasksplitting/api/UserRepository.java`（修改，新增更新密码哈希的方法）
- 新增`ResetPasswordRequest.java`（record：`email`/`code`/`newPassword`）

**可能改动**：无。

### 技术选型
无本任务专属的新决策点，沿用T00201已确立的表结构与`Clock`注入方式（延续T00101起建立的时间可测试性约定）。

### 你要做什么
`ForgotPasswordService.resetPassword(String email, String code, String newPassword)`：
1. 按`email`+`code`查`PasswordResetCodeRepository`，查不到、`used=true`、或`expiresAt`早于当前时刻（`isBefore`，与T00103/T00106一致的边界语义：恰好等于不算过期）→ 抛异常，返回明确错误（如复用`AuthException`模式，新增`code = "INVALID_OR_EXPIRED_CODE"`）
2. 校验通过 → 用`BCryptPasswordEncoder`对`newPassword`哈希，更新`User.passwordHash`；把该`PasswordResetCode`标记`used = true`（防止同一验证码被重复使用）；返回成功
3. `ForgotPasswordController`新增`POST /api/auth/reset-password`端点接收`ResetPasswordRequest`

### 从验收标准推导出的隐含规则
1. `[INFER]` 验证码一旦被成功使用过一次，即使还在10分钟有效期内，也不能被再次使用——否则同一个验证码可以被无限次用来改密码，存在安全风险；`used`字段正是为了防止这一点，校验时需要同时检查`used=false`。
2. `[INFER]` 验证码的有效期边界与T00103/T00106建立的"恰好等于不算过期"语义保持一致（`isBefore`而非`isAfter`/`isBeforeOrEqual`），除非本任务另有明确理由采用不同语义，否则应遵循已确立的项目惯例。

### 明确不要做的事
- 不要实现旧密码失效/会话失效逻辑（属于T00203）
- 不要修改T00201已实现的发起找回流程

### 完成定义（DoD）
- `[FACT]` 10分钟内提交正确验证码+合法新密码 → 200成功，`User.passwordHash`已更新
- `[FACT]` 验证码错误 → 拒绝，明确错误响应，密码未变
- `[FACT]` 验证码正确但已超过10分钟（用测试Clock模拟）→ 拒绝
- `[INFER]` 验证码成功使用一次后，用同一验证码再次尝试 → 拒绝（`used`标记生效）

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00201真实交付后，`PasswordResetCodeRepository`/`ForgotPasswordService`的真实方法签名需要核对，若不同需调整落点。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "feat(T00202): 验证码校验与新密码设置 (AC-2)" && git push`

---

## T00203：旧密码失效与重新登录约束

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令 `cd server && mvn test`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00203：旧密码失效与重新登录约束

你要做的事：密码重置成功后，旧密码不能再登录，该用户此前所有会话立即失效。
对应验收标准：AC-3（新密码生效后旧密码立即失效，旧会话失效）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"忘记密码找回"纵向安全切片计划中的第3/3个任务，也是本故事最后一个任务。**前序任务T00201、T00202尚未实际交付**，本说明基于规划预判展开，派发前需先确认两者已合并。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/SessionRepository.java`（修改，新增`deleteAllForUser(int userId)`方法）
- `server/src/main/java/com/tasksplitting/api/ForgotPasswordService.java`（修改，`resetPassword`成功后调用上述方法）

**可能改动**：无。

### 技术选型
见全局技术选型"旧会话失效机制"，不重复论证。

### 你要做什么
1. `SessionRepository`新增`deleteAllForUser(int userId)`：`DELETE FROM "Session" WHERE userId = ?`
2. `ForgotPasswordService.resetPassword`成功更新密码后，紧接着调用`sessionRepository.deleteAllForUser(user.id())`

### 从验收标准推导出的隐含规则
`[INFER]` "旧密码立即失效"这条本身已经由T00202把`passwordHash`更新为新哈希值天然满足（旧密码校验时`BCryptPasswordEncoder.matches`会失败），本任务真正新增的是AC-3后半句"旧会话失效"这个额外约束——这两者是同一条AC里的两个独立断言，容易被误认为T00202已经顺带做完，需要在本任务里显式补上会话失效这部分，不能假设它已经被覆盖。

### 明确不要做的事
- 不要修改T00202已实现的验证码校验/密码更新逻辑本身
- 不要影响其他用户的Session（`deleteAllForUser`必须严格限定在触发重置的那个`userId`）

### 完成定义（DoD）
- `[FACT]` 密码重置成功后，用旧密码登录 → 401 + `INVALID_PASSWORD`
- `[FACT]` 密码重置成功后，用新密码登录 → 200成功
- `[INFER]` 密码重置前该用户存在的有效Session（如重置前已登录产生的token），重置后携带该token访问受保护接口 → 401（会话已失效）

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00201、T00202真实交付后，`ForgotPasswordService`真实方法结构需要核对。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "feat(T00203): 旧密码失效与重新登录约束 (AC-3)" && git push`

---

## T00301：最小上传闭环

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck` 并 `cd client && npx vitest run`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00301：最小上传闭环

你要做的事：搭建头像上传最薄闭环——已登录用户上传jpg/png≤2MB图片，成功后个人中心页面展示新头像。
对应验收标准：AC-1（上传合规图片→成功，返回头像URL，个人中心展示）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"头像上传"纵向安全切片计划中的第1/3个任务，也是本故事第一个任务。前置状态：`User`表无`avatarUrl`字段；无`server/uploads/`目录；前端`App.tsx`只有登录/Todo两个视图，无个人中心。本任务完成后，暂不引入Feature Flag（同US-AUTH-02的判断，`[ASSUME]`）。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/schema.sql`（修改，追加`User.avatarUrl`字段）
- `server/src/main/java/com/tasksplitting/api/User.java`（修改，record新增`avatarUrl`字段）
- `server/src/main/java/com/tasksplitting/api/UserRepository.java`（修改，新增更新`avatarUrl`的方法）
- `server/src/main/java/com/tasksplitting/api/AvatarController.java`（新增，`POST /api/avatars`上传 + `GET /api/avatars/{filename}`读取）
- `client/src/App.tsx`（修改，新增"个人中心"视图与三态切换、上传表单）
- `.gitignore`（修改，新增`server/uploads/`）

**可能改动**：
- `client/src/styles.css`（`[INFER]`，个人中心页面样式）

### 技术选型
见文档开头"二、US-PROFILE-01 全局技术选型"表，本任务落地其中"文件存储方式""文件名策略""访问方式""User表新增字段""前端个人中心视图落地方式"共5项，不重复论证。

### 你要做什么
**后端**：
1. `schema.sql`追加`avatarUrl`字段（同T00201的兼容写法处理`ADD COLUMN`）
2. `AvatarController.POST /api/avatars`：接收`MultipartFile`（本任务暂不做格式/大小校验，全部接受——**校验逻辑属于T00303范围**，本任务只搭通路径，需要在测试里明确注明这一点，避免误以为本任务遗漏了校验），保存到`server/uploads/avatars/{UUID}.{原扩展名}`，更新当前登录用户的`avatarUrl`为`/api/avatars/{UUID}.{原扩展名}`，返回该URL
3. `AvatarController.GET /api/avatars/{filename}`：从`server/uploads/avatars/`读取对应文件字节流返回，`Content-Type`按扩展名推断
4. 该Controller的上传端点需要能拿到"当前登录用户"——由于`AuthInterceptor`只做token有效性校验、不往下游传递用户身份，需要新增机制获取当前用户（`[ASSUME]`技术选型：让`AuthInterceptor`把解析出的`userId`存入`request.setAttribute`，`AvatarController`通过`@RequestAttribute`取用；这是本任务范围内必要的最小扩展，不改变`AuthInterceptor`对`/api/todos`的既有行为）

**前端**：
1. `App`顶层state新增第三态`'profile'`，`TodoBoard`顶部新增"个人中心"入口按钮切换到该视图
2. 新增个人中心视图组件：展示当前头像（若无则展示默认占位）、文件选择控件、上传按钮、"返回工作台"按钮
3. 上传成功后更新头像URL状态（提升到`App`顶层，为T00302的工作台同步展示做准备）

### 从验收标准推导出的隐含规则
无（"导航栏"的转译已在全局技术选型说明，不是本任务需要推导的隐含规则）。

### 明确不要做的事
- 不要实现格式/大小校验（属于T00303，本任务全部接受任意上传文件）
- 不要实现工作台顶部头像同步展示（属于T00302，本任务只做个人中心页面本身的展示）
- 不要修改`AuthInterceptor`对`/api/todos`路径的既有拦截行为

### 完成定义（DoD）
- `[FACT]` 已登录用户上传一个图片文件 → 成功，响应含头像URL，`User.avatarUrl`已更新
- `[FACT]` 上传成功后，个人中心页面展示新头像（`img`标签`src`指向返回的URL）
- `[INFER]` `GET /api/avatars/{filename}`能正确返回已上传文件的字节内容

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `[ASSUME]` 获取当前登录用户身份的具体机制（`AuthInterceptor`通过`request.setAttribute`传递`userId`），材料未规定，为本任务范围内的必要最小扩展。

### 交付要求
改动应能独立形成一个PR，范围只覆盖本任务。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方——这份汇报将作为T00302生成实现说明时的材料，请如实填写。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "feat(T00301): 最小上传闭环 (AC-1)" && git push`

---

## T00302：工作台顶部头像同步展示

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令 `cd client && npm run typecheck` 并 `cd client && npx vitest run`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00302：工作台顶部头像同步展示

你要做的事：上传新头像后，Todo工作台顶部同步展示最新头像缩略图（不只是个人中心页面）。
对应验收标准：AC-2（上传成功后导航栏/工作台头像同步更新）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"头像上传"纵向安全切片计划中的第2/3个任务。**前序任务T00301尚未实际交付**，本说明基于其规划预判展开，派发前需先确认T00301已合并，并核对`avatarUrl`状态在`App.tsx`中的真实存放位置（是否确实已提升到顶层组件，如未提升，需要先做这个提升再继续本任务）。

### 预计涉及文件

**必须改动**：
- `client/src/App.tsx`（修改，`TodoBoard`组件顶部新增头像缩略图展示，读取T00301提升到顶层的头像URL状态）

**可能改动**：
- `client/src/styles.css`（`[INFER]`，缩略图样式）

### 技术选型
无本任务专属的新决策点，沿用T00301已确立的"头像URL状态提升到`App`顶层"这一架构决策。

### 你要做什么
`TodoBoard`组件顶部（`<h1>开发环境已就绪</h1>`附近，`[ASSUME]`具体位置不影响功能）新增一个小尺寸头像图片展示，`src`绑定顶层传入的头像URL状态；若用户尚未上传过头像，展示一个默认占位图（`[ASSUME]`可以是纯色块/首字母缩写，材料未规定具体默认头像样式）。

### 从验收标准推导出的隐含规则
`[INFER]` "同步更新"意味着不需要用户手动刷新页面——从个人中心上传成功、切回工作台视图时，顶部缩略图必须已经是最新的，这要求状态是同一个共享state（T00301已经这样设计），而不是工作台组件各自独立发起一次头像查询请求。

### 明确不要做的事
- 不要修改T00301已实现的个人中心页面本身的头像展示
- 不要新增独立的"获取当前头像"API请求（应直接复用T00301已提升到顶层的状态，不重复请求）

### 完成定义（DoD）
- 工作台顶部展示头像缩略图
- 在个人中心成功上传新头像后，切回工作台视图，顶部缩略图立即反映新头像（同一次会话内，无需刷新页面）
- 未上传过头像时展示默认占位
- 有组件测试覆盖以上三点

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00301真实交付后，头像URL状态的真实存放位置与命名需要核对。
- `[ASSUME]` 默认占位头像的具体样式，材料未规定。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "feat(T00302): 工作台顶部头像同步展示 (AC-2)" && git push`

---

## T00303：上传失败路径的明确错误提示

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck` 并 `cd client && npx vitest run`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T00303：上传失败路径的明确错误提示

你要做的事：为头像上传接口补充格式与大小校验，不合规文件返回明确错误，不写入`avatarUrl`。
对应验收标准：AC-3（非jpg/png或>2MB → 明确错误提示，不写入）

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
这是"头像上传"纵向安全切片计划中的第3/3个任务，也是本故事最后一个任务。**前序任务T00301、T00302尚未实际交付**，本说明基于规划预判展开，派发前需先确认两者已合并。T00301当前全盘接受任意上传文件（未做校验），本任务补齐这一环。

### 预计涉及文件

**必须改动**：
- `server/src/main/java/com/tasksplitting/api/AvatarController.java`（修改，上传方法开头新增格式/大小校验）

**可能改动**：无。

### 技术选型
见全局技术选型"格式校验""大小校验"两项，不重复论证。

### 你要做什么
`AvatarController.POST /api/avatars`方法开头新增校验（在T00301已有的保存逻辑之前）：
1. `contentType`不是`image/jpeg`或`image/png` → 返回400 + `{"error":{"code":"UNSUPPORTED_FORMAT","message":"仅支持jpg/png格式"}}`，不保存文件，不更新`avatarUrl`
2. 文件大小超过2MB（`2 * 1024 * 1024`字节）→ 返回400 + `{"error":{"code":"FILE_TOO_LARGE","message":"文件大小不能超过2MB"}}`，同样不保存不更新
3. 两类校验均通过后，才执行T00301已有的保存逻辑

### 从验收标准推导出的隐含规则
`[INFER]` 两类错误必须用不同的错误码区分（类比登录故事T00102"账号不存在/密码错误分别提示"的既有模式），不能用一个笼统的"上传失败"覆盖两种原因，否则前端无法针对性展示提示文案。

### 明确不要做的事
- 不要修改T00301已实现的成功上传路径逻辑
- 不要做文件内容的魔数字节嗅探（技术选型已明确本阶段只依据声明的`contentType`）

### 完成定义（DoD）
- `[FACT]` 上传非jpg/png格式文件 → 400 + `UNSUPPORTED_FORMAT`，`avatarUrl`未变
- `[FACT]` 上传超过2MB的合规格式文件 → 400 + `FILE_TOO_LARGE`，`avatarUrl`未变
- `[FACT]` 两类错误码不同，前端能据此区分展示

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `NEEDS CLARIFICATION`：T00301、T00302真实交付后，`AvatarController`的真实方法结构需要核对。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、实际技术选型是否与本说明一致、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "feat(T00303): 上传失败路径的明确错误提示 (AC-3)" && git push`
