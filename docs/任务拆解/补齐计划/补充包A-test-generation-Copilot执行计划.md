# 补充任务包A：test-generation · Copilot（qwen3.8:27b）执行计划

> 8个任务，均为"只加测试、不改生产代码"，对`tasksplitting`当前真实状态（`main`，登录故事已全部合并）自查后直接生成，未经子agent。互相独立，可任意顺序执行，也可分给不同会话并行跑。
> CI/CD验收模式：全部`PR-only`。本阶段不接入spec-verifier。
> 执行方式：每个任务开一个新的Copilot Chat会话（Agent模式，模型`qwen3.8:27b`），复制对应任务的完整代码块粘贴即可。完成后核对DoD，`git add -A && git commit -m "test(T-TG-0X): <任务名>" && git push`（建议每个任务开独立分支+PR，命名如`t-tg-01-todo-empty-title-test`）。

---

## T-TG-01：Todo空标题校验补测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-01：Todo空标题校验补测试

你要做的事：为`TodoController.create`已有的"空标题拒绝"行为补充集成测试（该行为已实现，当前无任何测试覆盖）。
对应验收标准：无新增AC，验证既有生产代码行为：`title`为空字符串或纯空白时返回400且不写入数据库。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`server/src/main/java/com/tasksplitting/api/TodoController.java`的`create`方法已有校验：`title`为`null`或`trim()`后为空时返回`400 Bad Request` + `{"message":"title is required"}`，不写入数据库。当前代码库对这条已存在的行为**没有任何测试覆盖**。`/api/todos`已被`AuthInterceptor`保护，测试需要先登录拿到有效token。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/TodoValidationTest.java`（新增）

**可能改动**：无。

### 技术选型
**测试文件归属** → 新建独立文件`TodoValidationTest`，不并入现有`TodoAuthIntegrationTest`。依据：现有测试文件已按主题拆分（`AuthIntegrationTest`管登录、`LoginEventIntegrationTest`管埋点、`TodoAuthIntegrationTest`管鉴权），本任务验证的是"输入校验"这一不同主题，混入`TodoAuthIntegrationTest`会让该文件职责模糊。`[INFER]`基于既有拆分惯例。

### 你要做什么
新增`TodoValidationTest`（`@SpringBootTest`+`MockMvc`，参照`TodoAuthIntegrationTest`的搭建方式）：
1. 先调用`/api/auth/login`用测试种子用户登录拿到有效token
2. `POST /api/todos`携带空字符串`title` → 断言400 + `message`字段存在
3. `POST /api/todos`携带纯空白字符串（如`"   "`）`title` → 同样断言400
4. 两种情况后分别`GET /api/todos`确认数据库中todo数量未增加（相对登录后、发起校验失败请求前的数量）

### 从验收标准推导出的隐含规则
无，本任务是给既有行为补测试，不涉及新行为推导。

### 明确不要做的事
- 不要修改`TodoController.create`的校验逻辑本身
- 不要修改`TodoRepository`
- 不要顺带给`GET /api/todos`补充其他无关测试

### 完成定义（DoD）
- `[FACT]` 空字符串`title`提交返回400，响应体含`message`字段
- `[FACT]` 纯空白字符串`title`提交同样返回400
- `[INFER]` 两种失败提交后，数据库中todo总数未增加

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR，只新增测试文件，不改生产代码。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD 3条 → `git add -A && git commit -m "test(T-TG-01): Todo空标题校验补测试" && git push`

---

## T-TG-02：Authorization头格式容错补测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-02：Authorization头格式容错补测试

你要做的事：为`AuthInterceptor.extractBearerToken`已有的"格式错误请求头拒绝"行为补充集成测试（该行为已实现，当前无测试覆盖这个具体分支）。
对应验收标准：无新增AC，验证既有生产代码行为：`Authorization`头存在但不是合法`Bearer <token>`格式时返回401。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`server/src/main/java/com/tasksplitting/api/AuthInterceptor.java`的`extractBearerToken`方法：头部不以`"Bearer "`（含尾随空格）开头，或`"Bearer "`后内容为空，均返回`null`，触发401 + `{"error":{"code":"UNAUTHORIZED","message":"missing or malformed Authorization header"}}`。当前`TodoAuthIntegrationTest`已测试"完全没有header"、"token不存在于库中"、"token已过期"三种情况，**没有测试"header存在但scheme/格式错误"这一支**。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/TodoAuthIntegrationTest.java`（修改，新增用例）

