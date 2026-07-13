# Enterprise Help Desk Copilot — Delivery Backlog

## 1. Purpose

This backlog translates the enterprise copilot strategy into an execution-ready structure for the current project. It is designed to move the solution from a conversational AI workspace into a **client-ready Help Desk Copilot** with explicit workflow support, procedural validation, approval-governed write actions, and auditable execution.

This backlog assumes the following product priority:

1. Daily Briefing
2. Stalled Ticket Resolution
3. Assisted Closure
4. Pattern Detection
5. Monthly SLA Report

---

## 2. Delivery principles

### 2.1 Product principles
- Do not build a generic chatbot.
- Optimize for technician productivity, not ticket listing.
- Use deterministic logic for policy, SLA, and approval gates.
- Use LLM for drafting, summarization, and narrative only.
- Any write-back to ServiceNow must follow:
  - proposal
  - review/edit
  - approval
  - execution
  - audit log

### 2.2 Technical principles
- Keep current orchestration assets, but add explicit flow services.
- Prefer composable backend services over monolithic prompt logic.
- Standardize flow traceability with `flow_id`.
- Design for degraded/fallback demo resilience.
- Keep demo mode and live mode explicit.

---

## 3. Release structure

## Release A — Demo Foundation Hardening
Goal:
Stabilize the current workspace and formalize the execution model around approval, traceability, and deterministic flow contracts.

## Release B — Workflow Copilot Experience
Goal:
Deliver flows 2 and 3 as strong operational experiences, not just chat responses.

## Release C — Operational Intelligence
Goal:
Deliver pattern reasoning and monthly reporting to show portfolio-level intelligence.

---

## 4. Epics and features

# Epic 01 — Flow Governance Foundation

## Objective
Establish the common model for all sensitive actions: draft, approval, execution, audit, and traceability.

### Feature 01.01 — Flow execution model
**Description**  
Define a common lifecycle for all write-capable flows.

**Scope**
- flow stages
- status transitions
- UI/backend agreement

**Acceptance criteria**
- A flow has a unique `flow_id`
- A flow can exist in one of these states:
  - `proposed`
  - `edited`
  - `approved`
  - `discarded`
  - `executed`
  - `failed`
- Backend responses include `flow_id` and `currentStage`
- UI can render current flow stage without extra inference

**Dependencies**
- none

---

### Feature 01.02 — Audit log contract
**Description**  
Define the audit structure that will support approval-governed actions.

**Scope**
- DTO shape
- persistence contract
- audit payload conventions

**Acceptance criteria**
- `agent_audit_log` structure is documented
- Audit records include:
  - `flow_id`
  - `ticket`
  - `draft_version`
  - `approved_by`
  - `timestamp`
  - `payload_enviado`
- At least one flow uses this structure end-to-end
- Audit model supports both demo persistence and future DB persistence

**Dependencies**
- Feature 01.01

---

### Feature 01.03 — Draft entity model
**Description**  
Create a reusable draft contract for suggested actions and editable outputs.

**Scope**
- draft DTO
- draft versioning
- editable content support

**Acceptance criteria**
- Draft contains:
  - `draftId`
  - `draftType`
  - `draftVersion`
  - `ticket`
  - `content`
  - `generatedAt`
  - `approvalState`
- Drafts support version increments after edit
- Flow execution references a specific draft version

**Dependencies**
- Feature 01.01

---

### Feature 01.04 — Approval action contract
**Description**  
Standardize approve/discard/edit execution semantics.

**Acceptance criteria**
- Approval APIs are consistent across flows
- Approval requires explicit user identity
- Discard action is traceable
- Execution is blocked unless state is `approved`

**Dependencies**
- Feature 01.02
- Feature 01.03

---

# Epic 02 — Briefing Flow Productionization

## Objective
Turn the current daily briefing into the formal entry point of the enterprise copilot.

### Feature 02.01 — Formal Briefing API contract
**Description**  
Stabilize the response shape for `GET /api/agent/briefing`.

**Acceptance criteria**
- Response includes:
  - `summary`
  - `context.metrics`
  - `attentionToday`
  - `complianceWatch`
  - `patterns`
  - `sourceMode`
- `sourceMode` can be:
  - `remote`
  - `local-fallback`
  - `seed-demo`
- Frontend reads `sourceMode` explicitly

**Dependencies**
- none

---

### Feature 02.02 — Deterministic APY08 rules in briefing
**Description**  
Apply deterministic business rules for risk and compliance.

**Acceptance criteria**
- SLA risk is computed without LLM
- Conformity window 13–15 days is represented
- Days without update are deterministic
- Metrics are explainable from backend logic

