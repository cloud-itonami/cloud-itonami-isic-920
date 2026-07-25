(ns gamblingfacilityops.test
  "Comprehensive test suite: store, governor, operations, phases, scope exclusion edge cases."
  (:require [gamblingfacilityops.store :as store]
            [gamblingfacilityops.governor :as governor]
            [gamblingfacilityops.operation :as op]
            [gamblingfacilityops.phase :as phase]
            [gamblingfacilityops.sim :as sim]))

;; ============================================================================
;; STORE TESTS
;; ============================================================================

(defn test-store-facility-lookup
  "Test that store returns correct facility by ID."
  []
  (let [s (store/make-store)
        fac (store/get-facility s :casino-downtown)]
    (and (= (:id fac) :casino-downtown)
         (= (:name fac) "Downtown Casino & Resort")
         (true? (:registered? fac))
         (true? (:verified? fac)))))

(defn test-store-facility-not-found
  "Test that store returns nil for unknown facility."
  []
  (let [s (store/make-store)
        fac (store/get-facility s :unknown-casino)]
    (nil? fac)))

(defn test-store-facility-verified?
  "Test facility-verified? check."
  []
  (let [s (store/make-store)]
    (and (true? (store/facility-verified? s :casino-downtown))
         (true? (store/facility-verified? s :racebook-metro))
         (false? (store/facility-verified? s :unknown-casino)))))

(defn test-store-list-facilities
  "Test listing all facilities."
  []
  (let [s (store/make-store)
        facilities (store/list-facilities s)]
    (>= (count facilities) 2)))

(defn test-store-referral-resources
  "Test retrieving referral resources."
  []
  (let [s (store/make-store)
        resource (store/get-referral-resource s :problem-gambling-hotline)]
    (and resource
         (= (:id resource) :problem-gambling-hotline)
         (= (:phone resource) "1-800-522-4700"))))

;; ============================================================================
;; GOVERNOR HARD CHECK TESTS
;; ============================================================================

(defn test-governor-check-1-facility-verified
  "Test HARD CHECK 1: Facility unverified."
  []
  (let [s (store/make-store)]
    (and (true? (:pass? (governor/check-facility-verified s :casino-downtown)))
         (false? (:pass? (governor/check-facility-verified s :unknown-casino))))))

(defn test-governor-check-2-effect-propose
  "Test HARD CHECK 2: Effect must be :propose."
  []
  (let [p-propose {:effect :propose}
        p-commit {:effect :commit}
        p-execute {:effect :execute}]
    (and (true? (:pass? (governor/check-effect-propose p-propose)))
         (false? (:pass? (governor/check-effect-propose p-commit)))
         (false? (:pass? (governor/check-effect-propose p-execute))))))

(defn test-governor-check-3-scope-exclusion
  "Test HARD CHECK 3: Scope exclusion for forbidden keywords."
  []
  (let [clean {:op :schedule-facility-maintenance :content "Schedule HVAC"}
        with-odds {:op :schedule-facility-maintenance :content "set odds for next week"}
        with-payout {:op :schedule-supply :content "collect payouts"}
        with-verification {:op :schedule-supply :content "verify patron age"}]
    (and (true? (:pass? (governor/check-scope-exclusion clean)))
         (false? (:pass? (governor/check-scope-exclusion with-odds)))
         (false? (:pass? (governor/check-scope-exclusion with-payout)))
         (false? (:pass? (governor/check-scope-exclusion with-verification))))))

(defn test-governor-check-3-scope-exclusion-qualified
  "Test HARD CHECK 3 qualified carve-out: legitimate safety/referral ops allowed."
  []
  (let [safety-concern {:op :flag-safety-concern :content "Patron showing distress signals"}
        referral-op {:op :coordinate-responsible-gambling-referral
                     :content "Connect patron to gambling support hotline"}]
    (and (true? (:pass? (governor/check-scope-exclusion safety-concern)))
         (true? (:pass? (governor/check-scope-exclusion referral-op))))))

(defn test-governor-full-gate
  "Test full governor gate with all three checks."
  []
  (let [s (store/make-store)
        good-proposal {:op :schedule-facility-maintenance
                       :facility-id :casino-downtown
                       :effect :propose
                       :content "Schedule maintenance"}
        bad-facility {:op :schedule-facility-maintenance
                      :facility-id :unknown-casino
                      :effect :propose
                      :content "Schedule maintenance"}
        bad-effect {:op :schedule-facility-maintenance
                    :facility-id :casino-downtown
                    :effect :execute
                    :content "Schedule maintenance"}
        bad-scope {:op :schedule-facility-maintenance
                   :facility-id :casino-downtown
                   :effect :propose
                   :content "Schedule maintenance AND set odds"}]
    (and (true? (:approved? (governor/govern s good-proposal)))
         (false? (:approved? (governor/govern s bad-facility)))
         (false? (:approved? (governor/govern s bad-effect)))
         (false? (:approved? (governor/govern s bad-scope))))))

