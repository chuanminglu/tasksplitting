# 补充任务包C：config · Copilot（qwen3.8:27b）执行计划

> 5个任务，均为配置/脚手架类，对`tasksplitting`当前真实状态自查后直接生成，未经子agent。互相独立。
> CI/CD验收模式：全部`PR-only`（T-CFG-01本身是搭建CI，不能要求它自证"CI跑绿"，属循环依赖）。本阶段不接入spec-verifier。

---

## T-CFG-01：CI工作流搭建

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行"你要做什么"里要求的验证命令，把运行结果原样贴出来；如果失败，自行分析原因并修复后重新运行，重复此过程直到通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应验证方式，不允许在没有验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件、实际技术选型是否与说明一致、是否有偏离范围的地方。

---

## T-CFG-01：CI工作流搭建

你要做的事：新增GitHub Actions工作流，在push/PR时自动跑`server`的`mvn test`和`client`的`npm run typecheck`。
对应验收标准：无新增AC；验收要点：工作流配置语法正确、逻辑能覆盖两个子项目的验证命令。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
仓库当前无`.github/workflows`目录，无任何CI配置。`[FACT]`自查确认。

### 预计涉及文件

**必须改动**：
- `.github/workflows/ci.yml`（新增）

**可能改动**：无。

### 技术选型
1. **触发条件** → `push`到`main`分支 + 所有`pull_request`。`[ASSUME]`材料未规定，按业界惯例选定。
2. **Job结构** → 单个workflow内两个job（`server-test`跑`mvn test`，`client-typecheck`跑`npm run typecheck`），并行执行，互不阻塞。依据：两者互不依赖，并行更快暴露问题。`[ASSUME]`
3. **Actions版本** → `actions/checkout@v4`、`actions/setup-java@v4`（`temurin`发行版，17版本，对齐`pom.xml`的`java.version`）、`actions/setup-node@v4`（20版本，对齐`package.json`惯例）。`[ASSUME]`具体版本号材料未规定，选当前主流稳定版本。

### 你要做什么
1. 新增`.github/workflows/ci.yml`，定义`server-test`和`client-typecheck`两个job
2. `server-test`：checkout → setup-java(17) → `cd server && mvn test`
3. `client-typecheck`：checkout → setup-node(20) → `cd client && npm ci && npm run typecheck`
4. 本地验证：无法在本工作区真实触发GitHub Actions（需要推送到GitHub后才能看到运行结果），验证范围止步于"YAML语法正确"+"本地手动执行一遍workflow里写的命令序列，确认都能成功跑完"（即`cd server && mvn test`和`cd client && npm ci && npm run typecheck`本地跑通）

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要在本任务里要求"CI必须跑绿"作为验收条件（本任务就是在搭建CI本身，要求它自证CI跑绿会形成循环依赖）
- 不要顺带引入部署/发布相关的workflow（如自动发布到生产环境），只做测试验证这一个workflow

### 完成定义（DoD）
- `.github/workflows/ci.yml`文件存在，YAML格式可被正确解析（无语法错误）
- 本地手动执行`cd server && mvn test`成功（复用workflow里定义的相同命令）
- 本地手动执行`cd client && npm ci && npm run typecheck`成功
- 交付报告需如实说明"CI是否已实际在GitHub上跑过、跑绿"——如果本次任务只到本地验证，需要明确标注，不能暗示已经在CI环境验证过

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、本地验证结果、是否有偏离范围的地方，以及"CI是否已在GitHub实际跑绿"这一点的如实说明。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "chore(T-CFG-01): CI工作流搭建" && git push`（**建议**：这个PR合并后，去GitHub Actions页面确认实际跑绿，再回来补一句备注，这是唯一需要人工补充验证的任务）

---

## T-CFG-02：数据库路径环境变量化

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令`cd server && mvn test`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应验证方式，不允许在没有验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件、实际技术选型是否与说明一致、是否有偏离范围的地方。

---

## T-CFG-02：数据库路径环境变量化

你要做的事：把`application.yml`里硬编码的SQLite数据库路径改为可通过环境变量覆盖，默认值保持不变。
对应验收标准：无新增AC；验收要点：不设环境变量时行为不变，设置后使用新路径。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`server/src/main/resources/application.yml`当前`spring.datasource.url: jdbc:sqlite:server/prisma/dev.db`是写死的字面量，无法在不同部署环境（如容器化部署时数据卷挂载路径不同）下覆盖。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/application.yml`（修改）

