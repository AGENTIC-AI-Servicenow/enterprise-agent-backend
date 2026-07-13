# Enterprise Help Desk Copilot — Execution Plan for Client-Ready Demo

## 1. Executive framing

This project is no longer just an AI chat connected to ServiceNow. It is evolving into an **operational copilot for Help Desk technicians and support analysts**, designed to reduce decision time, improve procedural compliance, and make operational actions traceable and auditable.

The central business premise is critical:

> The technician already lives in ServiceNow and already sees tickets all day.  
> Therefore, the copilot must not compete with native ticket views. It must **compress analysis, prioritize next action, validate procedure, draft operational outputs, and enforce approval before write-back**.

This document translates the current project state into a **serious client-facing demo strategy**, with a concrete architecture target, backlog, narrative, documentation package, and product decisions.

---

## 2. Current state assessment

### 2.1 What already exists in the project

The current solution already contains meaningful foundations:

#### Backend
- Spring Boot / Java backend connected to ServiceNow Table API
- Azure OpenAI integration
- Conversational orchestration layer
- Intent classification and routing
- Incident retrieval and rendering services
- Operational prioritization heuristics
- Shared visible-context mechanism between frontend and backend
- Daily briefing foundation via `BriefingService` and `BriefingController`
- Corrected routing for list/count cases
- Explicit count-aware responses for open incidents

Relevant current classes include:
- `AgentOrchestrator`
- `AgentDecisionEngine`
- `IntentClassifier`
- `ServiceNowClient`
- `IncidentRendererService`
- `IncidentPolicyService`
- `OperationalRiskService`
- `AnalyticsService`
- `BriefingService`

#### Frontend
- Workspace-style conversational UI in `frontend/src/app/agent/page.tsx`
- Enterprise-like layout with contextual right panel
- Shared visible incidents context
- Initial daily briefing card
- More operational visual language, less decorative UI
- Fallback logic to build local briefing from visible incidents when ServiceNow is unavailable

### 2.2 What this means strategically

The project is already beyond “hello world chatbot” stage.  
It has enough building blocks to become a **credible enterprise demo** if the next step is focused on:

1. **Sharper business framing**
2. **Deterministic workflow logic**
3. **Approval-governed write actions**
4. **Documented architecture and operating model**
5. **A demo storyline that proves operational value**

---

## 3. Critical evaluation of the 5 proposed flows

## A. Critical evaluation of scope

### Flow 1 — Daily operational briefing

#### Demo value
Very high.

#### Why it matters
This is the best entry point because it immediately reframes the product from:
- “ask me about tickets”

to:
- “I start your day with operational focus”

#### Real differentiator
- actionable synthesis instead of list repetition
- technician-specific prioritization
- deterministic operational metrics
- LLM as narrator, not as calculator

#### Implementation risk
Low to medium.

#### Risks
- if briefing depends too much on live ServiceNow quality/connectivity, it may fail during demo
- briefing can feel generic unless seeded with realistic incidents and meaningful heuristics

#### MVP recommendation
Must stay in the demo.  
This should be the **hero flow**.

---

### Flow 2 — Unblock stalled “On Hold” ticket

#### Demo value
Very high.

#### Why it matters
This is where the copilot moves from passive analysis into **guided operational action**.

#### Real differentiator
- compresses diagnosis time
- proposes next step
- drafts technician communication
- demonstrates human-in-the-loop approval before write

#### Implementation risk
Medium.

#### Risks
- requires ticket history/work notes quality
- if draft generation is weak, it may feel superficial
- write-back flow requires explicit governance and audit trail

#### MVP recommendation
Must be included in the first serious demo phase, right after Flow 1.

---

### Flow 3 — Assisted closure with procedural validation

#### Demo value
Very high.

#### Why it matters
This is a strong “trust moment” because it shows the copilot does not just write text; it validates operational policy before action.

#### Real differentiator
- deterministic policy gate
- compliance-first behavior
- explicit approval before closure
- clear separation of “can close” vs “should not close yet”

#### Implementation risk
Medium to high.

#### Risks
- closure rules may vary by ticket type and instance conventions
- cross-table logic for `incident`, `sc_task`, `sc_req_item` can grow fast
- requires careful scoping to avoid overbuilding

#### MVP recommendation
Include in demo, but narrow scope:
- incident closure
- `sc_task` closure with limited parent validation
- explicit blocking reasons
- editable closure note draft

Do **not** attempt full generalized workflow closure engine yet.

---

