# Tripper Project Infrastructure

本文档用于说明 Tripper 项目的整体架构、分层职责和逐文件职责。当前项目是一个基于 Kotlin、Spring Boot 和 Embabel Agent Framework 的 AI 旅行规划应用。

## 1. 项目整体定位

Tripper 是一个旅行规划 Agent 应用。用户在 Web 表单中输入出发地、目的地、日期、预算、交通偏好和旅行者画像后，后端通过 Embabel Agent 执行多阶段规划流程：

1. 根据用户 brief 和 traveler profile 寻找兴趣点。
2. 对每个兴趣点进行并发调研。
3. 汇总调研结果生成结构化旅行计划。
4. 对行程做确定性校验，并在发现阻塞问题时触发一次结构化修复。
5. 根据每天停留地点寻找住宿链接。
6. 对最终行程做住宿、预算、链接和路线一致性校验。
7. 对 HTML 和图片链接做后处理。
8. 通过 Thymeleaf/htmx 页面展示最终结果和执行过程。

项目的 AI 能力主要来自：

- Embabel Agent 的 action/goal 编排。
- OpenAI 模型调用。
- MCP 工具调用，包括 Web、Maps、Weather、Browser Automation、Airbnb 等。
- 结构化领域模型，约束 LLM 输出为可被程序继续处理的数据对象。
- Java 实现的 RAG、行程校验、评测、可观测性、安全和编辑模块，用于展示个人扩展能力。

## 2. 总体架构

```mermaid
flowchart TD
    User["User Browser"] --> Web["Spring MVC + Thymeleaf + htmx"]
    Web --> Controller["JourneyHtmxController"]
    Controller --> Platform["Embabel AgentPlatform"]
    Platform --> Agent["TripperAgent"]
    Agent --> Domain["Travel Domain Model"]
    Agent --> Verifier["Java Itinerary Verifier"]
    Agent --> LLM["OpenAI Models"]
    Agent --> Tools["Tool Groups / MCP Tools"]
    Tools --> MCP["Docker MCP Gateway / MCP Servers"]
    Agent --> Result["TravelPlan"]
    Result --> View["journey-plan.html"]
    View --> User

    Platform --> Status["ProcessStatusController"]
    Status --> Processing["processing.html / SSE updates"]
    Processing --> User
```

## 3. 分层职责

### 3.1 Web/UI Layer

职责：

- 展示旅行规划输入表单。
- 接收用户提交的表单数据。
- 展示异步 Agent 执行状态。
- 展示最终旅行计划、地图链接、住宿链接、参考链接和执行成本。

主要文件：

- `src/main/kotlin/com/embabel/tripper/web/JourneyHtmxController.kt`
- `src/main/kotlin/com/embabel/agent/web/htmx/ProcessStatusController.kt`
- `src/main/resources/templates/**`
- `src/main/resources/static/css/**`

### 3.2 Application / Agent Orchestration Layer

职责：

- 启动 Spring Boot 应用。
- 注册 Embabel Agent。
- 创建并启动 AgentProcess。
- 将多个 Agent action 串成完整旅行规划流程。

主要文件：

- `src/main/kotlin/com/embabel/tripper/TripperApplication.kt`
- `src/main/kotlin/com/embabel/tripper/agent/TripperAgent.kt`

### 3.3 Domain Layer

职责：

- 定义旅行规划业务对象。
- 将用户输入、LLM 中间结果和最终结果结构化。
- 提供少量确定性计算逻辑，例如 Google Maps 路线链接生成。

主要文件：

- `src/main/kotlin/com/embabel/tripper/agent/domain.kt`

### 3.4 Tool Integration Layer

职责：

- 暴露外部工具给 Agent 使用。
- 接入 Brave Search。
- 接入 MCP 工具组。
- 将 Docker/MCP 工具按 role 注册成 Embabel ToolGroup。

主要文件：

- `src/main/kotlin/com/embabel/tripper/Brave.kt`
- `src/main/kotlin/com/embabel/tripper/config/ToolsConfig.kt`
- `compose.yaml`
- `compose.dmr.yaml`
- `compose.ollama.yaml`
- `src/main/resources/application-docker-ce.yml`

### 3.5 Security Layer

职责：

- 提供可选 Google OAuth2 登录。
- 在 `embabel.security.enabled=false` 时允许无认证访问，方便 demo。
- 在开启安全配置时保护非静态资源页面。

主要文件：

- `src/main/kotlin/com/embabel/agent/web/security/SecurityConfig.kt`
- `src/main/kotlin/com/embabel/agent/web/security/CustomOAuth2UserService.kt`
- `src/main/kotlin/com/embabel/agent/web/security/LoginController.kt`
- `src/main/kotlin/com/embabel/agent/web/security/UserController.kt`
- `README-SECURITY.md`

