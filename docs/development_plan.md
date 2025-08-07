## textProc: Headless Management API Migration Plan

Goal: Remove the web UI and expose management via REST and Actuator so an external app can orchestrate this service (and others in the RAG pipeline).

Approved scope:
- Support both profiles: `standalone` and `scdf`
- Pending list: directory scan in `standalone`, queue depth in `scdf`
- HDFS only; remove S3 references
- Public APIs for now
- Remove the UI entirely

Phases

Phase 0 — Baseline and Docs
- Verify versions from `versions.txt`
- Capture current controls and endpoints in this plan
- Update docs as changes land

Phase 1 — Management REST APIs (Additive)
- Add `GET /api/files/processed` returning `List<FileProcessingInfo>`
- Add `GET /api/files/pending`
  - standalone: list files in configured input directory
  - scdf: return queue depth via RabbitMQ Management API if configured; otherwise `unavailable`
- Keep and standardize existing:
  - POST `/api/processing/start`
  - POST `/api/processing/stop`
  - POST `/api/processing/reset`
  - GET `/api/processing/state`
- Add Actuator health details via a custom `HealthIndicator`

Phase 2 — Remove UI (Breaking change approved)
- Remove Thymeleaf template(s) and view controller
- Remove Thymeleaf dependency and config
- Update README to reflect headless operation

Phase 3 — HDFS-only
- Remove S3/MinIO code and dependency
- Ensure HDFS flow records `FileProcessingInfo` consistently

Phase 4 — Health and Metrics
- Expose Actuator endpoints: `health, info, metrics, bindings, stream-control`
- Health details include: processing state, binding state (scdf), HDFS reachability, processed count
- Add RabbitMQ notification publisher (default queue: `pipeline.metrics`) sending embedProc-compatible schema plus `meta.service`

Phase 5 — Documentation refresh
- Update `README.md` (features, usage)
- Update `implementation_details.md` (API-first design)
- Update `CONTROLS.md` (programmatic controls)
- Update `gotchas.md` (SCDF queue depth requires RabbitMQ management)

Tracking
- Phase 0: Completed
- Phase 1: Completed
- Phase 2: Completed
- Phase 3: Completed
- Phase 4: In Progress
- Phase 5: In Progress


