# AI Application Portfolio Plan

> Personal development plan based on the existing Tripper travel-planning agent.
> The goal is to turn this project from an open-source agent demo into a stronger AI application portfolio project.

## Positioning

Target role: AI Application Engineer / LLM Application Engineer / Agent Engineer.

Project positioning:

- Build a production-oriented AI travel planning agent on top of Tripper.
- Emphasize RAG, tool orchestration, structured outputs, verification, evaluation, observability, cost control, and guardrails.
- Keep the project honest: this starts from the Embabel Tripper example, and the portfolio value should come from the added engineering layers.

## Baseline

Current project strengths:

- Kotlin + Spring Boot JVM application.
- Embabel Agent workflow with multiple `@Action` steps.
- Domain model for travel brief, travelers, points of interest, days, stays, and travel plans.
- Tool usage through web search, maps, weather, image search, and Airbnb MCP tools.
- htmx web UI, Docker Compose setup, and GitHub Actions workflow.

Current gaps for an AI application resume project:

- It is closer to a framework demo than an original product.
- No strong RAG pipeline or private knowledge grounding.
- Very limited test coverage and no meaningful AI output evaluation.
- Limited observability around token cost, latency, tool calls, and failure modes.
- No explicit prompt-injection or tool-safety guardrails.
- No clear evidence of my own contribution yet.

## Development Path

### Phase 0: Make The Baseline Portfolio-Ready

Purpose: make the project easy to run, easy to explain, and clearly separated from the upstream demo.

Tasks:

- [x] Add a project note explaining this is a personal extension based on Tripper.
- [x] Add Maven Wrapper so reviewers can run the project without installing Maven manually.
- [x] Add a local development guide with required environment variables and Docker services.
- [x] Add a small demo script or sample input that can reproduce one planning run.
- [x] Clean up README links and port descriptions if they are inconsistent.
- [x] Add basic assertions to existing unit tests.

Acceptance criteria:

- A reviewer can clone the repository, configure keys, and understand how to run it.
- The repository clearly states which parts are upstream and which parts are my additions.
- CI runs at least unit tests and build verification.

Resume value:

- Shows engineering ownership, reproducibility, and professional project hygiene.

### Phase 1: RAG Travel Knowledge Base

Purpose: upgrade the app from generic LLM generation to grounded generation over user-provided travel knowledge.

User story:

- As a traveler, I can upload or import travel documents such as guides, policies, personal notes, hotel preferences, and destination research.
- The agent uses these documents when planning and cites the supporting sources.

Functional requirements:

- [x] Support uploading or importing `.txt`, `.md`, or URL-based travel material.
- [x] Extract text and split it into retrievable chunks.
- [x] Retrieve relevant chunks for a travel brief before generating the itinerary.
- [x] Include knowledge-base citation instructions in the generated travel plan prompt.
- [x] Add a retrieval-debug view showing which chunks were used for a run.
- [ ] Add `.pdf` extraction.
- [ ] Generate embeddings and store chunks in a vector index.

Technical requirements:

- [x] Define document, chunk, and retrieval result models in Java.
- [x] Keep RAG retrieval separate from normal web search results.
- [x] Mark retrieved document context as user-provided knowledge.
- [x] Store metadata such as source title, URL/file name, and chunk id.
- [ ] Store page number metadata for PDF sources.
- [ ] Add embedding model and vector-store abstraction.

Acceptance criteria:

- The generated plan references uploaded material when relevant.
- Each citation maps back to a source document or URL.
- The app can explain why a retrieved chunk was used.

Resume value:

- Demonstrates RAG architecture, embeddings, vector retrieval, grounding, and citation-aware generation.

Current implementation note:

- Phase 1 is implemented as a Java-owned RAG MVP under `src/main/java/com/embabel/tripper/rag`.
- The current retriever is an in-memory term-vector index so the app remains simple and buildable.
- The next improvement is to replace the retrieval implementation with embeddings and a vector store without changing the Agent integration surface.

### Phase 2: Itinerary Verifier And Repair Loop

Purpose: show that the agent output is not blindly trusted. The app should validate and repair plans using deterministic checks and tool-backed checks.

User story:

- As a user, I want the itinerary to be realistic, date-consistent, budget-aware, and route-aware before I receive the final result.

Functional requirements:

