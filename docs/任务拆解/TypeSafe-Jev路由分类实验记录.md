# TypeSafe（Jev）用于模型路由分类的实验记录

> 背景：`docs/任务拆解/VueReact+Java专用Harness商业方案.md`附录A的模型路由决策树，目前靠Claude/qwen在做任务拆解时顺带给出判断。本次是一次侧路实验，测试能否用TypeSafe的Choice原语把这个判断单独抽成一个专门的分类调用。**结论：技术上可行，但criteria必须结构化才能用，本文档记录了完整的踩坑与修复过程。**
> 结论：暂不采纳（见下方"是否采纳"一节），仅作记录，避免以后重新踩同一个坑。

---

## 实验对象

登录故事T00103（连续失败锁定规则）——我们自己此前已经判定为`business-logic`标签的一个真实任务，改动涉及`schema.sql`/`User.java`/`UserRepository.java`/`AuthService.java`四个文件，全部属于同一个"用户认证"逻辑模块。

## 第一轮：字符串criteria（直接照抄路由决策树的文字定义）

```json
"criteria": {
  "crud": "增删改查、配置项、纯补测试、无复杂状态的展示交互",
  "business-logic": "需要梳理多个条件分支或状态迁移，但改动范围仍在单个模块内",
  "refactor": "改动必然牵扯到2个以上既有模块的接口或数据结构",
  "novel-design": "没有可复制的既有模式，需要从头设计方案"
}
```

**结果**：`choice: "refactor"`，`confidence: 0.67`，`probabilities: {refactor: 0.75, business-logic: 0.24, crud: 0.01, novel-design: 0.0}`——**判错了**，而且伴随的`is_complex`（Noul，问"是否涉及多分支/状态机判断"）给了0.87，反而更支持`business-logic`，Jev自己两个答案内部有点矛盾。

**归因**：任务描述里提到了"涉及schema.sql新增字段、User.java修改、UserRepository.java修改"——三个文件名。推测Jev把"改了好几个文件"误当成了`refactor`定义里"牵扯到2个以上既有模块"的证据，混淆了"文件数量"和"模块数量"这两个不同的概念。

## 第二轮：结构化criteria（`what`/`not_for`/`examples`）

按TypeSafe官方文档`primitives/choice.md`里"Structured instructions and criteria"一节的做法，把`business-logic`和`refactor`两个选项从字符串改成对象，显式加了一条`not_for`把"文件数量≠模块数量"这个易混点点破：

```json
"business-logic": {
  "what": "需要梳理多个条件分支或状态迁移，但改动全部集中在同一个功能模块内部——即使跨了好几个文件，只要这些文件都服务于同一个模块，仍然算business-logic",
  "not_for": "改动跨越了两个原本相互独立的功能模块，或者改动的是模块之间的接口约定本身——这种情况属于refactor，不是business-logic",
  "examples": ["给一个已有登录功能加上失败次数限制和账号锁定规则，涉及该功能自己的数据表字段和判断逻辑"]
},
"refactor": {
  "what": "改动必须同时涉及两个或以上原本相互独立的功能模块的接口或数据结构",
  "not_for": "只是在同一个功能模块内部改了好几个文件——文件数量不等于模块数量",
  "examples": ["把新建的登录鉴权系统接入到原本不需要登录的Todo模块"]
}
```

**结果**：`choice: "business-logic"`，`confidence: 1.0`，`probabilities: {business-logic: 1.0, 其余: 0.0}`——**完全判对，且没有任何歧义**。

## 结论

1. **Jev不是开箱即用的**：把路由决策树的文字定义原样粘贴进criteria会产生真实的误判（这次是把business-logic错判成refactor），跟我们这个项目里反复验证的"模糊提示词导致模型判断跑偏"是同一类问题，只是主角从qwen/Claude换成了专门的分类模型。
2. **结构化criteria（what/not_for/examples）是官方明确背书的解法**，不是我们自己猜的技巧；本次实测把混淆的置信度从0.67直接拉到1.0，效果非常干净。
3. **确认了两条官方最佳实践**：多个Choice/Noul/Score问题应该在一次请求里批量问（并行评估，几乎不增加延迟）；`confidence`低于阈值时，业务代码应该走"转人工"分支而不是硬用结果，官方SDK示例代码就是这么写的。

## 是否采纳到StackHarness的路由决策里

**暂不采纳，维持现状**（由Claude/qwen在任务拆解时直接给出路由标签），原因见此前讨论：
- 换分类器解决的是"猜标签准不准"这一个环节，回答不了商业方案真正要的"能不能撑起80%覆盖率"这个问题——那必须靠benchmark真实执行数据
- 现在还在攒qwen的真实样本（补齐计划39个任务进行中），这时候再引入一个全新的第三方模型做路由决策，会让后续路由错误的归因变得更复杂（分不清是任务拆解方法论的问题还是TypeSafe分类器的问题）

**值得记住的点**：如果以后真的要接入TypeSafe做路由分类，全部8个路由标签的criteria都要写成结构化的`what/not_for/examples`格式，不能像第一轮那样图省事直接抄决策树的一句话定义——这份记录里已经给了`business-logic`/`refactor`两个可以直接复用的结构化criteria模板，其余标签（`crud`/`config`/`test-generation`/`simple-ui`/`integration`/`algorithm`/`performance-tuning`）如果以后要用，照这个模式补齐即可。