### 3.6 Configuration / Runtime Layer

职责：

- 定义服务端口、模型、persona、工具、日志、安全等配置。
- 定义 Maven 构建、Docker 镜像、Docker Compose 服务和 CI。

主要文件：

- `pom.xml`
- `src/main/resources/application.yml`
- `src/main/resources/application-docker-ce.yml`
- `Dockerfile`
- `compose.yaml`
- `.github/workflows/maven.yml`

### 3.7 Observability Layer

职责：

- 为每次 Web planning run 创建可回看的 Agent run trace。
- 记录 action 时间线、状态、工具组、模型摘要、prompt/output 字符规模、错误和最终 usage/cost。
- 通过 `/runs` 和 `/runs/{id}` 页面查看最近 run 和单次 run 详情。
- 默认不保存完整 prompt 正文，只保存摘要和计数，降低敏感信息进入历史记录的风险。

主要文件：

- `src/main/java/com/embabel/tripper/observability/**`
- `src/main/resources/templates/runs.html`
- `src/main/resources/templates/run-detail.html`

### 3.8 Safety / Guardrails Layer

职责：

- 将 RAG 和外部网页内容标记为不可信上下文。
- 检测知识库 chunk 中的 prompt injection、工具滥用指令和敏感信息暴露风险。
- 在 Agent prompt 中加入工具安全策略、允许工具组和单 action 工具调用预算。
- 对最终 HTML 和结构化链接做 unsafe URL 过滤。
- 对 trace 摘要执行敏感信息脱敏，避免常见 token、key、secret、email 等进入历史记录。

主要文件：

- `src/main/java/com/embabel/tripper/safety/**`

### 3.9 Plan Editing Layer

职责：

- 保存完成后的旅行计划版本。
- 支持按全局或单日范围提交编辑指令。
- 生成 day-level diff 和版本历史。
- 保留原始 route、date、budget 和 brief 约束。
- 每次编辑后重新运行行程 verifier。

主要文件：

- `src/main/java/com/embabel/tripper/editing/**`
- `src/main/resources/templates/plan-edit.html`

### 3.10 Test Layer

职责：

- 放置单元测试和集成测试。
- 覆盖 Java RAG、行程校验和评测 harness 的确定性行为。
- 通过轻量评测子集检查 Phase 3 数据集和指标输出。

主要文件：

- `src/test/kotlin/com/embabel/example/travel/agent/TravelPlanTest.kt`
- `src/test/java/com/embabel/tripper/rag/TravelKnowledgeServiceTest.java`
- `src/test/java/com/embabel/tripper/verification/ItineraryVerificationServiceTest.java`
- `src/test/java/com/embabel/tripper/eval/TravelEvaluationHarnessTest.java`
- `src/test/java/com/embabel/tripper/observability/AgentRunTraceServiceTest.java`
- `src/test/java/com/embabel/tripper/safety/ContentSafetyServiceTest.java`
- `src/test/java/com/embabel/tripper/safety/ToolSafetyServiceTest.java`
- `src/test/java/com/embabel/tripper/editing/PlanEditingServiceTest.java`

## 4. 核心运行链路

### 4.1 首页加载

1. 浏览器访问 `/` 或 `/travel/journey`。
2. `JourneyHtmxController.showPlanForm()` 创建默认 `JourneyPlanForm`。
3. Thymeleaf 渲染 `journey-form.html`。
4. 页面展示出发地、目的地、日期、预算、交通方式和 traveler 信息输入项。

### 4.2 提交旅行计划

1. 用户提交表单到 `/travel/journey/plan`。
2. `JourneyHtmxController.planJourney()` 将表单转换为：
   - `JourneyTravelBrief`
   - `Travelers`
3. Controller 从 `AgentPlatform` 中找到 Tripper Agent。
4. Controller 创建 `AgentProcess`，设置 token budget 和 verbosity。
5. Controller 启动 agent process。
6. 页面返回 `common/processing.html`，开始显示执行状态。

### 4.3 Agent 执行流程

`TripperAgent` 中的主要 action 顺序如下：

1. `confirmExpensiveOperation`
   - 根据入口判断是否需要用户确认高成本操作。
2. `findPointsOfInterest`
   - 调用 LLM 和 Web/Maps/Math/Weather 工具寻找兴趣点。
   - 输出 `ItineraryIdeas`。
3. `researchPointsOfInterest`
   - 对每个 `PointOfInterest` 并发调研。
   - 使用 Web、Browser Automation、Weather 和 Brave 图片搜索。
   - 输出 `PointOfInterestFindings`。
4. `proposeTravelPlan`
   - 汇总兴趣点调研信息，生成 `ProposedTravelPlan`。
   - 要求 LLM 输出 HTML 计划、每日地点、图片、视频、页面链接和访问国家。
