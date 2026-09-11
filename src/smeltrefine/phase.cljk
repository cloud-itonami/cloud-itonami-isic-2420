(ns smeltrefine.phase
  "Phase 0->3 staged rollout for the precious & non-ferrous metals
  smelting-refining plant-operations coordination actor.

    Phase 0  read-only          -- no writes, still governor-gated.
    Phase 1  assisted-intake    -- production-batch logging allowed,
                                    every write needs human approval.
    Phase 2  assisted-coordinate -- adds safety-concern flags and
                                    shipment-coordination proposals,
                                    still approval.
    Phase 3  supervised-auto    -- adds maintenance scheduling (still
                                    always approval -- see below);
                                    governor-clean, high-confidence
                                    `:log-production-batch` (no
                                    physical/financial risk) may auto-
                                    commit.

  `:schedule-maintenance` is deliberately ABSENT from every phase's
  `:auto` set, including phase 3 -- a permanent structural fact, not a
  rollout milestone still to come. Scheduling real maintenance against
  a piece of equipment is one act in this domain with physical
  consequence (a smelting-furnace/refining-furnace/electrolytic-
  refining-cell/converter maintenance window means production downtime,
  and the equipment ends up actually touched); it is always a human
  plant supervisor's call. `:coordinate-shipment` is also ABSENT from
  every phase's `:auto` set -- a refined precious/non-ferrous metal
  shipment carries an unusually high per-kilogram financial value in
  this vertical (gold/silver bullion in particular), a second,
  independent reason (beyond the shared 'coordination, not control'
  discipline) for a human shipping approver's sign-off on every one,
  not only the weight-exceeded case the governor already HARD-blocks.
  `smeltrefine.governor`'s `furnace-actuate-blocked-violations`
  HARD-blocks actuate attempts unconditionally, and the confidence/
  high-stakes gate independently never lets `:flag-safety-concern`
  auto-commit either -- multiple independent layers agree on where
  this actor's authority ends. Like every prior sibling's phase-3
  `:auto` set, this domain has only ONE member
  (`:log-production-batch`) -- no separate no-risk lifecycle distinct
  from ordinary record logging.")

(def write-ops
  #{:log-production-batch :schedule-maintenance
    :flag-safety-concern :coordinate-shipment})

;; NOTE the invariant: `:schedule-maintenance` is a member of
;; `write-ops` (governor-gated like any write) but is NEVER a member of
;; any phase's `:auto` set below. Do not add it there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed
  to auto-commit when governor-clean>}."
  {0 {:label "read-only"           :writes #{}                                            :auto #{}}
   1 {:label "assisted-intake"     :writes #{:log-production-batch}                        :auto #{}}
   2 {:label "assisted-coordinate" :writes #{:log-production-batch :flag-safety-concern
                                             :coordinate-shipment}                          :auto #{}}
   3 {:label "supervised-auto"     :writes write-ops
      :auto #{:log-production-batch}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:schedule-maintenance` and `:coordinate-shipment` are never auto-
    eligible at any phase, so they always escalate once the governor
    clears them (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Precious & Non-Ferrous Metals Smelting-Refining Plant
  Operations Governor verdict to a base disposition before the phase
  gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