**可能改动**：无。

### 技术选型
无开放性决策——直接复用该文件已有的测试搭建方式（种子用户、已发布的登录/请求辅助方法）。

### 你要做什么
在`TodoAuthIntegrationTest`新增2个用例：
1. `Authorization: Basic dXNlcjpwYXNz`（错误scheme） → `GET /api/todos` → 401 + `error.code == "UNAUTHORIZED"`
2. `Authorization: Bearer `（Bearer后为空字符串） → `GET /api/todos` → 同样401 + `error.code == "UNAUTHORIZED"`

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要修改`AuthInterceptor`本身的判断逻辑
- 不要修改错误响应格式

### 完成定义（DoD）
- `[FACT]` 错误scheme（如`Basic`）的Authorization头 → 401 + `error.code == "UNAUTHORIZED"`
- `[FACT]` `Bearer `后为空 → 同样401 + `error.code == "UNAUTHORIZED"`

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR，只新增测试用例，不改生产代码。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD 2条 → `git add -A && git commit -m "test(T-TG-02): Authorization头格式容错补测试" && git push`

---

## T-TG-03：公开路由不受保护回归测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-03：公开路由不受保护回归测试

你要做的事：为"`/api/auth/login`与`/api/health`不受`AuthInterceptor`拦截"这一既有行为补充回归测试，防止未来有人无意中扩大拦截器的`addPathPatterns`范围而无测试报警。
对应验收标准：无新增AC，验证既有生产代码行为：公开端点无需`Authorization`头也可访问。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`server/src/main/java/com/tasksplitting/api/WebConfig.java`把`AuthInterceptor`只注册到`/api/todos/**`。`/api/auth/login`（`AuthController`）和`/api/health`（`HealthController`）目前"没被拦"纯粹是因为路径模式没匹配到，没有任何测试显式锁定这个事实——如果以后有人把`addPathPatterns`改成`/api/**`，不会有测试失败提醒。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例）

**可能改动**：无。

### 技术选型
**新增用例放在哪个文件** → 放入`AuthIntegrationTest`（不新建文件）。依据：验证的是"登录接口本身的可用性"，语义上属于该文件既有职责范围，且复用其`@SpringBootTest`上下文比新建一个更省资源。`[ASSUME]`

### 你要做什么
在`AuthIntegrationTest`新增2个用例：
1. 不带任何`Authorization`头，`GET /api/health` → 200
2. 不带任何`Authorization`头，`POST /api/auth/login`携带正确的种子用户名密码 → 200 + 非空`token`（证明登录端点本身不受拦截器影响，这与"登录成功"这条已有测试的意图不同，这里的重点是"零token情况下该端点依然可达"）

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要修改`WebConfig`的拦截路径配置
- 不要新增其他公开端点

### 完成定义（DoD）
- `[FACT]` 无Authorization头访问`/api/health` → 200
- `[FACT]` 无Authorization头调用`/api/auth/login`（正确凭证）→ 200 + token非空

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD 2条 → `git add -A && git commit -m "test(T-TG-03): 公开路由不受保护回归测试" && git push`

---

## T-TG-04：登录空用户名/密码补测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-04：登录空用户名/密码补测试

你要做的事：为`AuthService.login`已有的"用户名/密码为空时不抛异常、走正常失败分支"这一行为补充测试。
对应验收标准：无新增AC，验证既有生产代码行为：空/缺失用户名密码不会导致500，而是走`USER_NOT_FOUND`分支。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`AuthService.login`对`username`/`rawPassword`做了`null`兜底（`safeUsername = username == null ? "" : username`），空字符串用户名查不到用户会走`USER_NOT_FOUND`分支，不会抛`NullPointerException`。当前测试都用的是有效用户名/密码组合，没有测试空值/缺失字段这条路径。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例）

**可能改动**：无。

### 技术选型
无开放性决策。

### 你要做什么
在`AuthIntegrationTest`新增2个用例：
1. `POST /api/auth/login`请求体`username`为空字符串、`password`任意 → 401 + `error.code == "USER_NOT_FOUND"`（非500）
2. `POST /api/auth/login`请求体JSON里完全不传`username`字段（Java record反序列化为`null`） → 同样401 + `error.code == "USER_NOT_FOUND"`（非500）