**Dependencies**
- Feature 02.01

---

### Feature 02.03 — Briefing quick actions
**Description**  
Convert briefing chips into transitions to other flows.

**Acceptance criteria**
- User can launch:
  - stalled ticket view
  - close-check
  - ticket analysis
- Actions are available from briefing items
- Transition preserves ticket context

**Dependencies**
- Feature 02.01

---

### Feature 02.04 — Seeded briefing scenario support
**Description**  
Allow briefing to operate with seeded realistic data for demo reliability.

**Acceptance criteria**
- Demo can run without real-time ServiceNow dependency
- Seed mode is visually identifiable
- At least 20 realistic incidents can drive the briefing narrative

**Dependencies**
- Feature 02.01

---

# Epic 03 — Stalled Ticket Resolution Flow

## Objective
Help the technician transform an “On Hold” or stale ticket into a concrete next step within seconds.

### Feature 03.01 — StalledTicketService
**Description**  
Create deterministic service to diagnose why a ticket is stalled.

**Acceptance criteria**
- Service retrieves ticket context
- Service computes:
  - days on hold
  - days without update
  - contact attempts
  - recommended next action
- Service output is structured and independent from LLM phrasing

**Dependencies**
- `ServiceNowClient`
- policy services

---

### Feature 03.02 — Contact attempt policy service
**Description**  
Formalize the 3-contact-attempt rule.

**Acceptance criteria**
- Attempts are counted deterministically from notes/history
- Rule output is available as:
  - count
  - threshold reached
  - next allowed operational action
- Logic is testable without LLM

**Dependencies**
- none

---

### Feature 03.03 — Stalled ticket draft generation
**Description**  
Generate a technician-ready follow-up draft from structured diagnostic context.

**Acceptance criteria**
- Draft is editable before approval
- Draft includes recommended tone and next action
- Draft references a ticket and a `flow_id`

**Dependencies**
- Feature 03.01
- Epic 01 governance foundation

---

### Feature 03.04 — Execute follow-up after approval
**Description**  
Write work note or follow-up payload to ServiceNow only after explicit approval.

**Acceptance criteria**
- No execution without approval
- Execution stores audit log
- UI shows:
  - proposed
  - approved
  - executed
- Timeline is visible to the user

**Dependencies**
- Feature 03.03
- Epic 01 governance foundation

---

### Feature 03.05 — Stalled ticket UI panel
**Description**  
Create a dedicated operational panel in the workspace.

**Acceptance criteria**
- Ticket diagnostic is visible without reading raw chat
- Editable draft panel exists
- Buttons:
  - Approve
  - Edit
  - Discard
- Execution result is visible in timeline form

**Dependencies**
- Feature 03.03
- Feature 03.04

---

# Epic 04 — Assisted Closure Flow

## Objective
Ensure the copilot validates procedure before suggesting or executing closure.

### Feature 04.01 — ClosureValidationService
**Description**  
Create deterministic closure eligibility engine.

**Acceptance criteria**
- Supports scoped ticket types:
  - `incident`
  - `sc_task`
- Returns:
  - `eligible`
  - `blockingReasons`
  - `validationChecks`
- No closure recommendation is produced without validation result

**Dependencies**
- policy services
- ServiceNow retrieval

---

### Feature 04.02 — Closure policy rules (APY08 + scoped process rules)
**Description**  
Externalize rules related to closure validation.

**Acceptance criteria**
- Rules cover at least:
  - closure eligibility for incidents
  - basic parent dependency checks for `sc_task`
  - evidence/approval checks for access-related scenarios when available
- Rule output is deterministic and explainable

**Dependencies**
- Feature 04.01

---

### Feature 04.03 — Closure draft note generation
**Description**  
Create editable close note draft only after validation context is available.

**Acceptance criteria**
- Draft note is generated from structured validation context
- Blocking reasons remain visible even if note exists
- User can edit the note before approval

**Dependencies**
- Feature 04.01
- Epic 01 governance foundation

---

### Feature 04.04 — Close execution with optional cascade
**Description**  
Support closure execution after approval, with clear visibility on impact.

**Acceptance criteria**
- Close action requires approval
- Optional parent close is separate and explicit
- All execution payloads are audited
- Failure is visible and recoverable

**Dependencies**
- Feature 04.03
- Epic 01 governance foundation

---

### Feature 04.05 — Closure UI modal/panel
**Description**  
Create a structured closure panel in the frontend.