### Flow 4 — Batch/pattern detection

#### Demo value
High.

#### Why it matters
This is the clearest proof that the system reasons over **sets**, not just individual records.

#### Real differentiator
- lot detection
- repeated symptom detection
- PRB candidate suggestion
- proactive operational intelligence

#### Implementation risk
Medium.

#### Risks
- similarity logic can become over-engineered
- embeddings may be unnecessary for demo
- false positives can reduce trust

#### MVP recommendation
Include, but simplify:
- deterministic clustering first
- keyword/similarity-based grouping
- repeated caller / repeated short description / category concentration
- PRB candidate suggestion as recommendation only

Embeddings should be optional, not foundational.

---

### Flow 5 — Monthly SLA report on demand

#### Demo value
Medium to high.

#### Why it matters
Strong for leadership and management stakeholders, less strong for the technician-facing story.

#### Real differentiator
- replaces Excel work
- creates executive narrative from operational data
- supports management reporting

#### Implementation risk
Medium.

#### Risks
- can drift into BI/reporting territory
- if the dataset is not curated, output can feel generic
- export quality matters for credibility

#### MVP recommendation
Keep as a secondary flow, not the core hero journey.  
Use it as the **closing managerial proof point**, not the opening act.

---

### Recommended priority order

1. Flow 1 — Daily briefing
2. Flow 2 — Unblock stalled ticket
3. Flow 3 — Assisted closure
4. Flow 4 — Batch/pattern detection
5. Flow 5 — Monthly SLA report

This order is correct and should be preserved.

---

## 4. Architecture target

## B. Target architecture

### 4.1 Architectural principle

The product must be structured around this rule:

> **Deterministic logic decides facts, policy, eligibility, metrics, and guardrails.  
> LLM generates narrative, drafts, explanations, and summaries.**

The LLM must not be the source of truth for:
- SLA calculation
- procedural validation
- approval logic
- closure eligibility
- contact attempt counting
- compliance windows
- write authorization

### 4.2 Target backend layers

#### 1. Experience / orchestration layer
Purpose:
- receive user intent
- route to proper flow
- combine deterministic and generative services
- manage response envelope

Current assets:
- `AgentOrchestrator`
- `AgentDecisionEngine`
- `IntentClassifier`

Recommended evolution:
- keep conversational orchestrator for generic interaction
- add explicit flow services and controllers for deterministic use cases

---

#### 2. Deterministic operational services
Purpose:
- compute facts and procedural state

Recommended services:
- `BriefingService`
- `StalledTicketService`
- `ClosureValidationService`
- `PatternDetectionService`
- `MonthlyReportService`
- `SlaPolicyService`
- `ContactAttemptPolicyService`
- `ConformityWindowPolicyService`
- `ApprovalWorkflowService`
- `AuditLogService`

---

#### 3. LLM composition services
Purpose:
- transform structured context into polished narrative or drafts

Recommended services:
- `BriefingNarrativeService`
- `DraftRecommendationService`
- `ClosureNoteDraftService`
- `ExecutiveReportNarrativeService`

These should accept structured DTOs and produce text.  
They should not fetch data or calculate policy.

---

#### 4. ServiceNow integration layer
Purpose:
- isolate ServiceNow table access and write operations

Recommended structure:
- `ServiceNowQueryClient`
- `ServiceNowWriteClient`
- `ServiceNowMapper`
- `ServiceNowTableGateway`

Write operations must always be invoked **after approval**.

---

#### 5. Governance / approval / audit layer
Purpose:
- enforce proposal → review → approval → execution → audit

Recommended services:
- `DraftVersionService`
- `ApprovalWorkflowService`
- `AuditLogService`

---

### 4.3 Recommended API contracts

#### Flow 1 — Daily briefing
- `GET /api/agent/briefing?technician={sys_id}`

Response shape:
- `summary`
- `context.metrics`
- `context.attentionToday`
- `context.complianceWatch`
- `context.patterns`
- `sourceMode` (`remote`, `local-fallback`, `seed-demo`)

---

#### Flow 2 — Stalled ticket
- `GET /api/agent/stalled/{ticketNumber}`
- `POST /api/agent/stalled/{ticketNumber}/draft`
- `POST /api/agent/stalled/{ticketNumber}/approve`
- `POST /api/agent/stalled/{ticketNumber}/execute`

Suggested response fields:
- ticket
- holdReason
- daysOnHold
- contactAttempts
- unblockRecommendation
- draft
- validationFlags
- approvalState

---

