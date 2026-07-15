# cloud-itonami-isic-2420: Manufacture of basic precious and other non-ferrous metals

Open Business Blueprint for **ISIC Rev.5 2420**: manufacture of basic precious and other non-ferrous metals — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office **precious & non-ferrous metals smelting-refining plant operations**: production-batch data logging (purity-grade/weight/impurity-rate), smelting/refining-equipment maintenance scheduling, safety-concern flagging, and outbound refined-metal shipment coordination.

This repository designs a forkable OSS business for precious &
non-ferrous metals smelting-refining plant operations: run by a
qualified operator so a smelting-refining plant keeps its own
operating records instead of renting a closed SaaS.

## Scope: primary smelting-refining, not mining, casting, or downstream fabrication

ISIC 2420 covers the **PRIMARY production stage** that turns ore,
concentrate, or scrap of gold, silver, copper, or aluminum into
refined metal: a **smelting furnace** (blast furnace, flash-smelting
furnace, or reverberatory furnace) melts the feed and separates metal
from gangue, then a **refining stage** (anode furnace, electrolytic
refining cell, or converter) purifies the smelted metal into
doré/bullion bar (gold/silver), cathode (electrolytically refined
copper), anode (cast intermediate ahead of electrolytic refining), or
ingot (primary aluminum) — ready to sell or to pass on to a downstream
fabrication operation.

This is distinct from three neighboring verticals:

- **Mining/mineral-processing** (e.g. ISIC 0710/0729): upstream of
  this actor. Extracts ore from the ground and concentrates it — this
  actor never extracts ore; it consumes the ore/concentrate/scrap feed
  a mining actor's own output already is.
- **`cloud-itonami-isic-2432`** (Casting of non-ferrous metals):
  downstream of this actor. Takes already-refined/alloyed metal (this
  actor's own output, or purchased ingot) and melts it in a foundry
  furnace to pour into sand/permanent molds or die-cast it into shaped
  parts — this actor never pours a shaped mold or die-casts a part,
  and 2432 never smelts ore or electrolytically refines a metal.
- **Ferrous smelting/refining** (iron and steel): a separate, much
  higher-temperature process family with its own hazard profile — out
  of this actor's own scope (precious and OTHER NON-FERROUS metals
  only, per ISIC 2420's own definition).

This actor's own hazard profile is centered on PRIMARY smelting and
refining: molten-metal splash/burn risk at the smelting furnace and
converter, furnace/converter off-gas exposure (sulfur dioxide,
particulate), toxic heavy-metal-fume exposure (lead/arsenic/cadmium
dust common in complex ore feeds), electrolytic-refining-cell
acid-mist exposure, and environmental release concern (stack
emissions, effluent, tailings/slag) — distinct from a downstream
foundry's mold/core-binder fume and shakeout-dust profile.

## What this actor does

Proposes **plant operations coordination**, not equipment operation:
- `:log-production-batch` — smelting/refining batch, purity-grade (troy-ounce/kg for gold/silver, kg/tonne for copper/aluminum) and output-quality (impurity-rate) data logging (administrative, not an operational decision)
- `:schedule-maintenance` — smelting-furnace/refining-furnace/electrolytic-refining-cell/converter maintenance scheduling proposal
- `:flag-safety-concern` — surface a molten-metal-hazard (splash/burn, furnace/converter off-gas exposure)/toxic-fume-exposure (heavy-metal dust, electrolytic-cell acid mist)/environmental concern (stack emissions, effluent, tailings/slag release) — always escalates
- `:coordinate-shipment` — outbound refined-metal shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-critical domain**
(smelting furnace, molten-metal splash/burn hazard, furnace/converter
off-gas exposure, toxic heavy-metal-fume exposure, electrolytic-cell
acid-mist exposure, environmental release):

- Does NOT control the smelting furnace, refining furnace, electrolytic refining cell, or converter directly
- Does NOT make plant-safety, molten-metal-safety, or environmental-release decisions (that's the plant supervisor's exclusive human authority)
- Does NOT actuate the smelting furnace, refining furnace, electrolytic refining cell, or converter (human plant supervisor decides)
- ONLY proposes/coordinates operations back-office; all actuation requires explicit human approval
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation
- `:schedule-maintenance` and `:coordinate-shipment` are NEVER auto-eligible at any phase — always human sign-off, even when the governor is clean (a refined-metal shipment, especially gold/silver bullion, carries an unusually high per-kilogram value in this vertical)

## Architecture

Classic governed-actor pattern (`smeltrefine.operation/build`, a langgraph-clj StateGraph):
1. **`smeltrefine.advisor`** (sealed intelligence node, `SmeltRefineAdvisor`): proposes decisions only, never commits
2. **`smeltrefine.governor`** (independent, `Precious & Non-Ferrous Metals Smelting-Refining Plant Operations Governor`): validates against domain rules, re-derived from `smeltrefine.registry`'s pure functions and `smeltrefine.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct smelting/refining-furnace-equipment control)
     - Directly actuating the smelting furnace, refining furnace, electrolytic refining cell, or converter (`:actuate-furnace? true`) is a PERMANENT, unconditional block
     - A shipment may not push a batch's own recorded shipped weight past its own logged production weight (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:purity-grade` value on a production-batch patch
     - No physically implausible `:impurity-percent` value on a production-batch patch
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`smeltrefine.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`smeltrefine.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
clojure -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
clojure -M:dev:test

# Run the demo
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