**Acceptance criteria**
- Validation checks render as `OK` or `BLOCKED`
- Draft note is editable
- Closure buttons are disabled if blocked
- Optional parent close is clearly labeled

**Dependencies**
- Feature 04.03
- Feature 04.04

---

# Epic 05 — Pattern Detection and Batch Reasoning

## Objective
Show that the copilot reasons over the queue, not only over one ticket at a time.

### Feature 05.01 — Deterministic pattern detector
**Description**  
Detect clusters using deterministic grouping logic.

**Acceptance criteria**
- Detects at least:
  - repeated short descriptions
  - repeated category/theme
  - repeated caller/requester
- Clusters are explainable
- Each pattern includes rationale

**Dependencies**
- queue retrieval capabilities

---

### Feature 05.02 — PRB candidate suggestion
**Description**  
Recommend potential problem records without auto-creating them.

**Acceptance criteria**
- Suggests PRB candidate for repeated patterns
- Displays confidence and rationale
- Does not write to ServiceNow automatically

**Dependencies**
- Feature 05.01

---

### Feature 05.03 — Batch action proposals
**Description**  
Suggest grouped operational treatment where useful.

**Acceptance criteria**
- Proposed actions are advisory
- Any write-capable next step still requires approval
- Batch action output includes impacted ticket list

**Dependencies**
- Feature 05.01
- Epic 01 governance foundation

---

### Feature 05.04 — Pattern insight UI
**Description**  
Create a dedicated visual surface for pattern findings.

**Acceptance criteria**
- Findings are easy to scan
- Each finding shows:
  - pattern type
  - affected tickets
  - recommended next step
- No dependence on chat-only rendering

**Dependencies**
- Feature 05.01

---

# Epic 06 — Monthly SLA Reporting

## Objective
Demonstrate that the copilot also supports management visibility and replaces manual reporting work.

### Feature 06.01 — Monthly deterministic aggregation service
**Description**  
Create service to compute monthly operational KPIs.

**Acceptance criteria**
- Computes:
  - volume by category
  - volume by technician
  - SLA compliance
  - out-of-SLA count
  - month-over-month comparison
- KPI calculation is deterministic

**Dependencies**
- analytics/query layer

---

### Feature 06.02 — Executive narrative generator
**Description**  
Use LLM to explain the aggregate result.

**Acceptance criteria**
- LLM input is only structured KPI context
- Generated text is concise and management-friendly
- Output never invents KPIs not present in context

**Dependencies**
- Feature 06.01

---

### Feature 06.03 — Report view
**Description**  
Create a simple but credible report surface.

**Acceptance criteria**
- Includes cards + table
- Shows summary plus key metrics
- Supports comparison with previous month

**Dependencies**
- Feature 06.01
- Feature 06.02

---

### Feature 06.04 — Export path
**Description**  
Support at least one export mechanism for demo.

**Acceptance criteria**
- One format is demonstrable
- If export is simulated, it is clearly scoped
- Export reflects the same deterministic data shown on screen

**Dependencies**
- Feature 06.03

---

# Epic 07 — Demo Data & Scenario Control

## Objective
Guarantee a reliable and credible demo regardless of live ServiceNow conditions.

### Feature 07.01 — Seed dataset design
**Description**  
Prepare ~20 realistic tickets with curated scenarios.

**Acceptance criteria**
- Dataset includes:
  - open tickets
  - stalled tickets
  - closure-ready cases
  - compliance-watch cases
  - repeated-pattern tickets
  - at least one monthly-report-worthy scenario
- Ticket language is realistic for Mesa de Ayuda TI

**Dependencies**
- none

---

### Feature 07.02 — Seed/live/hybrid mode strategy
**Description**  
Make the source mode explicit and controllable.

**Acceptance criteria**
- System can indicate:
  - live
  - seed
  - hybrid
- Demo can continue if ServiceNow fails
- Mode is visible to operator

**Dependencies**
- Feature 07.01

---

### Feature 07.03 — Demo reset/reload capability
**Description**  
Allow quick recovery between demo runs.

**Acceptance criteria**
- Demo state can be reloaded easily
- Seed scenarios can be reset
- Demo operator effort is minimal

**Dependencies**
- Feature 07.01

---

# Epic 08 — Workspace Evolution Beyond Chat

## Objective
Shift the product from chat-centric interaction to workflow-centric assistance.

### Feature 08.01 — Workflow action cards
**Description**  
Add explicit action surfaces for major flows.

**Acceptance criteria**
- Workspace shows action cards for:
  - briefing
  - stalled resolution
  - closure
  - patterns
  - reports
- User can navigate flows without relying only on typed prompts