- [x] Validate that all dates from departure to return are covered.
- [x] Validate that each day has a location.
- [x] Validate that locations are ordered reasonably for the route.
- [x] Estimate travel time or distance between consecutive locations.
- [x] Check whether each stay matches the relevant travel days.
- [x] Check whether the plan exceeds the daily budget.
- [x] Check whether image and page links are valid.
- [x] If issues are found, send a structured repair request back to the planner.

Technical requirements:

- [x] Add a `PlanVerificationResult` domain model.
- [x] Add issue categories such as `DATE_GAP`, `ROUTE_TOO_LONG`, `BUDGET_EXCEEDED`, `INVALID_LINK`, and `MISSING_STAY`.
- [x] Add severity levels: `INFO`, `WARNING`, `ERROR`.
- [x] Persist verifier output for each planning run.
- [x] Display verification status in the UI.

Current implementation note:

- Phase 2 is implemented as a Java-owned verifier MVP under `src/main/java/com/embabel/tripper/verification`.
- The verifier performs deterministic checks for date coverage, duplicate/out-of-range dates, missing locations, budget mentions, URL syntax, stay coverage, and route estimates.
- Route estimates use an internal city coordinate catalog and haversine approximation so local tests remain deterministic.
- The Agent runs a one-shot repair action when blocking verifier errors are found, then verifies the repaired proposal again before accommodation lookup.
- Link checks currently validate URL structure. Network reachability checks can be added later with a timeout-controlled link checker.

Acceptance criteria:

- Invalid or incomplete plans are detected before final display.
- Repair prompts include concrete structured issues.
- The final plan includes a verification summary.

Resume value:

- Demonstrates structured output validation, agent reliability, and hybrid deterministic + LLM repair workflows.

### Phase 3: Agent Evaluation Harness

Purpose: move from demo quality to measurable quality.

User story:

- As a developer, I can run an evaluation suite to compare prompt, model, RAG, and tool changes.

Functional requirements:

- [x] Create a dataset of 30-50 travel planning cases.
- [x] Include cases for short trips, long trips, strict budgets, multi-country routes, family trips, accessibility constraints, and food/history/nature preferences.
- [x] Run an offline planner stand-in against the dataset in an evaluation mode.
- [x] Compute deterministic metrics.
- [ ] Optionally use an LLM judge for qualitative scoring.
- [x] Generate an evaluation report.

Metrics:

- Date coverage rate.
- Budget violation rate.
- Invalid link rate.
- Citation coverage rate.
- Tool-call success rate.
- Average latency.
- Average token cost.
- Verifier error count.
- LLM judge score for relevance, personalization, and feasibility.

Acceptance criteria:

- [x] Evaluation can be run locally with one command.
- [x] Evaluation output is stored as JSON and Markdown.
- [x] CI can run a lightweight regression subset through unit tests.

Current implementation note:

- Phase 3 is implemented as a Java-owned deterministic evaluation MVP under `src/main/java/com/embabel/tripper/eval`.
- The dataset lives in `evals/travel-eval-cases.json` and currently contains 30 portfolio-oriented travel cases.
- The current runner uses `DeterministicEvalPlanCandidateFactory` so CI can evaluate date coverage, budget/link/citation checks, tool-call success, latency, token cost, and verifier issues without real LLM or MCP calls.
- The next improvement is to add an Agent-backed candidate factory and optional LLM judge so the same dataset can compare prompts, model settings, RAG behavior, and tool orchestration changes.

Resume value:

- Shows AI evaluation, regression testing, prompt quality control, and measurable iteration.

### Phase 4: AgentOps Observability

Purpose: make every planning run debuggable and cost-aware.

User story:

- As a developer or operator, I can inspect each agent run step by step and understand cost, latency, tools, and failures.

Functional requirements:

- [ ] Track each agent action in a run timeline.
- [ ] Track model name, prompt size, completion size, token cost, and latency.
- [ ] Track tool calls, status, input summary, output summary, and error messages.
- [ ] Add a `/runs/{id}` or similar page to inspect execution history.
- [ ] Add cost budget warnings and model downgrade behavior.

Technical requirements:

- [ ] Define an `AgentRunTrace` model.
- [ ] Capture event data without logging sensitive prompt content by default.
- [ ] Add configurable verbosity levels for local debugging versus production mode.

Acceptance criteria:

- Each itinerary has a traceable execution record.
- Expensive or failed steps are easy to identify.
- Cost and latency are visible in the UI or report.

