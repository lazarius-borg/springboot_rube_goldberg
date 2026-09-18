# Platform Functionality & Usability Review Checklist: 016-platform-functionality-fixes

**Purpose**: Reviewer-owned requirements-quality validation checklist testing completeness, clarity, consistency, and edge-case coverage across all platform remediation requirements before task generation and implementation.  
**Created**: 2026-09-17  
**Feature**: [`specs/016-platform-functionality-fixes/spec.md`](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/016-platform-functionality-fixes/spec.md)  
**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact. Mark an item `[x]` only when the reviewer confirms that the requirements-quality criterion is satisfied in the specification.  
**Marker Semantics**: `[x]` indicates that the requirement specification itself is complete, unambiguous, and testable; it does not indicate implementation code completion.  

---

## 1. Requirement Completeness

- [x] CHK001 Are floor zone attributes explicitly required for table creation, table modification, and table listing responses? [Completeness, Spec §FR-001]
- [x] CHK002 Does the specification define fallback behavior when a table combination name is omitted by the manager? [Completeness, Spec §FR-002, Clarification 3]
- [x] CHK003 Are calculation rules for combined seating capacity explicitly specified when client input is absent? [Completeness, Spec §FR-002, Clarification 3]
- [x] CHK004 Are requirements documented for toggling operating schedule days as closed versus standard opening hours? [Completeness, Spec §FR-004, Clarification 5]
- [x] CHK005 Is the behavior of availability searches on closed operating days explicitly defined for both recurring weekdays and specific dates? [Completeness, Spec §FR-005]
- [x] CHK006 Are the specific data fields for table assignments in the manager reservation overview explicitly enumerated? [Completeness, Spec §FR-006]
- [x] CHK007 Are display requirements specified for cancellation reasons across all terminal reservation states? [Completeness, Spec §FR-007]
- [x] CHK008 Are multi-table reservation checkout options explicitly defined for party sizes exceeding single table capacities? [Completeness, Spec §FR-010, Clarification 1]
- [x] CHK009 Does the specification define the minimum and maximum allowable bounds for restaurant dining duration configuration? [Completeness, Spec §FR-011]
- [x] CHK010 Are waiting list matching rules upon reservation cancellations defined in terms of live table inventory availability? [Completeness, Spec §FR-016, Clarification 4]

---

## 2. Requirement Clarity & Unambiguity

- [x] CHK011 Is "floor zone" quantified with specific allowed values or character length limits? [Clarity, Spec §FR-001, Data Model §1.2]
- [x] CHK012 Is the auto-generated combination naming format specified unambiguously (e.g., "Combo: T1 + T2")? [Clarity, Spec §US-1.2, Clarification 3]
- [x] CHK013 Is "total physical table seating capacity" defined as the exact sum of active table capacities rather than an arbitrary limit? [Clarity, Spec §FR-008]
- [x] CHK014 Are the 15-minute dropdown duration increment intervals explicitly bounded by minimum (45 min) and restaurant maximum duration? [Clarity, Spec §FR-012, Clarification 2]
- [x] CHK015 Is the condition for displaying "party size exceeds seating capacity" vs "fully booked" unambiguously differentiated? [Clarity, Spec §US-2.2, Spec §FR-010]
- [x] CHK016 Is the exact representation of party size badges in the customer waiting list specified to match upcoming reservation cards? [Clarity, Spec §FR-015]
- [x] CHK017 Is the FIFO evaluation ordering for waiting list candidate evaluation on cancellation unambiguously stated? [Clarity, Spec §FR-016, Clarification 4]

---

## 3. Requirement Consistency & Immutability

- [x] CHK018 Do table combination capacity requirements align consistently between manager UI form defaults and backend API defaults? [Consistency, Spec §FR-002, Contracts §1.4]
- [x] CHK019 Are the immutability requirements for existing confirmed reservations consistent across opening hours updates, dining duration changes, and establishment policy edits? [Consistency, Spec §FR-014, Spec §US-4.3]
- [x] CHK020 Do customer duration selection bounds consistently respect individual restaurant maximum duration settings? [Consistency, Spec §FR-011, Spec §FR-012]
- [x] CHK021 Is the cancellation reason requirement consistent between customer cancellation actions, manager cancellation actions, and reservation entity models? [Consistency, Spec §FR-007, Data Model §1.5]
- [x] CHK022 Are party size visibility requirements consistent between customer upcoming reservations and active waiting list entries? [Consistency, Spec §FR-015, Spec §US-6.1]

---

## 4. Scenario & Workflow Coverage

- [x] CHK023 Are requirements documented for the complete table lifecycle: creation with zone, editing properties, combining, and safe deletion? [Coverage, Spec §US-1]
- [x] CHK024 Are requirements defined for when a customer books a party size that fits a single table versus requiring multiple distinct tables under one checkout? [Coverage, Spec §US-2, Clarification 1]
- [x] CHK025 Are requirements defined for customer dining duration selection across different times of day (lunch, standard dinner, extended feast)? [Coverage, Spec §US-3]
- [x] CHK026 Are requirements defined for manager establishment configuration updates when active future bookings already exist? [Coverage, Spec §US-4]
- [x] CHK027 Are requirements specified for waiting list queue tracking, real-time offer event dispatch, and 15-minute countdown acceptance? [Coverage, Spec §US-6]

---

## 5. Edge Cases & Concurrency Guardrails

- [x] CHK028 Does the specification define the rejection behavior when a manager attempts to delete a table allocated to an upcoming confirmed reservation? [Edge Case, Spec §Edge Cases, Contracts §1.3]
- [x] CHK029 Are requirements defined for handling race conditions when concurrent booking requests contend for the last available table seats in a timeslot? [Edge Case, Spec §Edge Cases]
- [x] CHK030 Are requirements specified for rejecting direct API booking requests targeting a designated closed day? [Edge Case, Spec §Edge Cases]
- [x] CHK031 Does the specification define what occurs when a constituent table in a table combination is modified or deactivated? [Edge Case, Plan §Project Structure]
- [x] CHK032 Are requirements documented for handling customer requests with party sizes exceeding the restaurant's cumulative total capacity? [Edge Case, Spec §FR-010]
- [x] CHK033 Is the behavior specified when a reservation cancellation frees tables that are insufficient for the first FIFO waiting entry but sufficient for subsequent entries? [Edge Case, Spec §FR-016]

---

## 6. Measurability & Acceptance Criteria Quality

- [x] CHK034 Can the floor zone persistence requirement be objectively validated with 100% test coverage? [Measurability, Spec §SC-001]
- [x] CHK035 Is the 0% overbooking success criterion verifiably testable under simulated concurrent load? [Measurability, Spec §SC-003]
- [x] CHK036 Can the elimination of futile waiting list entries for unseatable party sizes be objectively measured? [Measurability, Spec §SC-004]
- [x] CHK037 Is the requirement for displaying assigned table labels in manager reservation overviews verifiable without ambiguous display states? [Measurability, Spec §SC-005]
- [x] CHK038 Can the retention of original booking parameters on existing reservations following establishment settings edits be verified deterministically? [Measurability, Spec §SC-006]

---

## Notes

- Review ownership: Mark items `[x]` only after reviewer inspection verifies that the requirements are complete, unambiguous, and testable.
- Generated items are left unchecked (`[ ]`) for reviewer evaluation.
- Checklist covers all 12 reported operational concerns across the 4 microservices and both frontends.