5. `verifyAndRepairTravelPlan`
   - 调用 Java `ItineraryVerificationService` 校验日期覆盖、地点、预算、链接和路线估算。
   - 如果存在 ERROR 级问题，将 `PlanVerificationResult` 作为结构化 repair prompt 传回 planner。
   - 输出 `VerifiedTravelPlanProposal`。
6. `findPlacesToSleep`
   - 根据每天停留城市分组。
   - 调用 Airbnb 工具寻找住宿搜索链接。
   - 输出 `TravelPlan`。
   - 住宿结果生成后再次执行最终校验，并把 `PlanVerificationResult` 放入 `TravelPlan`。
7. `postProcessHtml`
   - 给图片添加样式。
   - 删除无效图片链接。
   - 作为 `makeTravelPlan` goal 的最终输出。

### 4.4 状态轮询和结果展示

1. `common/processing.html` 展示等待状态、事件流和执行详情。
2. `ProcessStatusController.checkPlanStatus()` 根据 `AgentProcess` 状态返回不同视图：
   - `COMPLETED`：注入最终结果并返回 `journey-plan.html`。
   - `FAILED`：返回 `common/processing-error.html`。
   - `TERMINATED`：返回 `common/processing-error.html`。
   - 其他状态：继续显示 `common/processing.html`。
3. `journey-plan.html` 展示最终计划、地图链接、住宿链接、参考页面、视频链接和执行摘要。

## 5. 逐目录说明

```text
.
├── src/main/kotlin
│   ├── com/embabel/tripper
│   │   ├── agent
│   │   ├── config
│   │   ├── util
│   │   └── web
│   └── com/embabel/agent/web
│       ├── htmx
│       └── security
├── src/main/java
│   └── com/embabel/tripper
│       ├── editing
│       ├── eval
│       ├── observability
│       ├── rag
│       ├── safety
│       ├── verification
│       └── web
├── src/main/resources
│   ├── templates
│   ├── static
│   └── application*.yml
├── src/test/kotlin
├── evals
├── images
├── .github/workflows
├── Dockerfile
├── compose*.yaml
└── pom.xml
```

### 5.1 `src/main/kotlin/com/embabel/tripper`

项目业务主包，包含应用入口、Agent、领域模型、外部服务和 Web Controller。

### 5.1.1 `src/main/java/com/embabel/tripper`

个人新增 Java 主包。当前 Phase 1 RAG MVP 放在这里，包括知识库领域对象、内存索引、检索服务和知识库管理 Controller。

### 5.2 `src/main/kotlin/com/embabel/agent/web`

通用 Web 支撑包，包含 htmx 处理页面、安全配置、登录和用户信息页面。虽然包名是 `com.embabel.agent.web`，但当前代码被本项目直接使用。

### 5.3 `src/main/resources/templates`

Thymeleaf 页面模板目录。负责渲染输入表单、处理中页面、最终结果页、登录页、平台页和公共布局。

### 5.4 `src/main/resources/static`

静态资源目录。当前主要是 CSS。

### 5.5 `src/test/kotlin`

测试目录。当前只有一个 travel plan 相关测试样例，覆盖不足。

### 5.6 `images`

README 中引用的项目截图，包括输入页、输出页、地图、Airbnb 链接、执行计划和事件流截图。

### 5.7 `.github/workflows`

GitHub Actions CI 配置目录。

## 6. 逐文件职责

### 6.1 根目录文件

| 文件 | 职责 |
| --- | --- |
| `README.md` | 原项目主说明文档，介绍 Tripper 功能、运行方式、截图、架构和开发说明。 |
| `PROJECT-NOTE.md` | 说明本 fork 与上游 Embabel Tripper 的关系，以及当前个人扩展范围。 |
| `LOCAL-DEVELOPMENT.md` | 本地开发指南，说明环境变量、MCP secret、测试、运行和 demo 请求。 |
| `README-AI-APPLICATION-PLAN.md` | 个人扩展计划文档，说明如何把项目升级成更适配 AI 应用岗位的作品。 |
| `infra.md` | 当前文件，说明项目整体架构、分层职责和逐文件职责。 |
| `README-SECURITY.md` | Google OAuth2 和安全配置说明。 |
| `evals/README.md` | Phase 3 评测 harness 说明，包含数据集范围、运行命令和输出路径。 |
| `evals/travel-eval-cases.json` | 30 条旅行规划评测数据，覆盖预算、家庭、无障碍、多国家、兴趣偏好和知识库引用等场景。 |
| `pom.xml` | Maven 构建文件，定义 Spring Boot、Kotlin、Embabel、OpenAI、MCP、Security、Thymeleaf 和测试依赖。 |
| `mvnw` / `mvnw.cmd` | Maven Wrapper 启动脚本，用于在未安装 Maven 的机器上运行构建。 |
| `Dockerfile` | 多阶段 Docker 构建文件，使用 Maven 构建 Spring Boot jar，并在运行阶段启动应用。 |
| `compose.yaml` | Docker Compose 主文件，定义 MCP Gateway、Zipkin 和可选 `agent` 服务。 |
| `compose.dmr.yaml` | Docker Model Runner 相关模型服务定义。 |
| `compose.ollama.yaml` | Ollama 本地模型服务定义。 |
| `mcp.env.example` | MCP secret 示例文件，包含 Brave 和 Google Maps key 的占位配置。 |
| `run.sh` | 本机启动脚本，执行 `./mvnw -Dmaven.test.skip=true spring-boot:run`。 |
| `scripts/demo-plan-request.sh` | 示例表单提交脚本，用固定旅行输入触发一次本地 planning run。 |
| `LICENSE` | Apache License 2.0。 |

