# Project Note

This repository starts from the open-source Embabel Tripper travel-planning agent.

The current goal of this fork is to evolve the baseline demo into a stronger AI application portfolio project. The original project already provides the Spring Boot/Kotlin web app, Embabel Agent workflow, travel domain model, MCP tool integration, and htmx UI. Personal extensions should be documented explicitly instead of presenting upstream code as original work.

## Current Personal Additions

- `README-AI-APPLICATION-PLAN.md`: staged plan for making the project more suitable for AI application engineering roles.
- `infra.md`: architecture and file responsibility documentation.
- `LOCAL-DEVELOPMENT.md`: local setup, environment, test, and run guide.
- Maven Wrapper files: reproducible Maven usage without requiring a system Maven install.
- `scripts/demo-plan-request.sh`: repeatable sample form submission against a running local app.
- Phase 0 cleanup: README port fixes, wrapper-based CI/run commands, and baseline unit assertions.
- Java-owned Phase 1 RAG MVP under `src/main/java/com/embabel/tripper/rag`, with document import, chunking, retrieval, prompt injection into the Agent workflow, and retrieval debug pages.
- Java-owned Phase 2 itinerary verifier MVP under `src/main/java/com/embabel/tripper/verification`, with structured verification issues, route/budget/date/link/stay checks, a one-shot Agent repair loop, UI verification summary, and unit tests.

## Attribution

- Upstream project: Embabel Tripper.
- Framework: Embabel Agent Framework.
- License: Apache License 2.0, as inherited from the upstream project.

Future feature work should keep this distinction clear by documenting which modules are upstream baseline and which modules are personal extensions.
