# Local Development Guide

This guide describes how to run the current Tripper baseline locally.

## Requirements

- Java 21 or newer.
- Docker Desktop, if you want MCP tools and Zipkin.
- API keys for full itinerary generation.

Maven does not need to be installed globally. Use the Maven Wrapper:

```bash
./mvnw --version
```

## Environment Variables

For a homepage smoke test, you can use non-empty dummy values. A real planning run requires valid model and tool credentials.

Minimum local smoke-test values:

```bash
export OPENAI_API_KEY=dummy
export BRAVE_API_KEY=dummy
export GOOGLE_CLIENT_ID=dummy
export GOOGLE_CLIENT_SECRET=dummy
```

Required for full planning:

```bash
export OPENAI_API_KEY=your_openai_api_key
export BRAVE_API_KEY=your_brave_api_key
```

Optional:

```bash
export ANTHROPIC_API_KEY=your_anthropic_api_key
```

Required only if OAuth security is enabled:

```bash
export GOOGLE_CLIENT_ID=your_google_client_id
export GOOGLE_CLIENT_SECRET=your_google_client_secret
```

MCP Docker tools use `.mcp.env`:

```bash
cp mcp.env.example .mcp.env
```

Then edit `.mcp.env`:

```text
brave.api_key=your_brave_api_key
google-maps.api_key=your_google_maps_api_key
```

`.mcp.env` is ignored by git.

## Run Tests

```bash
./mvnw test
```

## Run The Web App Locally

Start the Spring Boot app:

```bash
./mvnw -Dmaven.test.skip=true spring-boot:run
```

Open:

```text
http://localhost:8747/
```

Health check:

```text
http://localhost:8747/actuator/health
```

## Optional Background Services

Start MCP Gateway and Zipkin:

```bash
docker compose up mcp-gateway zipkin
```

Zipkin:

```text
http://localhost:9411/
```

MCP Gateway:

```text
http://localhost:9011/
```

## Docker Run

Build and run the app container:

```bash
docker compose --profile in-docker up --build
```

Open:

```text
http://localhost:8747/
```

## Demo Request

After the app is running, execute the sample request:

```bash
./scripts/demo-plan-request.sh
```

This submits a known travel-planning form to `/travel/journey/plan`. A complete successful run requires valid API keys and MCP tools. Without real keys, this still verifies that the form endpoint accepts the request and starts the processing page.

## Travel Knowledge Base

Phase 1 adds a Java-owned travel knowledge base MVP.

Open:

```text
http://localhost:8747/knowledge
```

Supported sources:

- Pasted text.
- Uploaded `.txt` or `.md` files.
- Imported URLs.

Retrieval debug page:

```text
http://localhost:8747/knowledge/debug
```

Current implementation:

- Java source lives under `src/main/java/com/embabel/tripper/rag`.
- Documents are stored in memory.
- Text is split into chunks.
- Retrieval uses a lightweight term-vector cosine similarity index.
- Retrieved chunks are injected into the Tripper Agent prompt with `[KB:<citationId>]` citation instructions.

The knowledge base is reset when the application restarts. A later phase can replace the in-memory term-vector index with persistent embeddings and a vector store.

## Itinerary Verification

Phase 2 adds a Java-owned itinerary verifier MVP.

Implementation path:

```text
src/main/java/com/embabel/tripper/verification
```

What it checks:

- Requested date coverage from departure to return.
- Duplicate or out-of-range itinerary dates.
- Missing `locationAndCountry` values.
- Approximate route distance and travel time for known cities.
- Budget mentions in the generated plan text.
- URL syntax for page, image, video, and stay links.
- Stay coverage for planned travel days after accommodation lookup.

Agent behavior:

- `TripperAgent.verifyAndRepairTravelPlan` verifies the first proposed plan.
- If the verifier finds ERROR-level issues, the Agent sends a structured repair prompt back to the planner.
- The repaired proposal is verified again before Airbnb lookup.
- The final `TravelPlan` includes a `PlanVerificationResult` shown on `journey-plan.html`.

Current limitations:

- Route estimates use an internal coordinate catalog and haversine approximation.
- URL checks validate syntax only; they do not perform network reachability checks.
- Verification results are persisted in memory and reset when the app restarts.

## Agent Evaluation Harness

Phase 3 adds an offline deterministic evaluation harness.

Run the full evaluation:

```bash
./mvnw -q -DskipTests compile exec:java -Dexec.mainClass=com.embabel.tripper.eval.TravelEvaluationCli
```

Run a CI-sized subset manually:

```bash
./mvnw -q -DskipTests compile exec:java -Dexec.mainClass=com.embabel.tripper.eval.TravelEvaluationCli -Dexec.args="--limit 8"
```

Outputs:

```text
target/evals/travel-evaluation-report.json
target/evals/travel-evaluation-report.md
```

Implementation path:

```text
src/main/java/com/embabel/tripper/eval
evals/travel-eval-cases.json
```

Current limitations:

- The default runner is deterministic and offline; it does not call the real Agent or LLM.
- LLM-judge qualitative scoring is planned but not implemented.

## Agent Run Observability

Phase 4 adds an in-memory AgentOps trace for each planning run.

Recent runs:

```text
http://localhost:8747/runs
```

Single run:

```text
http://localhost:8747/runs/{processId}
```

The planning and result pages also link to the run trace when a process id is available.

Implementation path:

```text
src/main/java/com/embabel/tripper/observability
src/main/resources/templates/runs.html
src/main/resources/templates/run-detail.html
```

Default configuration:

```yaml
embabel:
  tripper:
    observability:
      enabled: true
      capture-prompt-content: false
      max-summary-characters: 240
      max-runs: 100
      cost-warning-threshold-usd: 0.15
```

Current limitations:

- Traces are stored in memory and reset when the app restarts.
- Tool tracking records configured tool groups per action, not every low-level tool request/response.
- The app emits cost warnings, but automatic model downgrade is not implemented yet.

## Guardrails And Tool Safety

Phase 5 adds a Java-owned safety layer for prompt-injection defense, link filtering, tool-use policy, and trace redaction.

Implementation path:

```text
src/main/java/com/embabel/tripper/safety
```

Default configuration:

```yaml
embabel:
  tripper:
    safety:
      tools:
        enabled: true
        max-tool-calls-per-action: 8
        high-risk-tool-groups:
          - browser
          - browser_automation
          - airbnb
```

Current behavior:

- Retrieved knowledge is treated as untrusted content in Agent prompts.
- Prompt-injection and tool-misuse patterns are detected in RAG chunks.
- Suspicious instruction lines are removed before knowledge content is injected into prompts.
- Common secret, token, password, client-secret, API-key, bearer-token, GitHub-token, OpenAI-token, and email patterns are redacted from trace summaries.
- Agent prompts include per-action allowed tool groups and tool-call budget guidance.
- Final rendered HTML and structured output links are filtered to safe `http(s)` URLs.

Current limitations:

- The safety layer uses deterministic rules, not an LLM safety classifier.
- Tool governance is enforced through prompts and output filtering; low-level per-tool callback blocking is a future enhancement.
- Unsupported-claim scoring is not implemented beyond citation-aware RAG guidance and verifier checks.

## Useful Files

- `README.md`: project overview and quick start.
- `PROJECT-NOTE.md`: attribution and personal extension note.
- `infra.md`: architecture and file responsibilities.
- `README-AI-APPLICATION-PLAN.md`: staged AI application portfolio roadmap.