### 从验收标准推导出的隐含规则
无，验证的是既有null安全处理，不涉及新行为推导。

### 明确不要做的事
- 不要修改`AuthService.login`的null处理逻辑
- 不要新增输入校验（如"用户名不能为空"这类显式400校验）——本任务只验证现状，不改变现状

### 完成定义（DoD）
- `[FACT]` 空字符串username → 401 + `USER_NOT_FOUND`，非500
- `[FACT]` 缺失username字段 → 同样401 + `USER_NOT_FOUND`，非500

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD 2条 → `git add -A && git commit -m "test(T-TG-04): 登录空用户名密码补测试" && git push`

---

## T-TG-05：用户名大小写敏感补测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-05：用户名大小写敏感补测试

你要做的事：为`UserRepository.findByUsername`当前"精确匹配、区分大小写"的行为补充测试。
对应验收标准：无新增AC，验证既有生产代码行为：大小写不同的用户名视为不同账号，登录会失败。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`UserRepository.findByUsername`的SQL是`WHERE username = ?`，SQLite的`TEXT`比较默认区分大小写（未使用`COLLATE NOCASE`），意味着注册用户名`alice`，用`Alice`或`ALICE`登录会查不到用户，走`USER_NOT_FOUND`分支。当前没有测试锁定这个行为——如果以后有人不小心改成大小写不敏感匹配，不会有测试报警。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例）

**可能改动**：无。

### 技术选型
无开放性决策——这条测试的目的就是**锁定当前行为**，不是判断"应该"区分大小写；如果产品后续要求不区分大小写，需要另立任务修改`findByUsername`的SQL并同步改写本测试，不在本任务范围内讨论。

### 你要做什么
在`AuthIntegrationTest`新增1个用例：创建测试用户`alice`（正确密码），分别用`Alice`和`ALICE`尝试登录 → 均返回401 + `error.code == "USER_NOT_FOUND"`（不是`INVALID_PASSWORD"`，因为是查不到用户，不是密码错）。

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要修改`UserRepository.findByUsername`的SQL或匹配方式
- 不要在本任务里讨论"是否应该"大小写不敏感，只验证现状

### 完成定义（DoD）
- `[FACT]` 用户名`Alice`（大小写不同于已注册的`alice`）登录 → 401 + `USER_NOT_FOUND`
- `[FACT]` 用户名`ALICE`同样 → 401 + `USER_NOT_FOUND`

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD 2条 → `git add -A && git commit -m "test(T-TG-05): 用户名大小写敏感补测试" && git push`

---

## T-TG-06：Todo列表排序补测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-06：Todo列表排序补测试

你要做的事：为`TodoRepository.findAll`已有的"按创建时间倒序"排序行为补充测试。
对应验收标准：无新增AC，验证既有生产代码行为：`GET /api/todos`返回结果按`createdAt`降序（最新的在最前）。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`TodoRepository.findAll`的SQL是`ORDER BY "createdAt" DESC`。当前`TodoAuthIntegrationTest.getTodosWithValidTokenReturns200`只断言返回200和数组类型，没有断言顺序。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/TodoAuthIntegrationTest.java`（修改，新增用例）

**可能改动**：无。

### 技术选型
无开放性决策。

### 你要做什么
在`TodoAuthIntegrationTest`新增1个用例：登录拿token后，依次创建3个Todo（标题分别为`"A"`/`"B"`/`"C"`，之间无需刻意等待，SQLite `CURRENT_TIMESTAMP`精度足以区分顺序；若同一秒内创建导致时间戳相同，需按`[ASSUME]`处理——见假设清单），`GET /api/todos`断言返回顺序为`["C","B","A"]`（按标题反推创建顺序，最新的排最前）。

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要修改`TodoRepository.findAll`的排序逻辑
- 不要给Todo新增排序相关的API参数（如`?sort=`）

### 完成定义（DoD）
- `[FACT]` 依次创建A/B/C三个Todo后，`GET /api/todos`返回顺序为C、B、A

**CI/CD验收模式：PR-only**

### 假设与待确认项
- `[ASSUME]` 若SQLite `CURRENT_TIMESTAMP`精度不足以区分同一秒内连续创建的多条记录（可能导致顺序不确定），执行者可在测试用例间插入短暂延时（如几十毫秒）保证时间戳可区分，这属于测试稳定性的实现细节，不影响本任务验证的核心事实。

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "test(T-TG-06): Todo列表排序补测试" && git push`

