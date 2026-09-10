(ns gamblingfacilityops.phase
  "Rollout phases for isic-920 actor.

   Phase 0: Read-only audit — proposals evaluated, no commits
   Phase 1: Auto-commit with escalation — safe proposals auto-commit, safety concerns escalate")

(defn phase-config
  "Return phase 0 or 1 configuration."
  [phase-num]
  (case phase-num
    0 {:name "Read-only Audit"
       :auto-commit? false
       :escalate-safety? true
       :description "Proposals evaluated, no commits (audit mode)"}
    1 {:name "Auto-commit with Escalation"
       :auto-commit? true
       :escalate-safety? true
       :description "Safe proposals auto-commit; safety concerns escalate"}
    {:error "Unknown phase"}))

(defn apply-phase
  "Apply phase logic to a workflow result.
   Returns {:action :commit | :hold | :escalate :reason ...}"
  [phase-num result]
  (let [config (phase-config phase-num)
        decision (:decision result)
        op (:op result)]

    (cond
      ;; Phase 0: Always read-only (audit mode)
      (= phase-num 0)
      {:action :hold
       :reason "Phase 0 (read-only audit): no commits"}

      ;; Phase 1: Auto-commit if approved, escalate if safety
      (= phase-num 1)
      (cond
        (= decision "safety-escalation")
        {:action :escalate
         :reason "Safety concern escalated to human"}

        (= decision "approved")
        {:action :commit
         :reason "Governance passed; auto-committed in Phase 1"}

        :else
        {:action :hold
         :reason "Governance failed; held for review"})

      :else
      {:action :hold
       :reason "Unknown phase"})))
