# Project Note

TripSmith started from the open-source Embabel Tripper travel-planning example and has been
rebuilt into a production-oriented AI application. This note keeps the boundary between
upstream work and personal work explicit, because presenting upstream code as original work
would be dishonest — and because the boundary itself is the clearest map of what this project
adds.

## Provenance

- Upstream project: [Embabel Tripper](https://github.com/embabel/tripper) — the baseline
  Spring Boot/Kotlin web app, the Embabel Agent workflow skeleton, the travel domain model,
  MCP tool integration and the htmx UI.
- Framework: [Embabel Agent Framework](https://github.com/embabel/embabel-agent).
- License: Apache License 2.0, inherited from upstream. Files derived from upstream retain
  their original license headers; see `NOTICE`.
- The codebase now lives under the `io.github.shibuna.tripsmith` namespace. Class names that
  describe the domain (e.g. `TripperAgent`) were kept where renaming added no clarity.

## Personal Work (module by module)

- `agent/` (Kotlin): the original demo agent grew into a verified pipeline and was then
  decomposed — prompts (`TripPrompts`), deterministic degradation (`FallbackPlans`), a
  tracing decorator (`ActionTracer`), display-safety post-processing
  (`PlanHtmlPostProcessor`), model routing and verifier mapping are separate components, and
  `TripperAgent` keeps orchestration only. Chinese-language runs route to domestic models;
  plan generation uses a two-call structure/HTML split for weak-JSON models; failed planner
  calls degrade to deterministic fallbacks instead of failing the run.
- `rag/`: travel knowledge base with chunking, multilingual local ONNX embeddings, vector
  retrieval, untrusted-content marking, citation instructions and a retrieval-debug view.
  SSRF-guarded, size-capped URL imports.
- `verification/`: deterministic itinerary verifier (date coverage, route estimates from a
  data-file city catalog, tiered budget checks, link/stay checks) feeding a one-shot LLM
  repair loop in the agent.
- `eval/`: two-tier evaluation — a deterministic CI tier exercising the verifier and metrics
  pipeline over a 30-case dataset, and a gated agent tier (`EVAL_AGENT=true`) that runs the
  real agent and scores plans with an LLM judge agent.
- `observability/`: per-run action timelines with cost/token/latency capture, redaction, a
  bounded store and `/runs` inspection pages.
- `safety/`: prompt-injection detection for retrieved content, output-side HTML whitelist
  sanitization, tool policy prompts, URL import guarding and trace redaction.
- `editing/`: versioned plan-edit sessions with day-scoped notes, diffs and verifier reruns
  (LLM-backed rewriting is the next step).
- Persistence: every store sits behind a port with an in-memory adapter (default, zero
  dependencies) and a JPA adapter plus pgvector index on the `postgres` profile.
- Engineering: server-side form validation before spend, cost engineering (tool-call caps,
  research truncation, POI scaling, measured $0.45–0.52/run), CI, tests, and the docs in
  `LOCAL-DEVELOPMENT.md` / `infra.md` / `README-AI-APPLICATION-PLAN.md`.

Future feature work should keep this distinction clear by documenting which modules are
upstream baseline and which are personal extensions.