---

## T-TG-07：Session过期边界补测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-07：Session过期边界补测试

你要做的事：为`AuthInterceptor`判断session过期时"`expiresAt`恰好等于当前时刻仍视为有效"这一边界行为补充测试。
对应验收标准：无新增AC，验证既有生产代码行为：`isBefore`语义下，`expiresAt == now`不算过期。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`AuthInterceptor.preHandle`判断`session.expiresAt().isBefore(LocalDateTime.now(clock))`才视为过期。这意味着`expiresAt`恰好等于当前时刻这一刻不算"早于now"，仍被视为有效。`TodoAuthIntegrationTest.getTodosWithExpiredTokenReturns401`只测试了明确过去的时间点，没有测试这个相等边界。`TodoAuthIntegrationTest`里已有可控测试`Clock`机制（`TestConfiguration`+`advance`方法，参照`T00106`交付报告），本任务复用它。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/TodoAuthIntegrationTest.java`（修改，新增用例）

**可能改动**：无。

### 技术选型
无开放性决策——复用该文件已有的可控`Clock`基础设施，不新增mock机制。

### 你要做什么
在`TodoAuthIntegrationTest`新增1个用例：构造一个`Session`，其`expiresAt`字段直接设置为测试`Clock`当前所指的确切时刻（不早不晚），携带该token访问`GET /api/todos` → 断言200（不是401）。

### 从验收标准推导出的隐含规则
无，这条本身就是在验证既有代码的字面语义（`isBefore`不含相等）。

### 明确不要做的事
- 不要修改`AuthInterceptor`的过期判断逻辑（如果发现"相等应视为过期"更符合业务预期，那是另一个决策，需要另立任务讨论，不在本任务范围内擅自改）

### 完成定义（DoD）
- `[FACT]` `expiresAt`恰好等于当前时刻的Session，访问受保护接口 → 200

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "test(T-TG-07): Session过期边界补测试" && git push`

---

## T-TG-08：账号锁定解除边界补测试

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令——涉及server目录改动跑 `cd server && mvn test`，涉及client目录改动跑 `cd client && npm run typecheck`（如需组件测试，另跑 `cd client && npx vitest run`），把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（与"预计涉及文件"逐项核对）、"可能改动"清单最终有没有改、实际技术选型是否与说明一致、新增了哪些测试用例、是否有偏离范围的地方。

---

## T-TG-08：账号锁定解除边界补测试

你要做的事：为`AuthService`判断账号锁定时"`lockedUntil`恰好等于当前时刻视为已解锁"这一边界行为补充测试（与T-TG-07对称）。
对应验收标准：无新增AC，验证既有生产代码行为：`isAfter`语义下，`lockedUntil == now`不算仍锁定。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`AuthService.login`判断`user.lockedUntil().isAfter(now)`才视为仍锁定。`lockedUntil`恰好等于当前时刻这一刻不算"晚于now"，账号视为已解锁，可正常登录。`AuthIntegrationTest.correctPasswordAfterLockExpiresLogsInAndClearsFailures`测的是"时钟推进15分钟之后"（明确已过期），没有测试这个相等边界。

### 预计涉及文件

**必须改动**：
- `server/src/test/java/com/tasksplitting/api/AuthIntegrationTest.java`（修改，新增用例）

**可能改动**：无。

### 技术选型
无开放性决策——复用该文件已有的可控`Clock`基础设施（`TestConfiguration`+`advance`/`reset`方法）。

### 你要做什么
在`AuthIntegrationTest`新增1个用例：构造一个用户，其`lockedUntil`字段直接设置为测试`Clock`当前所指的确切时刻，用正确密码登录 → 断言200成功（不是423），且登录成功后`failedLoginAttempts`归零、`lockedUntil`清空（复用现有成功登录清零逻辑的断言方式）。

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要修改`AuthService`的锁定判断逻辑

### 完成定义（DoD）
- `[FACT]` `lockedUntil`恰好等于当前时刻的账号，用正确密码登录 → 200成功
- `[INFER]` 登录成功后该用户`failedLoginAttempts`归零、`lockedUntil`清空

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、新增了哪些测试用例、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD 2条 → `git add -A && git commit -m "test(T-TG-08): 账号锁定解除边界补测试" && git push`