#### Flow 3 — Closure validation
- `GET /api/agent/close-check/{ticketNumber}`
- `POST /api/agent/close/{ticketNumber}/draft`
- `POST /api/agent/close/{ticketNumber}/approve`
- `POST /api/agent/close/{ticketNumber}/execute`

Suggested fields:
- `eligible`
- `blockingReasons`
- `validationChecks[]`
- `suggestedClosePayload`
- `draftCloseNotes`
- `cascadeOption`

---

#### Flow 4 — Patterns
- `GET /api/agent/patterns`
- optional: `POST /api/agent/patterns/{patternId}/propose-problem`
- optional: `POST /api/agent/patterns/{patternId}/create-batch-draft`

Suggested fields:
- `patternId`
- `patternType`
- `tickets`
- `confidence`
- `recommendedAction`
- `problemCandidate`

---

#### Flow 5 — Monthly report
- `GET /api/agent/report?period=YYYY-MM`
- `GET /api/agent/report?period=YYYY-MM&format=xlsx`
- `GET /api/agent/report?period=YYYY-MM&format=pdf`

Suggested fields:
- `summary`
- `kpis`
- `byCategory`
- `byTechnician`
- `sla`
- `comparisonPreviousMonth`
- `exportStatus`

---

### 4.4 Local entities/tables recommended

#### `agent_audit_log`
Mandatory.

Suggested fields:
- `id`
- `flow_id`
- `ticket`
- `draft_version`
- `approved_by`
- `approval_timestamp`
- `execution_timestamp`
- `action_type`
- `payload_enviado`
- `servicenow_response`
- `status`
- `session_id`
- `user_comment`

#### `agent_draft`
Suggested fields:
- `id`
- `flow_id`
- `ticket`
- `draft_type`
- `draft_version`
- `draft_content`
- `generated_by`
- `generated_at`
- `edited_by`
- `edited_at`
- `approval_state`

#### `agent_flow_execution`
Suggested fields:
- `flow_id`
- `flow_type`
- `ticket`
- `started_by`
- `started_at`
- `current_stage`
- `completed_at`
- `outcome`

#### `agent_demo_seed_metadata`
Optional, useful for demo mode:
- `dataset_name`
- `ticket_count`
- `scenario_tags`
- `last_loaded_at`

---

### 4.5 Observability and governance

Must include:
- structured logging per flow
- correlation id / flow id
- approval audit events
- degraded mode indicators
- prompt versioning for narrative outputs
- distinction between:
  - deterministic result
  - generated narrative
  - approved action
  - executed action

---

## 5. Roadmap by phases

## C. Proposed roadmap

### Phase 1 — Demo-ready
Objective:
Create a convincing enterprise demo that proves value without overcommitting autonomy.

#### Included
- Flow 1 complete and polished
- Flow 2 with draft + approval + simulated or controlled write
- Flow 3 with deterministic close-check + editable close draft
- Flow 4 simplified deterministic pattern detection
- Flow 5 basic monthly report view with optional export mock
- local audit model defined
- approval pattern visible in UI
- curated seed dataset of ~20 realistic tickets

#### Not required yet
- generalized embeddings engine
- production-grade persistence stack if not necessary
- complex workflow automation beyond scoped demo cases

---

### Phase 2 — Pilot
Objective:
Prove operational usability with real team behavior.

#### Included
- real audit persistence
- real write-back execution after approval
- policy rules externalized/configurable
- better failure handling for ServiceNow connectivity
- reporting hardening
- seeded + live blended mode
- user feedback capture

---

### Phase 3 — Productization
Objective:
Make it deployment-ready for enterprise governance.

#### Included
- RBAC and permission boundaries
- multi-team policy configuration
- configurable prompts and templates
- monitoring dashboards
- security review artifacts
- resilience and retry strategy
- data retention and privacy controls
- approval workflow integration with enterprise standards

---

## 6. Demo narrative

## D. Demo narrative

### 6.1 Core story

Do not present this as:
- “let me show you five features”

Present it as:
- “this is how a support analyst starts the day, decides faster, acts safely, and leaves auditable trace”

### 6.2 Recommended demo storyline

#### Scene 1 — Start of day
Open the workspace and show the daily briefing.

Message:
- the copilot does not repeat lists
- it tells the technician where to focus

**Wow moment:**  
The assistant immediately highlights what matters today.

**Trust moment:**  
Metrics and risk are deterministic, not hallucinated.

---

#### Scene 2 — Unblock an On Hold ticket
Click one ticket from “Atención hoy”.