**Dependencies**
- none

---

### Feature 08.02 — Right panel as operational workspace
**Description**  
Turn the right panel into active workflow context, not just passive insights.

**Acceptance criteria**
- Right panel can show:
  - draft panel
  - validation panel
  - audit summary
  - flow timeline
- Context changes according to current flow

**Dependencies**
- flow-specific UI work

---

### Feature 08.03 — Timeline and traceability UX
**Description**  
Expose the lifecycle of a flow visually.

**Acceptance criteria**
- Timeline shows:
  - generated
  - edited
  - approved
  - executed
- Timeline references `flow_id`
- Sensitive actions visibly show approval stage

**Dependencies**
- Epic 01 governance foundation

---

## 5. Dependencies map

### Foundational dependencies
- Epic 01 must start first
- Epic 02 can continue in parallel
- Epic 03 and Epic 04 depend on Epic 01
- Epic 05 depends on queue data strategy
- Epic 06 depends on aggregation service and demo data
- Epic 07 should start early to support demo reliability
- Epic 08 can evolve iteratively but should begin once governance model is defined

---

## 6. Quick wins

### Top quick wins
1. Add `sourceMode` formally to briefing contract
2. Define common DTOs for:
   - draft
   - approval
   - execution result
   - audit log
3. Introduce `flow_id` in flow-capable responses
4. Create `ClosureValidationService` skeleton
5. Create `StalledTicketService` skeleton
6. Seed ~20 realistic tickets
7. Add explicit workflow action cards in workspace

---

## 7. Technical debt visible now

### Backend debt
- conversational orchestration still carries too much business responsibility
- write-governance model not formalized yet
- deterministic services for flows 2–5 are not separated enough
- no common approval/audit contract yet

### Frontend debt
- workspace is stronger visually than operationally
- approval state UX is not formalized
- chat still dominates interactions
- flow-specific panels are not yet first-class

### Demo debt
- live ServiceNow reliability remains a risk
- curated dataset is not yet formalized
- managerial/reporting layer is still conceptual

---

## 8. Suggested sprint sequencing

## Sprint 1
- Epic 01 governance foundation
- Feature 02.01 briefing contract hardening
- Feature 02.02 APY08 deterministic rules
- Feature 07.01 seed dataset design

## Sprint 2
- Feature 03.01 stalled ticket service
- Feature 03.02 contact attempt policy
- Feature 03.03 stalled draft generation
- Feature 08.01 workflow action cards

## Sprint 3
- Feature 03.04 approved execution
- Feature 03.05 stalled ticket UI
- Feature 04.01 closure validation service
- Feature 04.02 closure policy rules

## Sprint 4
- Feature 04.03 close note draft
- Feature 04.04 close execution
- Feature 04.05 closure UI
- Feature 08.03 timeline UX

## Sprint 5
- Feature 05.01 pattern detector
- Feature 05.02 PRB suggestion
- Feature 05.04 pattern UI

## Sprint 6
- Feature 06.01 monthly aggregation
- Feature 06.02 executive narrative
- Feature 06.03 report view
- Feature 07.02 hybrid mode strategy

---

## 9. Definition of done for demo-ready scope

The demo-ready scope is complete when:

- The workspace opens with a deterministic daily briefing
- At least one stalled ticket can be diagnosed, drafted, approved, and executed with audit trace
- At least one closure scenario can be validated before close proposal
- The system shows at least one pattern across multiple tickets
- A monthly report can be generated from deterministic aggregates
- No write action can occur without explicit approval
- Demo can run with seeded realistic data even if ServiceNow is unavailable
- The client-facing narrative is supported by architecture and documentation

---

## 10. Recommended owners by track

### Product / Strategy
- flow definition
- prioritization
- demo narrative
- client positioning

### Backend / Integration
- deterministic services
- approval model
- audit model
- ServiceNow contracts

### Frontend / UX
- workflow surfaces
- approval/edit/execute interactions
- timeline and traceability
- right-panel operational UX

### AI / Prompting
- narrative templates
- draft quality
- structured prompt boundaries
- output consistency

### Governance / Security
- approve-before-write controls
- logging model
- token/data handling review
- audit completeness

---

## 11. Final recommendation

If only a subset can be built immediately, prioritize this subset:

1. Governance foundation
2. Daily Briefing hardening
3. Stalled Ticket flow end-to-end
4. Assisted Closure validation
5. Seed dataset and workflow-centric UX

This creates the strongest balance between:
- operational value
- trust
- architectural seriousness
- demo credibility
- implementation feasibility