**可能改动**：无。

### 技术选型
**占位符语法** → 使用Spring Boot原生`${DB_PATH:server/prisma/dev.db}`占位符（不引入额外配置库/profile机制），环境变量名`DB_PATH`。依据：Spring Boot的relaxed binding原生支持这个语法，是改动量最小的实现方式。`[ASSUME]`环境变量命名材料未规定。

### 你要做什么
把`url: jdbc:sqlite:server/prisma/dev.db`改为`url: jdbc:sqlite:${DB_PATH:server/prisma/dev.db}`。

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要改变数据库引擎（仍是SQLite）
- 不要修改`spring.sql.init.mode`等其他数据源相关配置

### 完成定义（DoD）
- `application.yml`改动后，不设置`DB_PATH`环境变量时，`mvn test`全部通过（证明默认值未变，不引入回归）
- **实测范围说明**：`DB_PATH`环境变量覆盖后是否真实生效，需要在JVM启动前设置环境变量才能验证，`mvn test`的标准运行方式不方便自动化覆盖这一点；交付报告需说明是否做了手工验证（如通过`DB_PATH=/tmp/other.db mvn test`观察行为），如未做手工验证需如实说明"仅确认配置语法正确+默认值场景测试通过，环境变量覆盖生效性未做进一步验证"，不能断言"已验证生效"

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、`mvn test`结果、环境变量覆盖生效性的验证方式（或未验证的如实说明）、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "chore(T-CFG-02): 数据库路径环境变量化" && git push`

---

## T-CFG-03：端口环境变量化（验证优先，不一定需要改代码）

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行验证命令，把运行结果原样贴出来；如果验证不通过，自行分析原因并修复后重新验证，重复此过程直到通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应验证方式，不允许在没有验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件（如果发现不需要改动，也要如实说明并给出验证依据）、实际技术选型是否与说明一致、是否有偏离范围的地方。

---

## T-CFG-03：端口环境变量化（先验证，再按需改动）

你要做的事：确认服务端口是否已经可以通过环境变量覆盖；如果不能，改成可覆盖；如果已经可以（不需要额外配置），如实说明并只补文档，不做无意义的代码改动。
对应验收标准：无新增AC；验收要点：`SERVER_PORT`环境变量能覆盖`application.yml`里写死的端口。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`application.yml`当前`server.port: 4000`是字面量。**这里有一个需要先验证、不能直接假设的技术事实**：Spring Boot的relaxed property binding机制，默认情况下`SERVER_PORT`这个环境变量名本身就能覆盖`server.port`配置项，**不需要**在yml里写任何`${PORT:4000}`占位符语法——但这条`[ASSUME]`未经本次针对当前项目实际配置的验证（比如项目里是否有其他配置覆盖了这个机制），必须先验证再决定要不要动代码，不能凭该框架的通用行为就断言这个项目里一定成立。

### 预计涉及文件

**必须改动**：取决于验证结果，见"你要做什么"。

**可能改动**：
- `server/src/main/resources/application.yml`（`[ASSUME]`，仅当验证发现`SERVER_PORT`不生效时才需要改）

### 技术选型
无法在展开阶段预先收敛——这正是本任务要通过实测来确定的问题。

### 你要做什么
1. **先验证**：设置`SERVER_PORT`环境变量（如`SERVER_PORT=4001`）启动应用（或用等价的测试方式，如Spring Boot Test里通过`@SpringBootTest(properties = "server.port=...")`或系统环境变量注入验证relaxed binding是否生效），确认应用实际监听端口是否变为设置的值
2. **如果验证通过**（`SERVER_PORT`已经生效，不需要代码改动）：不修改`application.yml`，只在`README.md`里补一句说明"服务端口可通过`SERVER_PORT`环境变量覆盖（Spring Boot原生支持）"
3. **如果验证不通过**（`SERVER_PORT`不生效，比如被其他配置覆盖或行为不符预期）：把`server.port: 4000`改为显式的`server.port: ${PORT:4000}`占位符语法（改用`PORT`而非`SERVER_PORT`作为变量名以示区分是显式配置而非依赖relaxed binding），重新验证生效

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要在未验证的情况下就直接添加占位符语法（可能是多余改动，也可能因为变量名选择不当引入新的不一致）
- 不要修改端口以外的其他`server.*`配置

### 完成定义（DoD）
- 有明确的验证记录（测试代码或手工验证步骤+结果），证明"环境变量能否覆盖端口"这一事实
- 若验证通过且未改代码，`README.md`已补充说明；若验证不通过，`application.yml`已改为显式占位符且重新验证通过
- 无论哪种结果，交付报告必须明确说明验证方法和结论，不能只写结论不写怎么验证的

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR（即使最终判断"不需要改动生产代码"，也要提交这个验证过程和README补充说明作为PR）。完成后返回：验证方法与结论、实际改了哪些文件（含"判断不需要改动"的情况）、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "chore(T-CFG-03): 端口环境变量化验证与配置" && git push`

