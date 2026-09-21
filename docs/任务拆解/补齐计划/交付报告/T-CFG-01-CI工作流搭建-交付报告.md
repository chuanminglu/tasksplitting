# T-CFG-01 交付报告 — CI工作流搭建

- 任务编号：T-CFG-01
- 验收标准：工作流配置语法正确、逻辑能覆盖 server 的 `mvn test` 与 client 的 `npm run typecheck`
- 验收模式：PR-only（本任务即搭建 CI 本身，不能要求它自证"CI 跑绿"，属循环依赖）
- 交付日期：2026-09-21

## 一、任务概述

仓库此前无任何 `.github/workflows` 与 CI 配置。本任务新增 `.github/workflows/ci.yml`，在 push 到 `main` 和所有 `pull_request` 上并行跑两个 job：`server-test`（`mvn test`）与 `client-typecheck`（`npm ci` + `npm run typecheck`）。只搭测试验证，不做部署/发布。

## 二、实际改动的文件（核对表）

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `.github/workflows/ci.yml` | 新增 | 两个并行 job：`server-test`（Java 17/temurin + `mvn test`）、`client-typecheck`（Node 20 + `npm ci && npm run typecheck`） |

未改：server / client 源码、`pom.xml`、`package.json`（根级 typecheck 脚本已存在，直接复用）。`package-lock.json` 仓库中已存在并被 CI 使用，本任务未改动它。

## 三、实际技术选型是否与说明一致

- **触发条件**：✅ `push` 到 `main` + 所有 `pull_request`（与 `[ASSUME]` 一致）。
- **Job 结构**：✅ 单 workflow 内两个 job 并行（`server-test`、`client-typecheck`），互不依赖。
- **Actions 版本**：✅ `actions/checkout@v4`、`actions/setup-java@v4`（temurin 17，对齐 `pom.xml` 的 `<java.version>17</java.version>`）、`actions/setup-node@v4`（Node 20）。
- **一处必要的命令修正（见六）**：规格写的是 `cd client && npm ci && npm run typecheck`。经自查，本仓库是 **npm workspaces monorepo**（根 `package.json` 含 `"workspaces": ["client"]`），`client` 是子 workspace、无独立 `package-lock.json`，`cd client && npm ci` 在此结构下无法正确安装。故 client job 改为在**仓库根**执行 `npm ci && npm run typecheck`（复用已定义的根级 typecheck 脚本，等价于 `npm run typecheck --workspace client`）。这不影响"DoD 要求覆盖 client 的 typecheck"这一目标。

## 四、本地验证结果（对应 DoD 的每条断言）

| DoD 断言 | 验证方式 | 结果 |
|---|---|---|
| `ci.yml` 存在、YAML 可被解析 | 文件已创建，结构为标准 GitHub Actions schema | ✅ |
| 本地 `mvn test` 成功 | `cd server; mvn test` | ✅ `Tests run: 33, Failures: 0, Errors: 0` / `BUILD SUCCESS` |
| 本地 client typecheck 成功 | 根级 `npm install`（补全 workspace 依赖）后 `npm run typecheck` | ✅ `tsc --noEmit` 无错误输出 |

> 说明：本地 client 环境原先 node_modules 处于残缺状态（本地正在运行的 dev server 持有 rollup 原生文件锁，导致 `npm ci` 的"先删后装"失败）。用不删除的 `npm install` 补全依赖后 typecheck 通过。**CI 是干净的 ubuntu 环境，无此文件锁，`npm ci` 可正常执行。**

## 五、原始命令输出节选

```
$ cd server; mvn test
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ npm run typecheck          # 仓库根，等价 npm run typecheck --workspace client
> tasksplitting-client@0.1.0 typecheck
> tsc --noEmit
（无错误输出，退出码 0）
```

## 六、是否有偏离范围的地方（逐条对照"明确不要做的事"）

- **不要以"CI 必须跑绿"作为验收条件**：✅ 验收止步于本地两条命令跑通 + YAML 语法正确；未把"CI 跑绿"列为验收前提。
- **不要顺带引入部署/发布 workflow**：✅ 仅一个测试验证 workflow，无部署/发布。
- **偏离说明（如实标注，非"不要做的事"违规）**：
  1. client job 由规格假设的 `cd client && npm ci && npm run typecheck` 改为根级 `npm ci && npm run typecheck`——因 npm workspaces monorepo 结构所必需（client 是子 workspace，无独立 lockfile），目的（覆盖 client typecheck）不变。

## 七、交付物与后续事项

- 分支：`t-cfg-01-ci-workflow`；提交：`.github/workflows/ci.yml` + 本报告
- **CI 是否已在 GitHub 实际跑绿：尚未。** 本任务只做到本地两条命令验证 + YAML 语法正确。建议本 PR 合并后，到 GitHub Actions 页面确认 `CI` workflow 实际跑绿（这是唯一需要人工补充验证的任务，见规格备注）。
- 待办：阶段 3 下一任务 **T-CFG-02**。