Message:
- the copilot diagnoses why it is stalled
- suggests concrete next action
- drafts a follow-up note or communication

**Wow moment:**  
The analyst goes from confusion to a ready action in seconds.

**Trust moment:**  
The action is still editable and requires approval.

---

#### Scene 3 — Assisted closure
Take a ticket close to resolution and ask to prepare closure.

Message:
- the copilot checks if closure is actually allowed
- if blocked, it explains why
- if allowed, it drafts closure notes and offers controlled execution

**Wow moment:**  
The copilot understands procedure, not just language.

**Trust moment:**  
It refuses unsafe action and shows explicit validation gates.

---

#### Scene 4 — Patterns in the queue
Ask whether there are patterns.

Message:
- the copilot sees clusters, repeated symptoms, potential problem candidates

**Wow moment:**  
The solution reasons over the portfolio, not just one ticket.

**Trust moment:**  
Pattern logic is explainable and actionable.

---

#### Scene 5 — Managerial view
Generate a monthly report.

Message:
- the same operational intelligence scales from technician productivity to leadership reporting

**Wow moment:**  
A report is generated on demand instead of manually building Excel.

**Trust moment:**  
KPIs are computed from structured logic; LLM only produces narrative.

---

### 6.3 Sequence recommendation

Recommended live sequence:
1. Daily briefing
2. Stalled ticket unblock
3. Assisted closure
4. Pattern detection
5. Monthly report

This sequence moves from:
- individual productivity
- to procedural compliance
- to operational intelligence
- to leadership visibility

---

## 7. Documentation package

## E. Documentation package

### For executive client audience
1. **Executive One-Pager**
   - business problem
   - target users
   - business value
   - key differentiators
   - expected outcomes

2. **Client Demo Narrative Deck**
   - story of the technician journey
   - 5 flow storyline
   - trust/approval model
   - expected ROI hypotheses

### For operational leadership
3. **Operational Use Case Blueprint**
   - flow-by-flow value
   - procedure alignment
   - SLA and APY08 mapping
   - team adoption model

4. **Target Operating Model**
   - who uses what
   - when approval is required
   - escalation scenarios
   - how audit and accountability work

### For technical team
5. **Solution Architecture Document**
   - layers
   - components
   - integrations
   - data model
   - deterministic vs LLM boundary

6. **API Contract Specification**
   - request/response payloads
   - approval-state model
   - error model
   - degraded mode behavior

7. **Execution Backlog and Delivery Plan**
   - epics
   - features
   - dependencies
   - acceptance criteria

### For security and governance
8. **AI Governance & Approval Control Note**
   - approve-before-write
   - prompt role boundaries
   - audit model
   - sensitive action handling

9. **Security & Data Handling Note**
   - PII exposure considerations
   - token handling
   - data retention
   - logging and audit

### For adoption
10. **User Enablement Guide**
   - what the copilot does
   - what it does not do
   - how to interpret recommendations
   - how approval works

---

## 8. Backlog design

## F. Execution-ready backlog

## Epic 1 — Operational Briefing Experience

### Feature 1.1 — Deterministic briefing metrics
**Acceptance criteria**
- Computes open tickets, SLA risk, stalled tickets, compliance watch
- Uses Java deterministic logic
- Does not depend on LLM for calculations

### Feature 1.2 — Narrative generation from structured context
**Acceptance criteria**
- LLM receives structured `BriefingContext`
- Output is concise, executive, actionable
- Fallback exists if LLM fails

### Feature 1.3 — Ticket chips and quick transitions
**Acceptance criteria**
- Tickets from briefing are clickable
- Clicking launches stalled/analysis/closure flows

### Feature 1.4 — Source mode visibility
**Acceptance criteria**
- UI displays whether briefing is remote, local fallback, or seeded demo
- No silent degradation

---

## Epic 2 — Stalled Ticket Resolution

### Feature 2.1 — Stalled ticket diagnostic endpoint
**Acceptance criteria**
- Returns ticket, notes, days on hold, contact attempts, recommended next step

### Feature 2.2 — Follow-up draft generation
**Acceptance criteria**
- Generates editable operational draft
- Draft is linked to flow id and ticket

### Feature 2.3 — Approval-before-write execution
**Acceptance criteria**
- User must explicitly approve before posting to ServiceNow
- Execution writes to work notes only after approval

### Feature 2.4 — Audit logging
**Acceptance criteria**
- Stores flow id, draft version, approved by, payload sent, timestamp