---

## T-CFG-04：测试专属配置文件

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令`cd server && mvn test`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件、实际技术选型是否与说明一致、是否有偏离范围的地方。

---

## T-CFG-04：测试专属配置文件

你要做的事：新增`application-test.yml`，把测试环境的数据库配置统一收口到一个可复用的Spring Profile，减少各测试类各自用`@DynamicPropertySource`/`@TestPropertySource`零散覆盖数据库路径的重复代码（如果确实存在这种重复）。
对应验收标准：无新增AC；验收要点：测试profile生效，且不影响现有测试全部通过。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`server/src/main/resources`目前只有`application.yml`一个配置文件（`[FACT]`自查确认），无`application-test.yml`。**需要先自查**：当前4个测试类（`AuthIntegrationTest`/`LoginEventIntegrationTest`/`LoginEventResilienceTest`/`TodoAuthIntegrationTest`）各自是如何覆盖测试数据库路径的（是否每个类各自用不同的`@DynamicPropertySource`指向不同的SQLite文件，还是有统一机制）——这一点材料未提供确切结论，需要你派发前自查这4个测试类的实际配置方式，因为这直接决定本任务"统一收口"的具体改法。

### 预计涉及文件

**必须改动**：
- `server/src/main/resources/application-test.yml`（新增）

**可能改动**：
- 4个现有测试类文件（`[UNKNOWN]`是否需要改动，取决于自查结果——如果它们已经各自硬编码了独立的数据库路径且工作正常，是否要统一改成新profile需要权衡"统一收口的收益"与"改动现有稳定测试的风险"，倾向保守：如果改动会影响已通过的测试且收益不明显，可以只新增`application-test.yml`供后续新测试类使用，不强制重构现有4个类）

### 技术选型
**新测试的数据库隔离方式** → 新增`application-test.yml`，设置独立的测试用SQLite路径（如`jdbc:sqlite:target/test-data/application-test.db`），配合`@ActiveProfiles("test")`激活。依据：给后续新增的测试类提供一个标准、无需各自零散配置的基础设施，仓库现有模式（各测试类自行`@DynamicPropertySource`）在类数量少时可行，但不是长期可扩展的方式。`[ASSUME]`

**是否重构现有4个测试类** → **不重构**，保持现状不变，只新增`application-test.yml`本身，作为后续新测试类的可选基础设施。依据：现有4个测试类已经全部通过，重构它们的数据库配置方式属于对已稳定行为的无谓改动，风险大于收益，不在本任务必要范围内。`[ASSUME]`，如果后续判断确实需要统一重构，应另立任务。

### 你要做什么
1. 自查现有4个测试类的数据库配置方式，在交付报告里如实记录自查结论
2. 新增`server/src/main/resources/application-test.yml`，定义独立测试数据库路径
3. 不修改现有4个测试类

### 从验收标准推导出的隐含规则
无（"是否重构现有测试"这一模糊点已在技术选型中收敛为"不重构"）。

### 明确不要做的事
- 不要修改现有4个测试类的数据库配置方式（技术选型已明确保守处理）
- 不要删除或合并现有测试类

