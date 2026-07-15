(ns smeltrefine.registry-test
  (:require [clojure.test :refer [deftest is]]
            [smeltrefine.registry :as r]))

;; ----------------------------- equipment-verified? / equipment-registered? / equipment-ready? -----------------------------

(deftest equipment-is-verified-when-flagged
  (is (true? (r/equipment-verified? {:id "e1" :verified? true}))))

(deftest equipment-is-not-verified-when-false-or-missing
  (is (false? (r/equipment-verified? {:id "e1" :verified? false})))
  (is (false? (r/equipment-verified? {:id "e1"}))))

(deftest equipment-is-registered-when-flagged
  (is (true? (r/equipment-registered? {:registered? true}))))

(deftest equipment-is-not-registered-when-false-or-missing
  (is (false? (r/equipment-registered? {:registered? false})))
  (is (false? (r/equipment-registered? {}))))

(deftest equipment-ready-requires-both
  (is (true? (r/equipment-ready? {:verified? true :registered? true})))
  (is (false? (r/equipment-ready? {:verified? true :registered? false})))
  (is (false? (r/equipment-ready? {:verified? false :registered? true})))
  (is (false? (r/equipment-ready? {}))))

;; ----------------------------- batch-verified? / batch-registered? / batch-ready? -----------------------------

(deftest batch-is-verified-when-flagged
  (is (true? (r/batch-verified? {:id "b1" :verified? true}))))

(deftest batch-is-not-verified-when-false-or-missing
  (is (false? (r/batch-verified? {:id "b1" :verified? false})))
  (is (false? (r/batch-verified? {:id "b1"}))))

(deftest batch-is-registered-when-flagged
  (is (true? (r/batch-registered? {:registered? true}))))

(deftest batch-is-not-registered-when-false-or-missing
  (is (false? (r/batch-registered? {:registered? false})))
  (is (false? (r/batch-registered? {}))))

(deftest batch-ready-requires-both
  (is (true? (r/batch-ready? {:verified? true :registered? true})))
  (is (false? (r/batch-ready? {:verified? true :registered? false})))
  (is (false? (r/batch-ready? {:verified? false :registered? true})))
  (is (false? (r/batch-ready? {}))))

;; ----------------------------- shipment-weight-exceeded? -----------------------------

(deftest small-shipment-within-weight-does-not-exceed
  (is (false? (r/shipment-weight-exceeded?
               {:weight-kg 12.5 :shipped-weight-kg 2.5} 5.0))))

(deftest shipment-that-pushes-past-weight-exceeds
  (is (true? (r/shipment-weight-exceeded?
              {:weight-kg 3000.0 :shipped-weight-kg 2800.0} 1000.0))))

(deftest shipment-exactly-at-weight-does-not-exceed
  (is (false? (r/shipment-weight-exceeded?
               {:weight-kg 3000.0 :shipped-weight-kg 2800.0} 200.0))
      "exactly at weight is not over, only strictly beyond"))

(deftest missing-weight-is-not-flagged-exceeded
  (is (false? (r/shipment-weight-exceeded? {} 100.0)))
  (is (false? (r/shipment-weight-exceeded? {:weight-kg 800.0} nil))))

;; ----------------------------- purity-grade-valid? -----------------------------

(deftest known-purity-grades-are-valid
  (doseq [g [:gold-999-fine :gold-9999-fine
             :silver-999-fine :silver-9999-fine
             :copper-cathode-grade-a :copper-cathode-grade-1
             :aluminum-p1020 :aluminum-p0610]]
    (is (r/purity-grade-valid? g))))

(deftest fabricated-purity-grade-is-invalid
  (is (not (r/purity-grade-valid? :unobtainium)))
  (is (not (r/purity-grade-valid? nil))))

;; ----------------------------- impurity-valid? -----------------------------

(deftest typical-impurity-rate-is-valid
  (is (r/impurity-valid? 0.01))
  (is (r/impurity-valid? 0.0))
  (is (r/impurity-valid? 50.0))
  (is (r/impurity-valid? 100.0)))

(deftest negative-impurity-rate-is-invalid
  (is (not (r/impurity-valid? -1.0))))

(deftest excessive-impurity-rate-is-invalid
  (is (not (r/impurity-valid? 999.0)))
  (is (not (r/impurity-valid? 100.01))))

(deftest non-numeric-or-missing-impurity-rate-is-invalid
  (is (not (r/impurity-valid? nil)))
  (is (not (r/impurity-valid? "1.5"))))

;; ----------------------------- register-maintenance -----------------------------

(deftest maintenance-is-a-draft-not-a-real-actuation
  (let [result (r/register-maintenance "mnt-1" "furnace-001" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest maintenance-assigns-maintenance-number
  (let [result (r/register-maintenance "mnt-1" "furnace-001" 7)]
    (is (= (get result "maintenance_number") "MNT-000007"))
    (is (= (get-in result ["record" "maintenance_id"]) "mnt-1"))
    (is (= (get-in result ["record" "equipment_id"]) "furnace-001"))
    (is (= (get-in result ["record" "kind"]) "maintenance-schedule-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest maintenance-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "" "furnace-001" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "furnace-001" -1))))

;; ----------------------------- register-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment "ship-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-shipment "ship-1" 7)]
    (is (= (get result "shipment_number") "SHP-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "ship-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-coordination-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "ship-1" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-maintenance "mnt-1" "furnace-001" 0)
        hist (r/append [] c1)
        c2 (r/register-maintenance "mnt-2" "furnace-001" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "MNT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "MNT-000001" (get-in hist2 [1 "record_id"])))))
