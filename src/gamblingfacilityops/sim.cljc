(ns gamblingfacilityops.sim
  "Demo simulation driver: 5 scenarios covering happy path, hard checks, scope violations, escalation."
  (:require [gamblingfacilityops.store :as store]
            [gamblingfacilityops.operation :as op]
            [gamblingfacilityops.phase :as phase]))

(defn scenario-1-maintenance
  "Scenario 1: Happy Path — Schedule facility maintenance."
  [s]
  (let [proposal {:op :schedule-facility-maintenance
                  :facility-id :casino-downtown
                  :content "Schedule HVAC maintenance for next Tuesday"
                  :effect :propose}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 1 formatted)]
    {:scenario "1-maintenance"
     :result formatted
     :phase-action phase-action
     :expected "Approved (happy path)"
     :pass? (= (:action phase-action) :commit)}))

(defn scenario-2-supply
  "Scenario 2: Happy Path — Coordinate supply request."
  [s]
  (let [proposal {:op :coordinate-supply-request
                  :facility-id :racebook-metro
                  :content "Order F&B supplies and office paper"
                  :effect :propose}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 1 formatted)]
    {:scenario "2-supply"
     :result formatted
     :phase-action phase-action
     :expected "Approved (happy path)"
     :pass? (= (:action phase-action) :commit)}))

(defn scenario-3-scope-violation-odds
  "Scenario 3: Hard Check — Scope exclusion: attempt to set odds."
  [s]
  (let [proposal {:op :schedule-facility-maintenance
                  :facility-id :casino-downtown
                  :content "Schedule maintenance AND set odds for next week's sports betting"
                  :effect :propose}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 1 formatted)]
    {:scenario "3-scope-violation-odds"
     :result formatted
     :phase-action phase-action
     :expected "Rejected (scope violation: 'odds')"
     :pass? (= (:action phase-action) :hold)}))

(defn scenario-4-scope-violation-verification
  "Scenario 4: Hard Check — Scope exclusion: attempt age verification."
  [s]
  (let [proposal {:op :coordinate-supply-request
                  :facility-id :racebook-metro
                  :content "Order supplies AND verify patron age 21+"
                  :effect :propose}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 1 formatted)]
    {:scenario "4-scope-violation-verification"
     :result formatted
     :phase-action phase-action
     :expected "Rejected (scope violation: 'age-verify')"
     :pass? (= (:action phase-action) :hold)}))

(defn scenario-5-safety-escalation
  "Scenario 5: Escalation — Flag problem-gambling risk signal."
  [s]
  (let [proposal {:op :flag-safety-concern
                  :facility-id :casino-downtown
                  :content "Patron at Table 5 displaying distress signals; suggest support resources"
                  :effect :propose}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 1 formatted)]
    {:scenario "5-safety-escalation"
     :result formatted
     :phase-action phase-action
     :expected "Escalated (safety concern)"
     :pass? (= (:action phase-action) :escalate)}))

(defn run-all-scenarios
  "Run all 5 demo scenarios and return results."
  []
  (let [store (store/make-store)
        scenarios [scenario-1-maintenance
                  scenario-2-supply
                  scenario-3-scope-violation-odds
                  scenario-4-scope-violation-verification
                  scenario-5-safety-escalation]
        results (map #(% store) scenarios)
        all-pass? (every? :pass? results)]

    {:total-scenarios (count results)
     :passed (count (filter :pass? results))
     :scenarios results
     :summary (if all-pass? "✓ All scenarios PASSED" "✗ Some scenarios FAILED")}))

(defn -main
  "CLI entry point for demo."
  [& args]
  (let [results (run-all-scenarios)]
    (println "=== ISIC-920 Gambling Facility Operations Demo ===")
    (println "")
    (doseq [{:keys [scenario result expected pass?]} (:scenarios results)]
      (println (str "[" (if pass? "✓" "✗") "] " scenario))
      (println (str "  Expected: " expected))
      (println (str "  Decision: " (:decision result)))
      (println (str "  Action: " (get (phase/apply-phase 1 result) :action)))
      (println ""))
    (println (str "Results: " (:passed results) "/" (:total-scenarios results) " passed"))
    (println (:summary results))))