### 6.2 Application 文件

| 文件 | 职责 |
| --- | --- |
| `src/main/kotlin/com/embabel/tripper/TripperApplication.kt` | Spring Boot 入口。启用配置属性扫描和 Embabel Agent 扫描，并配置 Docker Desktop MCP server 类型。 |

### 6.3 Agent 和领域模型文件

| 文件 | 职责 |
| --- | --- |
| `src/main/kotlin/com/embabel/tripper/agent/TripperAgent.kt` | 项目核心 Agent。定义旅行规划 action，包括成本确认、兴趣点发现、兴趣点调研、计划生成、校验/修复、住宿搜索和 HTML 后处理。 |
| `src/main/kotlin/com/embabel/tripper/agent/domain.kt` | 旅行领域模型。定义 `JourneyTravelBrief`、`Travelers`、`PointOfInterest`、`ItineraryIdeas`、`ResearchedPointOfInterest`、`ProposedTravelPlan`、`Stay`、`TravelPlan` 等数据结构；最终 `TravelPlan` 持有知识库上下文和校验结果。 |

### 6.3.1 Java RAG 文件

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/embabel/tripper/rag/TravelKnowledgeSourceType.java` | 知识来源类型枚举，包括粘贴文本、上传文件和 URL。 |
| `src/main/java/com/embabel/tripper/rag/TravelKnowledgeDocument.java` | 用户导入的知识文档模型。 |
| `src/main/java/com/embabel/tripper/rag/TravelKnowledgeHit.java` | 检索命中的 chunk 结果，包含分数、来源、citation id、安全评估和面向 prompt 的脱敏文本。 |
| `src/main/java/com/embabel/tripper/rag/TravelKnowledgeContext.java` | 可注入 Agent prompt 的知识上下文，实现 `PromptContributor`；明确把知识源标记为不可信上下文。 |
| `src/main/java/com/embabel/tripper/rag/IndexedTravelKnowledgeChunk.java` | 内部索引 chunk 模型，保存 chunk 文本和 term vector。 |
| `src/main/java/com/embabel/tripper/rag/TravelKnowledgeRepository.java` | 内存知识库 repository，保存文档和 chunk。 |
| `src/main/java/com/embabel/tripper/rag/TravelKnowledgeService.java` | Java RAG 核心服务，负责导入、HTML 转文本、切 chunk、term-vector 检索和构造知识上下文。 |
| `src/main/java/com/embabel/tripper/web/TravelKnowledgeController.java` | Java Controller，提供 `/knowledge` 管理页和 `/knowledge/debug` 检索调试页。 |

### 6.3.2 Java 行程校验文件

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/embabel/tripper/verification/VerificationSeverity.java` | 校验问题严重级别枚举：`INFO`、`WARNING`、`ERROR`。 |
| `src/main/java/com/embabel/tripper/verification/PlanIssueCategory.java` | 校验问题分类枚举，包括日期、路线、预算、链接和住宿问题。 |
| `src/main/java/com/embabel/tripper/verification/ItineraryDay.java` | Java verifier 的每日行程输入 DTO，避免 Java 编译期依赖 Kotlin domain。 |
| `src/main/java/com/embabel/tripper/verification/ItineraryLink.java` | Java verifier 的链接输入 DTO，标记链接来源字段和 URL。 |
| `src/main/java/com/embabel/tripper/verification/ItineraryStay.java` | Java verifier 的住宿输入 DTO，保存住宿覆盖日期和住宿链接。 |
| `src/main/java/com/embabel/tripper/verification/ItineraryVerificationRequest.java` | Java verifier 的统一输入请求，承载 brief、plan、days、links 和 stays。 |
| `src/main/java/com/embabel/tripper/verification/PlanVerificationIssue.java` | 单个结构化校验问题，包含类别、级别、日期、地点、消息和修复提示详情。 |
| `src/main/java/com/embabel/tripper/verification/TravelLegEstimate.java` | 相邻地点之间的路线估算结果，包含距离、耗时、估算方法和是否过长。 |
| `src/main/java/com/embabel/tripper/verification/PlanVerificationResult.java` | 一次行程校验结果，实现 `PromptContributor`，可直接作为 repair prompt 的结构化上下文。 |
| `src/main/java/com/embabel/tripper/verification/PlanVerificationRepository.java` | 内存校验结果 repository，保存最近 planning run 的校验输出。 |
| `src/main/java/com/embabel/tripper/verification/ItineraryVerificationService.java` | Java 校验核心服务，负责日期覆盖、地点、路线估算、预算、URL、住宿覆盖等确定性校验。 |