---

## Epic 3 — Assisted Closure with Procedural Validation

### Feature 3.1 — ClosureValidator rules engine
**Acceptance criteria**
- Evaluates closure eligibility by ticket type
- Returns OK/BLOCKED checks
- Uses deterministic APY08-aligned logic

### Feature 3.2 — Close note draft
**Acceptance criteria**
- Generates editable closure note only if validation passes or with clear draft scope
- Keeps procedural findings visible

### Feature 3.3 — Cascading close option
**Acceptance criteria**
- For scoped cases only
- Explicitly shows impact of closing parent item
- Requires separate approval

### Feature 3.4 — Closure audit
**Acceptance criteria**
- Logs every approved closure attempt and payload

---

## Epic 4 — Pattern Detection & Batch Reasoning

### Feature 4.1 — Deterministic pattern detector
**Acceptance criteria**
- Detects repeated themes by short description/category/caller
- Produces explainable clusters

### Feature 4.2 — PRB candidate suggestion
**Acceptance criteria**
- Flags likely problem candidates
- Does not auto-create PRB
- Presents rationale

### Feature 4.3 — Batch action recommendation
**Acceptance criteria**
- Recommends grouped treatment path
- Keeps approval mandatory for any write proposal

---

## Epic 5 — Monthly SLA Report

### Feature 5.1 — Deterministic monthly aggregation
**Acceptance criteria**
- Computes volume, category, technician, SLA, out-of-SLA, month-over-month comparison

### Feature 5.2 — Executive narrative
**Acceptance criteria**
- LLM produces concise summary from aggregated data
- No KPI calculation delegated to model

### Feature 5.3 — Export package
**Acceptance criteria**
- Supports at least one export path for demo
- PDF/XLSX may be partially simulated if needed, but clearly scoped

---

## Epic 6 — Approval, Audit, and Governance

### Feature 6.1 — Draft entity model
**Acceptance criteria**
- Every proposed action has versioned draft metadata

### Feature 6.2 — Approval workflow state
**Acceptance criteria**
- Draft states: proposed, edited, approved, discarded, executed

### Feature 6.3 — Audit log persistence
**Acceptance criteria**
- Sensitive flows create durable audit entries

### Feature 6.4 — Flow traceability
**Acceptance criteria**
- Every flow gets `flow_id`
- UI and backend logs share same reference

---

## Epic 7 — Demo Reliability

### Feature 7.1 — Seed dataset mode
**Acceptance criteria**
- Demo can run with ~20 realistic tickets
- Includes stalled, closure-ready, compliance-watch, repeated-pattern scenarios

### Feature 7.2 — Graceful degradation
**Acceptance criteria**
- ServiceNow/API failures do not break demo
- UI indicates fallback mode

### Feature 7.3 — Demo operator controls
**Acceptance criteria**
- Easy reset/reload dataset
- Optional scenario toggles if needed

---

## 9. Quick wins

### Quick wins to implement next
1. Formalize `sourceMode` in briefing response
2. Add draft/approval/audit DTOs for write-capable flows
3. Create `ClosureValidationService` skeleton with APY08 rules
4. Create `StalledTicketService` with contact-attempt counting rules
5. Create deterministic `PatternDetectionService`
6. Define local audit model even if persistence is in-memory at first
7. Seed demo dataset with realistic 20-ticket portfolio

---

## 10. Visible technical debt

### Debt already visible
- current workspace is strong visually but still centered around conversation more than explicit workflow cards
- approval model is not yet formalized end-to-end
- degraded/fallback behavior exists but is not yet standardized across all flows
- local persistence/audit model is not yet implemented
- ServiceNow write path governance is not yet enforced as platform rule
- pattern and closure flows are still conceptual, not structured backend capabilities

---

## 11. Risks and key decisions

## G. Risks and key decisions

### What should not be added yet
- full autonomous workflow execution
- embeddings-first architecture
- broad multi-table generalized automation engine
- complex PDF generation stack unless specifically needed
- multi-agent choreography just for demo theatrics

### What must remain deterministic
- SLA rules
- severity thresholds
- closure eligibility
- contact attempt counting
- conformity windows
- approval gating
- audit logging
- recommendation scoring inputs where business explanation matters

### Where LLM is truly useful
- briefing narrative
- concise diagnosis explanation
- draft communications
- closure note phrasing
- executive monthly report summary
- translation from system facts into operational language