### 完成定义（DoD）
- `application-test.yml`文件存在，配置了独立于开发库的测试数据库路径
- 现有4个测试类的全部测试用例（`mvn test`）依然全部通过，无回归
- 交付报告需包含对4个现有测试类数据库配置方式的自查结论

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、`mvn test`结果、现有测试类数据库配置方式的自查结论、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "chore(T-CFG-04): 测试专属配置文件" && git push`

---

## T-CFG-05：前端API Base URL可配置化

````
你现在处于一次任务执行能力测试中，请严格只做下面"任务说明"里写的内容，不要跳出范围。
除非任务说明本身标注了 NEEDS CLARIFICATION，否则不要中途询问我做澄清，遇到未覆盖的细节，按给出的假设（[ASSUME]标注的内容）直接执行，不要停下来等我确认。
完成代码修改后，必须在本工作区实际运行测试命令`cd client && npm run typecheck`并跑`cd client && npx vitest run`，把运行结果原样贴出来；如果测试失败，自行分析原因并修复后重新运行，重复此过程直到测试通过，或者你判断确实无法在合理范围内修复为止——如果是后者，必须如实说明卡在哪一步、报错是什么，不要假装已经完成。
"完成定义（DoD）"里的每一条断言都必须有对应测试覆盖，不允许在没有测试验证的情况下宣称某条DoD已满足。
本阶段不接入spec-verifier，不需要跑任何spec-verifier相关命令。
最后请按"交付要求"里写的格式汇报：实际改了哪些文件、实际技术选型是否与说明一致、是否有偏离范围的地方。

---

## T-CFG-05：前端API Base URL可配置化

你要做的事：让前端所有`fetch`调用的API地址支持通过构建时环境变量配置，开发环境默认行为不变（继续走Vite proxy）。
对应验收标准：无新增AC；验收要点：不设置环境变量时行为不变，设置后请求发往指定的base URL。

本任务信息自包含：不要假设除下文之外还有更多规划背景，下面就是执行这个任务所需的全部上下文。

### 背景
`client/src/App.tsx`当前所有`fetch`调用（`/api/auth/login`、`/api/todos`）都是相对路径，依赖开发环境下Vite的proxy配置转发到`http://localhost:4000`。生产构建后不存在Vite dev server，需要一种指定真实后端地址的机制。

### 预计涉及文件

**必须改动**：
- `client/src/App.tsx`（修改，所有`fetch`调用的URL前缀改为可配置）
- `client/.env.example`（新增，说明如何配置）

**可能改动**：无。

### 技术选型
**配置机制** → 使用Vite原生环境变量`import.meta.env.VITE_API_BASE_URL`，未设置时默认空字符串（保持相对路径不变，开发环境继续走proxy不受影响）；新增一个常量（如`const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''`），所有`fetch`调用URL前面拼接该常量。依据：Vite内置环境变量机制无需额外依赖，`VITE_`前缀是Vite要求暴露给客户端代码的强制约定。`[FACT]`（Vite官方机制，非本任务发明）。

### 你要做什么
1. `App.tsx`新增`API_BASE`常量
2. 所有`fetch('/api/...)`调用改为`fetch(\`${API_BASE}/api/...\`)`
3. 新增`client/.env.example`，内容示例：`VITE_API_BASE_URL=https://api.example.com`（含注释说明留空则使用相对路径走开发代理）

### 从验收标准推导出的隐含规则
无。

### 明确不要做的事
- 不要修改`vite.config.ts`里现有的proxy配置（开发环境行为保持不变）
- 不要给每个fetch调用引入统一的API客户端封装/axios等库（超出本任务范围，只做URL前缀可配置化这一件事）

### 完成定义（DoD）
- 未设置`VITE_API_BASE_URL`时，现有组件测试全部通过（证明默认行为不变）
- 有测试或代码审查证据表明设置`VITE_API_BASE_URL`后，fetch调用会使用带前缀的URL（可通过mock `import.meta.env`验证，`[ASSUME]`具体mock方式由执行者根据vitest环境能力选定）
- `.env.example`文件存在且含使用说明注释

**CI/CD验收模式：PR-only**

### 交付要求
改动应能独立形成一个PR。完成后返回：实际改了哪些文件、测试结果、是否有偏离范围的地方。
````

**Copilot完成后**：核对DoD → `git add -A && git commit -m "chore(T-CFG-05): 前端API Base URL可配置化" && git push`
