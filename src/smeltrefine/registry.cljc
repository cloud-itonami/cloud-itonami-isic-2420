(ns smeltrefine.registry
  "Pure-function domain logic for the precious & non-ferrous metals
  smelting-refining plant-operations coordination actor --
  equipment/batch verification, shipment-weight recompute, purity-
  grade validation, impurity-rate plausibility validation, and draft
  maintenance-schedule/shipment-coordination record construction.

  Per docs/adr/0001-architecture.md Decision 1: this vertical has NO
  pre-existing `kotoba-lang/smeltrefine`-style capability library to
  wrap (verified: `gh api search/repositories -f q=org:kotoba-lang+smelt`
  and `+refine` both return zero results). The domain logic therefore
  lives here as pure functions, re-verified INDEPENDENTLY by
  `smeltrefine.governor` -- the same 'ground truth, not self-report'
  discipline every sibling actor's own registry establishes (most
  directly `cloud-itonami-isic-2432`'s `nonferrousmfg.registry`, the
  closest architectural sibling): never trust a proposal's own
  self-reported weight/status when the inputs needed to recompute it
  independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant-operations system. It builds the DRAFT record
  a plant coordinator would keep (a scheduled maintenance window, a
  coordinated shipment), not the act of actuating a smelting furnace,
  refining furnace, electrolytic refining cell, or converter, or
  dispatching a real freight carrier (this actor NEVER does either --
  see README `What this actor does NOT do`).

  SCOPE NOTE: ISIC 2420 (this actor) covers PRIMARY smelting and
  refining of precious and other non-ferrous metals -- ore/concentrate/
  scrap intake -> smelting furnace (blast furnace / flash-smelting
  furnace / reverberatory furnace) melts and separates metal from
  gangue -> refining stage (anode furnace / electrolytic refining cell
  / converter) purifies the smelted metal -> doré bar / bullion bar /
  cathode / anode / ingot output. This is the PRIMARY PRODUCTION stage
  that turns ore/concentrate/scrap into refined metal, distinct from
  `cloud-itonami-isic-2432` (Casting of non-ferrous metals), the
  DOWNSTREAM foundry vertical that takes already-refined/alloyed metal
  (this actor's own output, or purchased ingot) and melts+pours or
  die-casts it into shaped parts -- 2420 never pours a shaped mold or
  die-casts a part, and 2432 never smelts ore or electrolytically
  refines a metal. It is also distinct from mining/mineral-processing
  ISIC classes (e.g. 0710/0729) that produce the ore/concentrate feed
  this actor's own smelting furnace consumes -- 2420 never extracts
  ore from the ground. This actor's own hazard profile centers on
  PRIMARY smelting/refining: molten-metal splash/burn at the smelting
  furnace and converter, furnace/converter off-gas exposure (sulfur
  dioxide, particulate), toxic heavy-metal-fume exposure (lead/
  arsenic/cadmium dust common in complex ore feeds), electrolytic-
  refining-cell acid-mist exposure, and environmental release concern
  (stack emissions, effluent, tailings/slag) -- distinct from a
  downstream foundry's mold/core-binder fume and shakeout-dust
  profile.")

;; ----------------------------- constants -----------------------------

(def valid-purity-grades
  "The closed set of purity-grade values a production-batch (smelted/
  refined doré, bullion, cathode, anode, or ingot lot) record may
  declare -- the standard precious/non-ferrous smelting-refining grade
  families. Anything else is a fabricated/unrecognized purity grade --
  the governor HARD-holds rather than let an invented grade pass
  through."
  #{;; gold (troy-ounce convention -- fineness expressed per mille)
    :gold-999-fine :gold-9999-fine
    ;; silver (troy-ounce convention)
    :silver-999-fine :silver-9999-fine
    ;; copper (kg/tonne convention -- LME cathode grades)
    :copper-cathode-grade-a :copper-cathode-grade-1
    ;; aluminum (kg/tonne convention -- primary ingot designations)
    :aluminum-p1020 :aluminum-p0610})