### 6.3.3 Java 评测文件

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/embabel/tripper/eval/TravelEvalCase.java` | 单条旅行评测用例模型，包含 brief、日期、预算、旅客、约束、兴趣、预期国家和主题。 |
| `src/main/java/com/embabel/tripper/eval/TravelEvalDataset.java` | 从 `evals/travel-eval-cases.json` 加载评测用例。 |
| `src/main/java/com/embabel/tripper/eval/EvalPlanCandidate.java` | 单次 planner 输出候选结果，承载 verifier 请求、模拟 latency、token cost 和 tool-call 统计。 |
| `src/main/java/com/embabel/tripper/eval/EvalPlanCandidateFactory.java` | 评测候选计划生成接口，后续可替换为真实 Agent-backed runner。 |
| `src/main/java/com/embabel/tripper/eval/DeterministicEvalPlanCandidateFactory.java` | 离线确定性候选计划生成器，用于本地和 CI 稳定评测。 |
| `src/main/java/com/embabel/tripper/eval/EvaluationCaseResult.java` | 单条评测结果，保存状态、覆盖率、校验问题、citation、tool-call 和成本指标。 |
| `src/main/java/com/embabel/tripper/eval/EvaluationMetrics.java` | 聚合评测指标，包括日期覆盖率、预算/链接问题率、citation 覆盖、tool-call 成功率、平均 latency、成本和 verifier 问题数。 |
| `src/main/java/com/embabel/tripper/eval/EvaluationReport.java` | 评测报告模型，并生成 Markdown 汇总。 |
| `src/main/java/com/embabel/tripper/eval/TravelEvaluationHarness.java` | 评测核心流程：选择数据集子集、生成候选计划、调用行程校验器、聚合指标。 |
| `src/main/java/com/embabel/tripper/eval/TravelEvaluationReportWriter.java` | 将评测报告写为 JSON 和 Markdown 文件。 |
| `src/main/java/com/embabel/tripper/eval/TravelEvaluationCli.java` | 命令行入口，支持指定数据集、输出目录和 limit。 |

### 6.3.4 Java 可观测性文件

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/embabel/tripper/observability/AgentRunStatus.java` | Agent run 生命周期状态枚举：`RUNNING`、`COMPLETED`、`FAILED`、`TERMINATED`。 |
| `src/main/java/com/embabel/tripper/observability/AgentRunEventStatus.java` | 单个 action trace event 状态枚举：`STARTED`、`COMPLETED`、`FAILED`。 |
| `src/main/java/com/embabel/tripper/observability/AgentRunObservabilityProperties.java` | Phase 4 配置属性，控制开关、摘要长度、保留 run 数、prompt 正文捕获和成本预警阈值。 |
| `src/main/java/com/embabel/tripper/observability/AgentRunTraceEvent.java` | 单个 action 时间线事件，保存 action 名、状态、耗时、模型、工具组、prompt/output 字符数、摘要和错误。 |
| `src/main/java/com/embabel/tripper/observability/AgentRunTrace.java` | 单次 Agent run trace，保存 route、预算、最终 cost/token/model 使用、warnings 和 action timeline。 |
| `src/main/java/com/embabel/tripper/observability/AgentRunTraceRepository.java` | 内存 trace repository，保存最近 run 并按配置裁剪数量。 |
| `src/main/java/com/embabel/tripper/observability/AgentRunTraceService.java` | 可观测性核心服务，负责创建 run、记录 action start/complete/fail、补最终 usage/cost 和生成成本预警。 |
| `src/main/java/com/embabel/tripper/observability/AgentRunTraceController.java` | `/runs` 和 `/runs/{id}` 页面 Controller。 |

