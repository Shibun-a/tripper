# Travel Evaluation Harness

Phase 3 adds an offline evaluation harness for the travel-planning agent. It is deterministic by default so local runs and CI can catch regressions without real LLM or MCP calls.

## Dataset

`travel-eval-cases.json` contains 30 travel-planning cases covering:

- Short and longer trips.
- Strict-budget requests.
- Multi-country routes.
- Family and accessibility constraints.
- Food, history, nature, art, museum, and road-trip preferences.
- Cases that require user knowledge-base citation coverage.

## Run

Run the full local evaluation:

```bash
./mvnw -q -DskipTests compile exec:java -Dexec.mainClass=com.embabel.tripper.eval.TravelEvaluationCli
```

Run a smaller subset:

```bash
./mvnw -q -DskipTests compile exec:java -Dexec.mainClass=com.embabel.tripper.eval.TravelEvaluationCli -Dexec.args="--limit 8"
```

Reports are written to:

```text
target/evals/travel-evaluation-report.json
target/evals/travel-evaluation-report.md
```

Current runner:

- Uses `DeterministicEvalPlanCandidateFactory` as an offline stand-in for the planner.
- Runs the Phase 2 itinerary verifier against each candidate plan.
- Computes date coverage, budget violation rate, invalid link rate, citation coverage, tool-call success rate, latency, token cost, and verifier issue counts.

The next step is to add a real Agent-backed candidate factory so the same dataset can compare prompts, model settings, RAG behavior, and tool orchestration changes.