(def valid-output-forms
  "The closed set of PRIMARY-PRODUCTION output shapes this smelting-
  refining plant's own output may take -- doré bar (unrefined
  gold/silver alloy from smelting, pre-refining), bullion bar (refined
  gold/silver, post-refining), cathode (electrolytically refined
  copper sheet), anode (cast intermediate between smelting and
  electrolytic refining), or ingot (cast primary aluminum). A
  smelting-refining plant never ships a shaped/machined part or a
  die-cast component (that is `cloud-itonami-isic-2432`'s own
  downstream scope, not this actor's) and never ships raw ore or
  concentrate (that is a mining/mineral-processing actor's own
  upstream scope, not this actor's)."
  #{:dore-bar :bullion-bar :cathode :anode :ingot})

(def impurity-min-percent
  "Physical floor for a batch's own impurity/output-quality reading
  (zero impurity is the best possible outcome -- fully pure metal --
  never negative)."
  0.0)

(def impurity-max-percent
  "Physical ceiling for a batch's own impurity reading -- a batch
  cannot be more than 100% impure (i.e. contain zero of the declared
  metal). A reading above this is implausible assay/sensor data, not a
  real batch."
  100.0)

;; ----------------------------- equipment checks -----------------------------

(defn equipment-verified?
  "Ground-truth check: has `equipment`'s own record been marked
  verified (i.e. it has actually been inspected/commissioned and
  registered in the SSoT, not merely referenced from an unverified
  maintenance request)? A pure predicate over the equipment's own
  permanent field -- no proposal inspection needed."
  [equipment]
  (true? (:verified? equipment)))

(defn equipment-registered?
  "Ground-truth check: does `equipment`'s own record carry a
  `:registered?` true flag (i.e. it is on file in the plant's
  equipment registry)? Scheduling maintenance against equipment that
  is not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [equipment]
  (true? (:registered? equipment)))

(defn equipment-ready?
  "Combined ground-truth gate: the equipment must be both `verified?`
  AND `registered?` before ANY maintenance may be scheduled against
  it. Two independent facts on the equipment's own permanent record,
  neither inferred from the advisor's own rationale."
  [equipment]
  (and (equipment-verified? equipment) (equipment-registered? equipment)))

;; ----------------------------- batch checks -----------------------------

(defn batch-verified?
  "Ground-truth check: has `batch`'s own record been marked verified
  (i.e. its purity-grade/weight/impurity claims have actually been
  assayed, not merely logged from an unverified intake patch)?"
  [batch]
  (true? (:verified? batch)))

(defn batch-registered?
  "Ground-truth check: is `batch`'s own record on file in the plant's
  production ledger? Coordinating a shipment against a batch that is
  not on file and registered is the exact scope violation this actor's
  HARD invariant ('plant/batch record must be independently verified/
  registered before any action') exists to block."
  [batch]
  (true? (:registered? batch)))

(defn batch-ready?
  "Combined ground-truth gate: the batch must be both `verified?` AND
  `registered?` before ANY shipment may be coordinated against it."
  [batch]
  (and (batch-verified? batch) (batch-registered? batch)))

(defn shipment-weight-exceeded?
  "Ground-truth check for a `:coordinate-shipment` proposal:
  would `shipped-to-date-kg` + `new-weight-kg` exceed `batch`'s own
  recorded `:weight-kg` (the batch's own logged production weight)?
  Needs no proposal inspection or stored-verdict lookup -- its inputs
  are permanent fields already on the batch's own record, the same
  shape every sibling actor's own cost/total-matching check uses."
  [batch new-weight-kg]
  (let [capacity (:weight-kg batch)
        so-far (:shipped-weight-kg batch 0.0)]
    (and (number? capacity)
         (number? new-weight-kg)
         (> (+ (double so-far) (double new-weight-kg)) (double capacity)))))

(defn purity-grade-valid?
  "Is `purity-grade` one of the closed, known purity-grade values
  (gold, silver, copper, or aluminum precious/non-ferrous smelting-
  refining grade family)? nil/blank is treated as invalid (a
  production-batch patch must declare a real purity grade, not omit it
  silently)."
  [purity-grade]
  (contains? valid-purity-grades purity-grade))

(defn impurity-valid?
  "Is `percent` a physically plausible batch impurity/output-quality
  reading? Rejects nil, non-numbers, negative values, and values
  beyond `impurity-max-percent` -- a fabricated or assay-error
  reading, never let through as a real batch fact."
  [percent]
  (and (number? percent)
       (>= (double percent) impurity-min-percent)
       (<= (double percent) impurity-max-percent)))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human plant supervisor's/shipping approver's act, not this
  actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-maintenance
  "Validate + construct the MAINTENANCE-SCHEDULE DRAFT -- a proposed
  smelting-furnace/refining-furnace/electrolytic-refining-cell/
  converter maintenance window against a verified, registered piece of
  equipment. Pure function -- does not actuate the furnace, refining
  cell, or converter or execute any maintenance; it builds the RECORD
  a plant coordinator would keep. `smeltrefine.governor` independently
  re-verifies the equipment's own verified/registered ground truth,
  and permanently blocks any attempt to directly actuate the smelting/
  refining equipment (see README `Actuation`), before this is ever
  allowed to commit."
  [maintenance-id equipment-id sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance: equipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record {"record_id" maintenance-number
                "kind" "maintenance-schedule-draft"
                "maintenance_id" maintenance-id
                "equipment_id" equipment-id
                "immutable" true}]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn register-shipment
  "Validate + construct the SHIPMENT-COORDINATION DRAFT -- a proposed
  outbound refined-metal shipment against a verified, registered
  production batch. Pure function -- does not dispatch any real
  freight carrier; it builds the RECORD a plant coordinator would
  keep. `smeltrefine.governor` independently re-verifies the
  shipment's own claimed weight against `shipment-weight-exceeded?`,
  before this is ever allowed to commit."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