### 6.3.5 Java 安全和 Guardrails 文件

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/embabel/tripper/safety/SafetyRiskLevel.java` | 安全风险等级枚举：`NONE`、`LOW`、`MEDIUM`、`HIGH`。 |
| `src/main/java/com/embabel/tripper/safety/SafetyFindingCategory.java` | 安全问题分类枚举，包括 prompt injection、工具滥用请求、敏感信息暴露和 unsafe link。 |
| `src/main/java/com/embabel/tripper/safety/SafetyFinding.java` | 单条安全发现，记录类别、风险等级和说明。 |
| `src/main/java/com/embabel/tripper/safety/SafetyAssessment.java` | 针对一段不可信内容的安全评估结果，包含最高风险等级和 findings 汇总。 |
| `src/main/java/com/embabel/tripper/safety/SensitiveDataRedactor.java` | 敏感信息脱敏服务，覆盖常见 API key、token、secret、password、bearer token、OpenAI/GitHub token 和 email。 |
| `src/main/java/com/embabel/tripper/safety/ContentSafetyService.java` | 内容安全核心服务，负责 prompt-injection 检测、不可信文本 prompt 化、HTML 链接过滤和 URL 安全判断。 |
| `src/main/java/com/embabel/tripper/safety/ToolSafetyProperties.java` | 工具安全配置属性，定义单 action 工具预算和高风险工具组。 |
| `src/main/java/com/embabel/tripper/safety/ToolSafetyService.java` | 生成 Agent prompt 中的工具安全策略，并识别需要确认的高风险工具组。 |

### 6.3.6 Java 计划编辑文件

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/embabel/tripper/editing/EditableItineraryDay.java` | 可编辑计划中的每日行程 DTO，保存日期、地点和编辑备注。 |
| `src/main/java/com/embabel/tripper/editing/PlanEditChange.java` | 单条 day-level diff，记录日期、编辑前内容和编辑后内容。 |
| `src/main/java/com/embabel/tripper/editing/PlanEditDiff.java` | 单次编辑 diff 汇总，包含摘要和变更列表。 |
| `src/main/java/com/embabel/tripper/editing/PlanEditVersion.java` | 计划版本模型，保存版本号、编辑指令、HTML、days、diff 和 verifier 结果。 |
| `src/main/java/com/embabel/tripper/editing/PlanEditSession.java` | 单个 planning process 的编辑会话，保存原始约束和全部版本。 |
| `src/main/java/com/embabel/tripper/editing/PlanEditingRepository.java` | 内存编辑会话 repository。 |
| `src/main/java/com/embabel/tripper/editing/PlanEditingService.java` | 计划编辑核心服务，负责创建 session、应用全局/单日编辑、生成 diff 并重跑 verifier。 |
| `src/main/java/com/embabel/tripper/editing/PlanEditController.java` | `/plans/{runId}/edit` 编辑页面 Controller。 |

### 6.4 外部工具和配置文件

| 文件 | 职责 |
| --- | --- |
| `src/main/kotlin/com/embabel/tripper/Brave.kt` | Brave Search 集成。封装 web/news/image/video search service，其中 `BraveImageSearchService.searchImages()` 作为 Agent 可调用工具。 |
| `src/main/kotlin/com/embabel/tripper/config/ToolsConfig.kt` | Spring 配置。创建 `RestClient`，并把 MCP Airbnb 工具过滤注册成 Embabel `ToolGroup`。 |
| `src/main/kotlin/com/embabel/tripper/util/ImageChecker.kt` | HTML 图片链接校验器。扫描 `<img>` 标签，通过 HTTP HEAD 检查图片链接是否有效，并移除无效图片。 |

### 6.5 Web Controller 文件

| 文件 | 职责 |
| --- | --- |
| `src/main/kotlin/com/embabel/tripper/web/JourneyHtmxController.kt` | 旅行规划主 Controller。负责展示表单、接收提交、转换表单到领域对象、创建并启动 AgentProcess。 |
| `src/main/kotlin/com/embabel/agent/web/htmx/GenericProcessingValues.kt` | 通用 processing 页面模型对象。把 process id、页面标题、详情、结果 key 和成功视图写入 Spring `Model`。 |
| `src/main/kotlin/com/embabel/agent/web/htmx/PlatformController.kt` | `/platform` 页面 Controller，返回平台信息页面模板。 |
| `src/main/kotlin/com/embabel/agent/web/htmx/ProcessStatusController.kt` | AgentProcess 状态 Controller。根据 process 状态返回处理中、成功结果或错误页面。 |

### 6.6 Security 文件

| 文件 | 职责 |
| --- | --- |
| `src/main/kotlin/com/embabel/agent/web/security/SecurityConfig.kt` | Spring Security 配置。根据 `embabel.security.enabled` 决定是否启用 OAuth2 认证。 |
| `src/main/kotlin/com/embabel/agent/web/security/CustomOAuth2UserService.kt` | Google OAuth2 用户加载逻辑。把 OAuth2 用户包装为带 `ROLE_USER` 的 `DefaultOAuth2User`。 |
| `src/main/kotlin/com/embabel/agent/web/security/LoginController.kt` | `/login` 登录页 Controller。 |
| `src/main/kotlin/com/embabel/agent/web/security/UserController.kt` | `/user` 用户信息页 Controller。把 OAuth2 用户姓名、邮箱、头像等信息写入模型。 |

