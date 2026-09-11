# ADR-0001: SmeltRefineAdvisor ⊣ Precious & Non-Ferrous Metals Smelting-Refining Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-2420` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-2420` publishes an OSS blueprint for precious &
non-ferrous metals smelting-refining plant operations coordination
(production-batch purity-grade/weight/impurity-rate data logging,
smelting/refining-furnace-equipment maintenance scheduling,
safety-concern flagging, and outbound refined-metal shipment
coordination). Like every actor in this fleet, the blueprint alone is
not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the same
langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established across the cloud-itonami fleet.

The closest architectural analog is `cloud-itonami-isic-2432` (Casting
of non-ferrous metals): both are back-office coordination actors for a
fixed processing PLANT with heavy manufacturing equipment and a real
physical safety dimension, and both share the same four-op shape
(`:log-production-batch`/`:schedule-maintenance`/`:flag-safety-
concern`/`:coordinate-shipment`) and the same two-entity verified/
registered gate structure (equipment for maintenance scheduling, batch
for shipment coordination). The two verticals are, however, distinct
STAGES of the same non-ferrous-metals value chain, not the same plant:
2420 is the PRIMARY production stage -- a smelting furnace (blast /
flash-smelting / reverberatory) melts ore/concentrate/scrap and
separates metal from gangue, then a refining stage (anode furnace /
electrolytic refining cell / converter) purifies it into doré bar /
bullion bar / cathode / anode / ingot -- while 2432 is a DOWNSTREAM
foundry that takes already-refined/alloyed metal (2420's own output,
or purchased ingot) and melts+pours or die-casts it into shaped parts.
2420 never pours a shaped mold or die-casts a part; 2432 never smelts
ore or electrolytically refines a metal. 2420 is also distinct from
mining/mineral-processing ISIC classes (0710/0729) that produce the
ore/concentrate feed this actor's own smelting furnace consumes --
2420 never extracts ore from the ground. This build mirrors 2432's
architecture closely but adapts the hazard profile, equipment
vocabulary, and record shape to the PRIMARY smelting-refining plant:
2420's permanent equipment-actuation block guards a smelting furnace,
refining furnace, electrolytic refining cell, **or converter**
(`:actuate-furnace?`, kept as the same field name as 2432 to preserve
the governor-rule shape, documented to cover all four); 2420's
production-batch record declares a `:purity-grade` spanning gold/
silver/copper/aluminum smelting-refining grade families (per ISIC
2420's own combined precious/non-ferrous scope) rather than 2432's
cast-alloy families; 2420's `valid-output-forms` set is `:dore-bar`/
`:bullion-bar`/`:cathode`/`:anode`/`:ingot` (PRIMARY-PRODUCTION output
shapes) rather than 2432's cast-part shapes (`:sand-cast`/`:die-cast`/
etc.); and 2420's `:impurity-percent` field (the batch's own residual-
impurity reading, target near zero) replaces 2432's `:defect-rate-
percent` (a cast part's own reject-rate reading) -- both are the same
"physically plausible 0-100% reading" shape, applied to what each
vertical's own production-batch record actually measures.

This vertical has NO pre-existing `kotoba-lang/smeltrefine`-style
capability library to wrap (verified: `gh api "search/repositories?q=
org:kotoba-lang+smelt"` and the same query for `+refine` both return
zero results). This build therefore uses self-contained domain logic
-- pure functions in `smeltrefine.registry` (equipment/batch
verification, shipment-weight recompute, purity-grade validation,
impurity-rate plausibility validation) are re-verified independently
by the governor, the same "ground truth, not self-report" discipline
established across prior actors (most directly
`cloud-itonami-isic-2432`'s `nonferrousmfg.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:precious-nonferrous-smelting-refining-plant-operations-governor`, is
grep-verified UNIQUE fleet-wide (`gh api "search/code?q=precious-
nonferrous-smelting-refining-plant-operations-governor"` returns
`"total_count":0`, zero hits before this repo was created).

## Decision

### Decision 1: Self-contained domain logic (no external smelting-refining capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
precious & non-ferrous metals smelting-refining vertical has NO
pre-existing capability library to wrap. The equipment/batch-
verification / shipment-weight / purity-grade / impurity-rate
validation functions live as pure functions in `smeltrefine.registry`
and are re-verified independently by `smeltrefine.governor` -- the
same "ground truth, not self-report" discipline established across
prior actors (most directly `cloud-itonami-isic-2432`'s
`nonferrousmfg.registry`).

### Decision 2: Coordination, not control — scope boundary at the back-office

This actor is **strictly back-office coordination** of precious &
non-ferrous metals smelting-refining plant operations. It does NOT:
- Control the smelting furnace, refining furnace, electrolytic refining cell, or converter directly
- Make plant-safety, molten-metal-safety, or environmental-release decisions (exclusive to the human plant supervisor)
- Actuate the smelting furnace, refining furnace, electrolytic refining cell, or converter

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human plant-supervisor
approval. This is not a replacement for the supervisor's authority --
it is a proposal-screening and documentation layer.

**CRITICAL SAFETY BOUNDARY**: precious & non-ferrous metals smelting-
refining is a safety-critical domain (molten-metal splash/burn risk,
furnace/converter off-gas exposure, toxic heavy-metal-fume exposure,
electrolytic-refining-cell acid-mist exposure, environmental release,
heavy material handling). Safety-concern flagging NEVER auto-commits.
All safety concerns escalate immediately to human review.

### Decision 3: Safety-concern escalation — always human sign-off

`:flag-safety-concern` (molten-metal-hazard concern, splash/burn risk,
furnace/converter off-gas exposure, toxic heavy-metal-fume exposure,
electrolytic-refining-cell acid-mist exposure, environmental release
concern, equipment-safety concern, crew fatigue) ALWAYS escalates,
never auto-commits. This is not a "low-stakes proposal" -- it is a
circuit-breaker that must reach human authority.

### Decision 4: Two independent verified/registered gates (equipment AND batch), not one

Like `cloud-itonami-isic-2432`, this vertical has TWO entity kinds
each gating a different op: `:schedule-maintenance` independently
verifies the referenced **equipment** unit's own `:verified?`/
`:registered?` fields; `:coordinate-shipment` independently verifies
the referenced **batch**'s own `:verified?`/`:registered?` fields.
Both are the same "plant/batch record must be independently verified/
registered before any action" HARD invariant applied to the two
distinct record kinds this domain actually has.
`:coordinate-shipment` additionally independently recomputes whether a
batch's own recorded shipped-to-date weight plus the proposal's own
claimed weight would exceed the batch's own recorded production
weight -- never taken on the advisor's self-report.

### Decision 5: `:coordinate-shipment` is never auto-eligible, at any phase

Unlike a "no physical/financial risk" op, coordinating a refined
precious/non-ferrous metal shipment carries an unusually high
per-kilogram financial value in this vertical (gold/silver bullion in
particular). `smeltrefine.phase`'s phase-3 `:auto` set has exactly one
member (`:log-production-batch`) -- `:coordinate-shipment` always
escalates to a human shipping approver even when clean and within
weight, a second, independent reason (beyond the shared "coordination,
not control" discipline every sibling actor's `:schedule-maintenance`
and `:flag-safety-concern` already enforce) specific to this
vertical's own high per-unit value.

### Decision 6: HARD invariants (no override)

Four HARD governor invariants (elaborated into ten concrete checks in
`smeltrefine.governor`, mirroring `cloud-itonami-isic-2432`'s own
elaboration of its HARD invariants into concrete checks) block
proposals and cannot be overridden by human approval:
1. Plant/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's weight must independently recompute within the batch's own logged production weight
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct smelting-furnace/refining-furnace/electrolytic-refining-cell/converter control or furnace actuation is permanently blocked
4. The op allowlist is closed -- `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Precious & non-ferrous metals smelting-refining plant operations
back-office now has a documented, governed, auditable coordination
layer that funnels all decisions through independent validation before
human approval.

(+) The "coordination, not control" boundary is explicit in code: all
`:effect :propose`, all real-world actuation requires human plant-
supervisor sign-off.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into ten concrete governor checks) protect against scope creep into
unauthorized equipment operation or furnace/refining-equipment
actuation. Safety concerns are a circuit-breaker, not a threshold.

(+) Safety-critical discipline is explicit: safety-concern flagging
cannot be rate-limited, suppressed, or auto-decided by phase gate.
Human review is mandatory.

(-) Still a simulation/proposal layer, not a real plant-operations
control system. Equipment actuation and smelting/refining-furnace
operation remain human-controlled via external channels.

(-) No integration with real plant-management databases (equipment
telemetry, batch tracking, freight dispatch, assay-lab systems) --
this is a standalone coordinator blueprint.

## Verification

- `cloud-itonami-isic-2420`: `kbb -M:test` green (all tests pass;
  see the superproject ADR and `kotoba-lang/industry` registry entry
  for the exact `Ran N tests containing M assertions, 0 failures, 0
  errors` output, verified from an independent fresh clone), `clojure
  -M:lint` clean, `kbb -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-weight-exceeded, furnace-actuate-
  blocked, already-scheduled, invalid-purity-grade, invalid-impurity-
  rate).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) -- no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `kbb -M:test` resolves offline inside the monorepo checkout.