;; ============================================================================
;; OPERATION WORKFLOW TESTS
;; ============================================================================

(defn test-operation-workflow
  "Test end-to-end workflow."
  []
  (let [s (store/make-store)
        proposal {:op :schedule-facility-maintenance
                  :facility-id :casino-downtown
                  :effect :propose
                  :content "Schedule HVAC"}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)]
    (and (= (:decision formatted) "approved")
         (= (:state result) :commit))))

(defn test-operation-workflow-rejection
  "Test workflow rejects bad proposals."
  []
  (let [s (store/make-store)
        proposal {:op :schedule-facility-maintenance
                  :facility-id :unknown-casino
                  :effect :propose
                  :content "Schedule"}
        result (op/run-workflow s proposal)]
    (= (:state result) :hold)))

;; ============================================================================
;; PHASE TESTS
;; ============================================================================

(defn test-phase-0-readonly
  "Test Phase 0 (read-only audit): always :hold."
  []
  (let [s (store/make-store)
        proposal {:op :schedule-facility-maintenance
                  :facility-id :casino-downtown
                  :effect :propose
                  :content "Schedule"}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 0 formatted)]
    (= (:action phase-action) :hold)))

(defn test-phase-1-auto-commit
  "Test Phase 1 (auto-commit): approved proposals commit."
  []
  (let [s (store/make-store)
        proposal {:op :schedule-facility-maintenance
                  :facility-id :casino-downtown
                  :effect :propose
                  :content "Schedule"}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 1 formatted)]
    (= (:action phase-action) :commit)))

(defn test-phase-1-escalation
  "Test Phase 1 (auto-commit): safety concerns escalate."
  []
  (let [s (store/make-store)
        proposal {:op :flag-safety-concern
                  :facility-id :casino-downtown
                  :effect :propose
                  :content "Patron distress"}
        result (op/run-workflow s proposal)
        formatted (op/format-result result)
        phase-action (phase/apply-phase 1 formatted)]
    (= (:action phase-action) :escalate)))

;; ============================================================================
;; INTEGRATION TESTS
;; ============================================================================

(defn test-sim-all-scenarios
  "Test all demo scenarios."
  []
  (let [results (sim/run-all-scenarios)]
    (and (= (:total-scenarios results) 5)
         (= (:passed results) 5)
         (every? :pass? (:scenarios results)))))

;; ============================================================================
;; TEST RUNNER
;; ============================================================================

(def all-tests
  [["Store: Facility lookup" test-store-facility-lookup]
   ["Store: Facility not found" test-store-facility-not-found]
   ["Store: Facility verified check" test-store-facility-verified?]
   ["Store: List facilities" test-store-list-facilities]
   ["Store: Referral resources" test-store-referral-resources]
   ["Governor: Check 1 (Facility verified)" test-governor-check-1-facility-verified]
   ["Governor: Check 2 (Effect :propose)" test-governor-check-2-effect-propose]
   ["Governor: Check 3 (Scope exclusion)" test-governor-check-3-scope-exclusion]
   ["Governor: Check 3 (Scope qualified)" test-governor-check-3-scope-exclusion-qualified]
   ["Governor: Full gate" test-governor-full-gate]
   ["Operation: Workflow happy path" test-operation-workflow]
   ["Operation: Workflow rejection" test-operation-workflow-rejection]
   ["Phase: Phase 0 (read-only)" test-phase-0-readonly]
   ["Phase: Phase 1 (auto-commit)" test-phase-1-auto-commit]
   ["Phase: Phase 1 (escalation)" test-phase-1-escalation]
   ["Integration: All demo scenarios" test-sim-all-scenarios]])

(defn run-tests
  "Run all tests and return results."
  []
  (let [results (map (fn [[name test-fn]]
                       [name (try (test-fn) (catch #?(:clj Exception :cljs js/Error) e false))])
                     all-tests)
        passed (count (filter second results))
        total (count results)]
    {:total total
     :passed passed
     :results results
     :summary (if (= passed total) "✓ All tests PASSED" "✗ Some tests FAILED")}))

(defn -main
  "CLI entry point for tests."
  [& args]
  (let [results (run-tests)]
    (println "=== ISIC-920 Test Suite ===")
    (println "")
    (doseq [[name pass?] (:results results)]
      (println (str "[" (if pass? "✓" "✗") "] " name)))
    (println "")
    (println (str "Results: " (:passed results) "/" (:total results) " passed"))
    (println (:summary results))
    ;; Exit non-zero on failure. Without this the entry point printed
    ;; "✗ Some tests FAILED" and still exited 0, so nothing could gate on it.
    (when-not (= (:passed results) (:total results))
      #?(:clj (System/exit 1)
         :cljs (js/process.exit 1)))))