### 6.7 Resource 配置文件

| 文件 | 职责 |
| --- | --- |
| `src/main/resources/application.yml` | 默认应用配置。定义端口 `8747`、Thymeleaf、静态资源缓存、安全开关、weather tool、Tripper persona、模型和日志级别。 |
| `src/main/resources/application-docker-ce.yml` | Docker CE / stdio MCP 配置。定义 Brave、fetch、puppeteer、wikipedia、github、google-maps 等 MCP server 的 Docker 启动方式。 |
| `src/main/resources/templates/runs.html` | 最近 Agent run 列表页，展示 route、状态、成本、action 数和工具组计数。 |
| `src/main/resources/templates/run-detail.html` | 单次 Agent run trace 详情页，展示摘要、usage/cost、warnings 和 action timeline。 |
| `src/main/resources/templates/plan-edit.html` | 计划编辑页面，展示约束、编辑表单、最新版本、diff、day notes、verifier 状态和版本历史。 |

### 6.8 Thymeleaf 页面模板

| 文件 | 职责 |
| --- | --- |
| `src/main/resources/templates/journey-form.html` | 旅行规划输入表单。支持出发地、目的地、交通方式、日期、预算、多个 traveler 和 brief 输入。 |
| `src/main/resources/templates/journey-plan.html` | 最终旅行计划页面。展示标题、brief、traveler、地图链接、HTML 计划正文、校验状态、知识来源、Airbnb 住宿链接、参考页面和视频链接。 |
| `src/main/resources/templates/login.html` | 登录页面。提供 Google OAuth2 登录入口，并显示登录错误和退出提示。 |
| `src/main/resources/templates/common/layout.html` | 公共页面布局。加载 CSS、可选 htmx/SSE 脚本、用户 fragment、内容区域和 footer。 |
| `src/main/resources/templates/common/platform.html` | 平台信息页面。链接到 Agent、Tool Groups、Models 和 Zipkin。 |
| `src/main/resources/templates/common/processing.html` | Agent 执行中页面。显示过程状态、终止按钮、事件流、计划步骤和前端更新脚本。 |
| `src/main/resources/templates/common/processing-error.html` | Agent 执行失败或终止页面。 |
| `src/main/resources/templates/common/user-info.html` | 用户资料页。展示 OAuth2 用户头像、姓名、邮箱和退出按钮。 |
| `src/main/resources/templates/common/fragments/empty.html` | 空 fragment，用于 layout 中没有额外 head 内容时占位。 |
| `src/main/resources/templates/common/fragments/footer.html` | 公共 footer fragment。 |
| `src/main/resources/templates/common/fragments/plan-complete.html` | 计划完成后的执行摘要 fragment，展示 goal、action history、成本、模型和 token 使用量。 |
| `src/main/resources/templates/common/fragments/user.html` | 用户认证状态 fragment，展示用户入口或 logout 表单。 |

### 6.9 静态资源文件

| 文件 | 职责 |
| --- | --- |
| `src/main/resources/static/css/embabel-common-dark.css` | 通用深色主题样式。 |
| `src/main/resources/static/css/project.css` | Tripper 项目自定义样式。 |

### 6.10 测试文件

| 文件 | 职责 |
| --- | --- |
| `src/test/kotlin/com/embabel/example/travel/agent/TravelPlanTest.kt` | Travel plan 相关测试占位。当前构造了 `ProposedTravelPlan`，但没有实际断言，需要后续补强。 |
| `src/test/java/com/embabel/tripper/rag/TravelKnowledgeServiceTest.java` | Java RAG 服务测试，验证文档导入、检索、citation 和 prompt context。 |
| `src/test/java/com/embabel/tripper/verification/ItineraryVerificationServiceTest.java` | Java 行程校验测试，覆盖日期缺口、缺失地点、预算、链接、路线和住宿覆盖检查。 |
| `src/test/java/com/embabel/tripper/eval/TravelEvaluationHarnessTest.java` | Java 评测 harness 测试，覆盖数据集规模/维度、CI 子集指标和 JSON/Markdown 报告写出。 |
| `src/test/java/com/embabel/tripper/observability/AgentRunTraceServiceTest.java` | Java 可观测性服务测试，覆盖 action timeline、失败记录、最终 usage/cost 和成本预警。 |
| `src/test/java/com/embabel/tripper/safety/ContentSafetyServiceTest.java` | Java 内容安全测试，覆盖 prompt injection 检测、敏感信息脱敏、HTML 链接过滤和 URL 安全判断。 |
| `src/test/java/com/embabel/tripper/safety/ToolSafetyServiceTest.java` | Java 工具安全测试，覆盖 prompt policy 生成和高风险工具组识别。 |
| `src/test/java/com/embabel/tripper/editing/PlanEditingServiceTest.java` | Java 计划编辑测试，覆盖原始版本创建、单日编辑、全局编辑、diff 和 verifier 重跑。 |