Resume value:

- Demonstrates production AI application concerns: observability, debugging, latency, and cost management.

### Phase 5: Guardrails And Tool Safety

Purpose: reduce unsafe or unreliable behavior when the agent reads web content and calls tools.

User story:

- As a user, I want the agent to avoid following malicious webpage instructions, leaking private data, or calling tools outside allowed boundaries.

Functional requirements:

- [ ] Treat external webpage content as untrusted context.
- [ ] Add prompt-injection detection for retrieved web content.
- [ ] Add tool permission rules and per-run tool budgets.
- [ ] Add confirmation before high-cost or high-risk operations.
- [ ] Add output filtering for unsafe links or unsupported claims.
- [ ] Redact sensitive user fields from logs and traces.

Acceptance criteria:

- Webpage instructions cannot override the agent's system or developer instructions.
- Risky tool calls require explicit permission or are blocked.
- Logs do not expose sensitive traveler information by default.

Resume value:

- Shows responsible AI, prompt-injection defense, tool governance, and privacy awareness.

### Phase 6: Multi-Turn Itinerary Editing Copilot

Purpose: turn the app from a one-shot generator into an interactive AI product.

User story:

- As a traveler, I can revise the generated plan through natural language while keeping parts I already like.

Example commands:

- "Keep the first two days, but make the rest cheaper."
- "Avoid long drives on the third day."
- "Replace museums with nature activities."
- "Add more food and wine recommendations."
- "Explain why you changed the route."

Functional requirements:

- [ ] Store generated plan versions.
- [ ] Support localized edits to selected days.
- [ ] Show a diff between the old and new itinerary.
- [ ] Keep user constraints across turns.
- [ ] Re-run verifier after every edit.

Acceptance criteria:

- Users can revise a plan without regenerating everything from scratch.
- The app shows what changed and why.
- Verifier and cost tracing still work for edited plans.

Resume value:

- Demonstrates conversational UX, stateful AI workflows, versioning, and human-in-the-loop product design.

## Recommended Build Order

Priority 0:

- [ ] Baseline cleanup.
- [ ] Maven Wrapper and reproducible run instructions.
- [ ] Basic test assertions.

Priority 1:

- [x] RAG travel knowledge base MVP.
- [ ] Itinerary verifier.
- [ ] Evaluation harness.

Priority 2:

- [ ] AgentOps observability.
- [ ] Guardrails and tool safety.

Priority 3:

- [ ] Multi-turn itinerary editing.
- [ ] Polished demo and portfolio write-up.

## Suggested Milestones

Milestone 1: Portfolio Baseline

- Project attribution is clear.
- Build and local run instructions are reliable.
- Existing domain logic has basic tests.

Milestone 2: Grounded Planning

- User documents can be indexed.
- Plans include citations.
- Retrieval debug output is available.

Milestone 3: Reliable Planning

- Plans are verified before display.
- Invalid plans trigger repair.
- Evaluation suite reports quality metrics.

Milestone 4: Production Readiness

- Run traces show action flow, tools, cost, and latency.
- Guardrails protect against unsafe tool use and prompt injection.
- CI includes build, unit tests, and lightweight eval checks.

Milestone 5: Product Experience

- Users can edit plans through multi-turn interaction.
- The UI shows versions, diffs, verification status, and cost summary.
- The final README includes demo screenshots, architecture, metrics, and my contributions.

## Final Resume Framing

Possible project title:

Production-Ready AI Travel Planning Agent

Possible resume bullet:

- Extended an open-source Spring Boot/Kotlin travel planning agent into a production-oriented AI application with RAG-based private knowledge grounding, structured itinerary verification, automated evaluation, AgentOps observability, and tool-safety guardrails.

Stronger resume bullet after implementation:

- Built a multi-stage LLM agent that combines private document RAG, web/maps/weather/Airbnb tool calls, deterministic itinerary validation, automatic repair loops, and evaluation reports measuring date coverage, budget compliance, citation coverage, invalid links, latency, and token cost.

## What Not To Prioritize First

- Do not only polish the UI before adding AI engineering depth.
- Do not only add more model providers without evaluation or routing logic.
- Do not only add a chat box without memory, versioning, verification, or traceability.
- Do not only write better prompts without tests, metrics, and failure handling.
- Do not present the original Tripper code as fully original work.
