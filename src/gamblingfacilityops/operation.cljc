(ns gamblingfacilityops.operation
  "StateGraph operation workflow: intake → advise → govern → decide → commit | hold | escalate
   DEMO uses synchronous state machine.
   PRODUCTION: Use langgraph-clj StateGraph with async node definitions.

   State: {:proposal ... :advisor-result ... :governor-result ... :decision ...}
   Transitions:
   - intake → advise (advisor generates score/reasoning)
   - advise → govern (governor applies hard checks)
   - govern → decide (route to commit, hold, or escalate based on governor result)
   - decide → commit | hold | escalate (final state)"
  (:require [gamblingfacilityops.advisor :as advisor]
            [gamblingfacilityops.governor :as governor]))

;; State machine definitions
(def state-machine-spec
  "StateGraph node/edge definitions (demo spec for documentation).
   PRODUCTION: Use langgraph-clj StateGraph."
  {:nodes
   [{:id :intake
     :type "START"
     :description "Receive proposal"}
    {:id :advise
     :type "NODE"
     :description "Generate advisor score/reasoning"}
    {:id :govern
     :type "NODE"
     :description "Apply three HARD governor checks"}
    {:id :decide
     :type "NODE"
     :description "Route to commit, hold, or escalate"}
    {:id :commit
     :type "END"
     :description "Proposal auto-commits"}
    {:id :hold
     :type "END"
     :description "Proposal held for human review"}
    {:id :escalate
     :type "END"
     :description "Proposal escalated to human (safety/concern)"}]

   :edges
   [{:from :intake :to :advise}
    {:from :advise :to :govern}
    {:from :govern :to :decide}
    {:from :decide :to :commit :condition "governance passes"}
    {:from :decide :to :hold :condition "governance fails, not escalation-worthy"}
    {:from :decide :to :escalate :condition "safety concern flagged"}]})

(defn intake
  "Node 1: Receive and validate proposal structure."
  [proposal]
  {:state :advise
   :proposal proposal
   :stage "intake"})

(defn advise
  "Node 2: Generate advisor score and reasoning."
  [state store]
  (let [proposal (:proposal state)
        advisor-result (advisor/advisability (:op proposal)
                                            (:facility-id proposal)
                                            (:content proposal))]
    (assoc state
           :state :govern
           :advisor-result advisor-result
           :stage "advise")))

(defn govern
  "Node 3: Apply three HARD checks."
  [state store]
  (let [proposal (:proposal state)
        governor-result (governor/govern store proposal)]
    (assoc state
           :state :decide
           :governor-result governor-result
           :stage "govern")))

(defn decide
  "Node 4: Route to commit, hold, or escalate."
  [state]
  (let [proposal (:proposal state)
        op (:op proposal)
        governor-approved? (get-in state [:governor-result :approved?])
        is-safety-escalation? (= op :flag-safety-concern)]

    (cond
      is-safety-escalation?
      (assoc state :state :escalate :decision "safety-escalation")

      governor-approved?
      (assoc state :state :commit :decision "approved")

      :else
      (assoc state :state :hold :decision "rejected"))))

(defn run-workflow
  "Execute the complete workflow: intake → advise → govern → decide → final state"
  [store proposal]
  (let [s1 (intake proposal)
        s2 (advise s1 store)
        s3 (govern s2 store)
        s4 (decide s3)]
    s4))

(defn format-result
  "Format workflow result for output."
  [final-state]
  {:op (get-in final-state [:proposal :op])
   :facility-id (get-in final-state [:proposal :facility-id])
   :decision (:decision final-state)
   :state (:state final-state)
   :advisor-score (get-in final-state [:advisor-result :score])
   :advisor-reasoning (get-in final-state [:advisor-result :reasoning])
   :governor-approved? (get-in final-state [:governor-result :approved?])
   :governance-failures
   (when-not (get-in final-state [:governor-result :approved?])
     (keep (fn [check]
             (when-not (:pass? check)
               {:reason (:reason check)}))
           (get-in final-state [:governor-result :checks])))})
