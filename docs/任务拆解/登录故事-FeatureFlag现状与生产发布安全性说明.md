# 登录故事 · Feature Flag（login-auth-enabled）生命周期与前端联动说明

> 版本：2026-09-21（二次更新）。首版基于T00101～T00105已合并、T00106尚未开始时的代码库核实；本次更新时T00106已实际完成（`AuthInterceptor`+`WebConfig`已落地，flag已从代码中物理删除），据此重写全文，并补充一个中途发现的重要缺口：**flag只包了后端API，没包前端UI结构，导致"flag关闭"这个状态在故事进行期间其实是不安全的**——这是本文档要重点记录的经验教训。

---

## 一、最终状态（T00106已完成，2026-09-21核实）

**flag已被物理删除，不再是任何形式的开关**：

- `server/src/main/resources/application.yml`：不再包含`app.feature.login-auth-enabled`配置项
- `server/src/main/java/com/tasksplitting/api/AuthController.java`：构造函数不再注入flag值，`login()`方法不再有`if (!loginAuthEnabled)`判断
- 源码级全仓库搜索`login-auth-enabled`/`loginAuthEnabled`（排除`target/`构建产物和`docs/任务拆解/`历史规划文档）**命中数为0**，满足T00106的DoD要求

**工作台数据保护改为常驻拦截器，不经过flag**：

```java
// server/src/main/java/com/tasksplitting/api/AuthInterceptor.java
@Component
public class AuthInterceptor implements HandlerInterceptor {
    // preHandle: 从 Authorization: Bearer <token> 头取token
    // 缺失/无效/过期 → 401 + {"error":{"code":"UNAUTHORIZED", ...}}
    // 校验通过 → 放行
}

// server/src/main/java/com/tasksplitting/api/WebConfig.java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/todos/**");
    }
}
```

代码注释里直接写明了这是"permanent auth gate...it has no feature-flag branch"——设计上就是不可逆的常态保护，不是又一个可以关闭的开关。

**前端已同步改为携带token访问工作台数据**：

```tsx
// client/src/App.tsx
const authHeaders = { Authorization: `Bearer ${token}` };
fetch('/api/todos', { headers: authHeaders })
  .then((response) => {
    if (response.status === 401) { onLogout(); return []; }
    return response.json();
  })
```

未登录/token失效时，前端收到401会自动调用`onLogout()`把用户打回登录页——这是`/api/todos`真正被保护后，前端配套需要做的最小闭环。

---

## 二、中途发现的缺口：flag当时只覆盖了后端API，没覆盖前端UI结构

这是本文档最值得记录的部分。**在T00106完成之前**，`App.tsx`的实现是：

```tsx
export default function App() {
  const [token, setToken] = useState<string | null>(null);
  if (!token) return <LoginForm onLogin={setToken} />;  // 无条件强制登录，没有绕过路径
  return <TodoBoard onLogout={() => setToken(null)} />;
}
```

**这里没有任何分支能回到"不登录直接看Todo看板"这个故事开始前的行为**。问题在于：这段代码是随T00101一起合并的，而T00101的技术选型只把flag加在了后端`AuthController`上，从没设计"前端也要感知这个flag"。结果是：

- 如果在T00101～T00105合并期间（flag=false）把这份代码部署到生产环境，所有用户打开页面**只会看到登录表单**，输入任何账号密码都会收到后端返回的404（因为flag关闭），**永远进不了Todo看板**——而这个看板在故事开始之前是完全开放、无需登录就能访问的。
- 也就是说，flag虽然让`/api/auth/login`这个新接口保持"不可用"，但没能让整个应用的**入口结构**保持"故事开始前的样子"。真正的Release Toggle要求是"关闭时，用户观察到的行为应该跟没做这个改动一样"，而这里的前端实现从合并那一刻起就已经改变了用户能观察到的行为（从"直接可用"变成"强制登录墙"），跟flag状态完全脱钩。

**为什么这次没有酿成真实事故**：因为这套代码全程只在本地开发环境验证、没有真的部署到生产环境，T00106也在这个缺口被发现后不久就完成了，flag整体被删除，"flag=false该怎么办"这个问题随之失去意义。但如果换一个真实项目、真的存在"合并到main但还没做完就要临时发布一个补丁"的场景，这个缺口是会真正触发生产事故的。

### 前端当时应该怎么做（面向未来同类故事的方法论记录）

如果要让这个设计从一开始就安全，`App.tsx`当时应该同时感知flag状态，保留"flag关闭→旧行为"的分支，而不是无条件强制登录：

```tsx
// 更安全的写法（本故事因为flag已删除，不再需要，仅作方法论记录）
export default function App() {
  const featureEnabled = /* 从某个途径获取flag状态，如构建时注入的环境变量，
                             或后端提供一个 GET /api/config 之类的只读端点 */;
  if (!featureEnabled) return <TodoBoard />;  // flag关闭：维持故事开始前的行为，不显示登录墙

  const [token, setToken] = useState<string | null>(null);
  if (!token) return <LoginForm onLogin={setToken} />;
  return <TodoBoard onLogout={() => setToken(null)} />;
}
```

具体怎么让前端拿到flag值是一个需要另外决策的技术选型点（构建时注入 vs 运行时查询后端），不是本文档要解决的问题；这里要记录的是**原则**：**一个Release Toggle包裹的范围，必须覆盖它引入的全部可观察行为变化，不能只包新增的后端API，漏掉因此连带改变的前端入口结构**。判断"要不要在前端也加flag分支"的标准很简单：**这个改动是否让某个原本可访问的入口变得不可访问（哪怕只是暂时被一个未完工的新流程挡住）？如果是，前端就必须能在flag关闭时绕开这个新流程，回到旧行为。**

---

## 三、给两份提示词的启示

这个缺口本质上是"技术选型收敛"和"预计涉及文件"两个环节都没有把"flag范围应该覆盖到哪里"这件事显式化——`单任务实现-subagent提示词.md`目前的"技术选型收敛规则"只要求收敛存储方式、凭证形式这类纯后端决策点，没有要求收敛"这个改动是否需要在多个入口点（API+UI结构）同步做flag判断"。见本目录下两份提示词是否需要为此新增一条规则的讨论（若已同步修改，以`单任务实现-subagent提示词.md`/`迭代任务分解-subagent提示词.md`的v3版本说明为准）。