### What can be simulated in demo
- some write execution responses
- problem proposal creation
- export generation if clearly labeled
- a subset of ServiceNow-linked closure cascade behavior

---

## 12. Client positioning

## H. Positioning in front of client

### How to explain that this is not a chatbot
Use this framing:

> This is not a conversational layer on top of ServiceNow.  
> It is an operational copilot that transforms ticket data into prioritized action, validated procedure, and auditable execution support.

### How to sell it as operational copilot
Core value pillars:
1. **Decision compression** — less time deciding what to do next
2. **Procedural safety** — the copilot validates before proposing action
3. **Traceable execution** — sensitive actions require approval and leave audit
4. **Operational intelligence** — sees patterns and work concentration
5. **Executive narrative** — turns operations into business-readable reporting

### How to avoid overpromising autonomy
Say explicitly:
- the copilot assists, recommends, drafts, and validates
- the human remains accountable for approved actions
- the system is intentionally designed with approval checkpoints for sensitive changes

### How to defend “approve before write”
Position it as a strength, not a limitation:
- protects operational integrity
- supports governance and compliance
- reduces risky automation
- increases trust and adoption
- makes AI acceptable for enterprise production contexts

---

## 13. APY08-aligned deterministic rule domains

These rules should be extracted into policy services and never delegated to LLM:

- standard request SLA = 3 days
- remote request SLA = 5 days
- critical incident = 2h
- high = 4h
- medium = 8h
- low = 16h
- 3 contact-attempt rule
- conformity auto-close at 15 calendar days

Recommended service split:
- `SlaPolicyService`
- `ContactAttemptPolicyService`
- `ClosurePolicyService`
- `ConformityWindowPolicyService`

---

## 14. MVP final recommendation

## Recommended final MVP demo

If the project had to be presented seriously to a client soon, the MVP demo should be:

### Included
- polished Daily Briefing
- Stalled Ticket Resolution with editable draft and explicit approval
- Assisted Closure with deterministic validation and editable close note
- simplified Pattern Detection over seeded queue
- lightweight Monthly Report for management view
- clear approval/audit story visible in architecture and UI
- curated seed dataset of ~20 realistic tickets

### Excluded for now
- generalized autonomous execution
- embeddings-heavy clustering
- full production export engine
- cross-process workflow automation beyond scoped demo cases

---

## 15. Top 5 documents to create first

1. **Executive One-Pager**
2. **Solution Architecture Document**
3. **Operational Flow Blueprint**
4. **Approval & Audit Governance Note**
5. **Execution Backlog / Delivery Plan**

---

## 16. Top 5 product/architecture decisions to close now

1. **Approval model**
   - exact stages, states, and UI behavior before any write action

2. **Audit persistence scope**
   - in-memory for demo vs local DB-backed persistence now

3. **Seeded dataset strategy**
   - whether the demo relies on live ServiceNow, seed mode, or hybrid mode

4. **Closure scope**
   - exactly which ticket types and validation rules are in-scope for MVP

5. **Pattern detection approach**
   - deterministic-only for MVP or deterministic + optional semantic similarity

---

## 17. Most probable risks if this is shown to a client tomorrow

1. **Looks impressive but not governed**
   - if approval/audit story is not explicit, client may see risk

2. **Too much “chat”, not enough workflow**
   - if flows are not surfaced as operational actions, it may feel like generic AI

3. **Overpromised autonomy**
   - if write behavior is ambiguous, trust will drop immediately

4. **Weak data realism**
   - if tickets are not realistic, value story will feel artificial

5. **ServiceNow dependency fragility**
   - live connectivity issues can undermine confidence unless degraded mode is clearly handled

---

## 18. Immediate implementation recommendation

The next implementation wave should focus on:

1. formalizing the approval/audit model in backend contracts
2. creating execution-ready docs for flows 2–5
3. adding deterministic service skeletons for:
   - stalled ticket
   - closure validation
   - pattern detection
   - monthly report
4. shifting more of the UI from generic conversation into **workflow-assisted panels**
5. building the realistic seed dataset that makes the entire story credible

---

## 19. Suggested next repo artifacts after this document

Recommended follow-up files:
- `docs/ENTERPRISE_DEMO_NARRATIVE.md`
- `docs/ENTERPRISE_BACKLOG.md`
- `docs/APPROVAL_AUDIT_MODEL.md`
- `docs/OPERATIONAL_RULES_APY08.md`
- `docs/CLIENT_EXECUTIVE_ONE_PAGER.md`

This would create a coherent package for product, architecture, delivery, and client conversation.