### 6.11 CI 文件

| 文件 | 职责 |
| --- | --- |
| `.github/workflows/maven.yml` | GitHub Actions 构建流程。使用 JDK 21 运行 `./mvnw -U -B test verify`，并在 main 分支失败/恢复时触发通知 workflow。 |

### 6.12 图片资源

| 文件 | 职责 |
| --- | --- |
| `images/input1.jpg` | README 中的输入页截图。 |
| `images/output1.jpg` | README 中的生成结果截图。 |
| `images/map.jpg` | README 中的地图链接截图。 |
| `images/airbnb.jpg` | README 中的 Airbnb 链接截图。 |
| `images/plan.jpg` | README 中的计划和使用量截图。 |
| `images/process.jpg` | README 中的事件流截图。 |

## 7. 当前架构的关键边界

### 7.1 LLM 负责的部分

- 根据 brief 和 traveler 信息生成兴趣点。
- 调研兴趣点并生成自然语言说明。
- 汇总调研内容生成 HTML 旅行计划。
- 根据停留地点生成 Airbnb 搜索 URL。

### 7.2 程序确定性负责的部分

- 表单到领域对象的转换。
- Agent action 编排。
- 并发执行兴趣点调研和住宿搜索。
- 根据 `Day.locationAndCountry` 生成 Google Maps 路线链接。
- 图片 HTML 样式后处理。
- 图片 URL 有效性检查。
- RAG 文档切分、内存检索和 citation 上下文构造。
- 行程日期、地点、路线、预算、链接和住宿一致性校验。
- 评测数据集加载、离线候选计划生成、指标聚合和报告写出。
- Agent run trace 创建、action timeline 记录、usage/cost 汇总和成本预警。
- 不可信 RAG 内容检测、prompt injection 规则识别、敏感信息脱敏、工具安全 prompt policy 和 unsafe link 过滤。
- 计划编辑 session、版本管理、day-level diff 和编辑后 verifier 重跑。
- Spring MVC 页面路由和状态分发。

### 7.3 外部系统负责的部分

- OpenAI 提供 LLM 和 embedding model。
- Brave 提供搜索结果和图片搜索结果。
- Docker MCP Gateway 提供外部 MCP 工具接入。
- Google Maps、Airbnb、Wikipedia、Puppeteer 等通过 MCP 工具被间接调用。
- Zipkin 用于链路追踪入口。

## 8. 后续扩展建议对应的架构位置

如果后续要增强为更适配 AI 应用岗位的项目，可以按以下位置扩展：

| 扩展方向 | 建议新增位置 | 说明 |
| --- | --- | --- |
| RAG 知识库 | `src/main/java/com/embabel/tripper/rag` | Java-owned MVP：文档上传、切分、内存 term-vector 检索、citation；后续替换为 embedding/vector store。 |
| 行程校验器 | `src/main/java/com/embabel/tripper/verification` | Java-owned MVP：日期、预算、路线、链接、住宿一致性校验；后续可替换为 maps-backed verifier。 |
| Agent 评测 | `src/main/java/com/embabel/tripper/eval` 和 `evals/` | Java-owned MVP：30 条数据集、离线确定性 runner、质量指标、JSON/Markdown 报告；后续接真实 Agent runner 和 LLM judge。 |
| 可观测性 | `src/main/java/com/embabel/tripper/observability` | Java-owned MVP：action timeline、usage/cost、latency、工具组摘要、成本预警和 `/runs` trace 页面；后续接低层 tool event 和持久化。 |
| Guardrails | `src/main/java/com/embabel/tripper/safety` | Java-owned MVP：prompt injection 检测、不可信 RAG 包装、工具 policy、敏感信息脱敏、unsafe link 过滤；后续接低层 tool callback allow/block。 |
| 多轮编辑 | `src/main/java/com/embabel/tripper/editing` | Java-owned MVP：plan version、day-level diff、局部编辑、约束保持、verifier rerun；后续接 LLM-backed rewrite action。 |

## 9. 本地运行入口

本机运行入口：

```bash
./mvnw -Dmaven.test.skip=true spring-boot:run
```

默认访问地址：

```text
http://localhost:8747/
```

健康检查：

```text
http://localhost:8747/actuator/health
```

完整生成旅行计划需要配置：

- `OPENAI_API_KEY`
- `BRAVE_API_KEY`
- MCP 相关 secret，例如 `.mcp.env`
- 如启用 OAuth2，还需要 `GOOGLE_CLIENT_ID` 和 `GOOGLE_CLIENT_SECRET`
