(ns gamblingfacilityops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: before this namespace there
  was no demo page and no generator at all (`docs/` held only `index.html`, the
  product face, and `operator-quickstart.md`).

  Everything on the generated page is produced by RUNNING this repo's real
  actor -- `gamblingfacilityops.operation/run-workflow`
  (intake -> advise -> govern -> decide) over a real
  `gamblingfacilityops.store/make-store`, then `gamblingfacilityops.phase/
  apply-phase`. No row is hand-typed, no id is invented: every `:facility-id`
  used by a scenario is asserted at build time to be a key of one of the three
  seed registers (`:facilities`, `:referral-resources`, `:staff`), and the
  proposal text is assembled from the store's own facility/resource/staff names
  rather than retyped. There is no langgraph dependency in this repo -- the
  workflow IS the synchronous state machine in `operation`, so that is what is
  driven, as `gamblingfacilityops.sim` and the test suite do.

  Deterministic: the workflow carries no timestamp (`advisor/propose`, the only
  function in this repo that calls `System/currentTimeMillis`, is deliberately
  NOT on this path -- `operation/advise` calls `advisor/advisability` directly),
  and nothing here reads a clock, a random source or the environment. Two runs
  against the same seed are byte-identical.

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [jp-go-dds.skin]
            [gamblingfacilityops.store :as store]
            [gamblingfacilityops.operation :as op]
            [gamblingfacilityops.phase :as phase]))

;; ---------------------------------------------------------------------------
;; store readers
;;
;; `store` exposes `get-facility` / `get-referral-resource` / `list-facilities`
;; / `list-referral-resources` but has NO staff accessor, so the staff register
;; is read off the atom directly. It is still the real seeded store value.
;; ---------------------------------------------------------------------------

(defn- facility-name [s id] (:name (store/get-facility s id)))
(defn- resource-name [s id] (:name (store/get-referral-resource s id)))
(defn- staff-register [s] (:staff @s))
(defn- staff-member [s id] (get (staff-register s) id))

(defn- seed-ids
  "Every id the seeded store knows about, across all three registers. A
  scenario may only address one of these -- see `assert-traceable!`."
  [s]
  (let [db @s]
    (into #{} (mapcat keys) [(:facilities db) (:referral-resources db) (:staff db)])))

;; ---------------------------------------------------------------------------
;; scenarios
;;
;; Built from the store so that names/ids on the page are traceable by
;; construction. `:expect` is the phase-1 action this scenario must actually
;; reach; `-main` refuses to write the page if any of them diverges, which is
;; what stops a governor/advisor keyword-list change from silently turning a
;; refusal into an approval behind this console.
;; ---------------------------------------------------------------------------

(defn- scenarios [s]
  (let [downtown  (facility-name s :casino-downtown)
        racebook  (facility-name s :racebook-metro)
        alice     (staff-member s :alice-shift-mgr)
        bob       (staff-member s :bob-housekeeping)
        helpline  (resource-name s :problem-gambling-hotline)
        selfexcl  (resource-name s :self-exclusion-program)
        counsel   (resource-name s :counseling-service)]
    [{:id "S01"
      :title "Facility maintenance (routine)"
      :note "Non-gaming plant equipment; nothing in restricted territory."
      :expect :commit
      :proposal {:op :schedule-facility-maintenance
                 :facility-id :casino-downtown
                 :effect :propose
                 :content (str "Schedule HVAC filter replacement in the back-of-house "
                               "plant room at " downtown " for next Tuesday.")}}

     {:id "S02"
      :title "Supply coordination (routine)"
      :note "Housekeeping consumables; no gaming inventory."
      :expect :commit
      :proposal {:op :coordinate-supply-request
                 :facility-id :racebook-metro
                 :effect :propose
                 :content (str "Restock housekeeping consumables and office paper for "
                               racebook " ahead of the weekend.")}}

     {:id "S03"
      :title "Staff shift proposal"
      :note "Administrative rostering only; named staff come from the store's staff register."
      :expect :commit
      :proposal {:op :schedule-staff-shift-proposal
                 :facility-id :casino-downtown
                 :effect :propose
                 :content (str "Propose swapping the Saturday late shift between "
                               (:name alice) " (" (:role alice) ") and the relief "
                               (:role bob) " rota.")}}

     {:id "S04"
      :title "Responsible-gambling referral (leaflet placement)"
      :note "Referral logistics: make the self-exclusion route visible at the desk."
      :expect :commit
      :proposal {:op :coordinate-responsible-gambling-referral
                 :facility-id :casino-downtown
                 :effect :propose
                 :content (str "Place " selfexcl " and " helpline
                               " information cards at the guest services desk of " downtown ".")}}

     {:id "S05"
      :title "Responsible-gambling referral (bilingual note, qualified carve-out)"
      :note (str "Content DOES contain restricted vocabulary, and the qualified carve-out in "
                 "HARD CHECK 3 lets it through because the op is a referral op. "
                 "The advisor scores it 0.0 on the same words -- advisory only, not the gate.")
      :expect :commit
      :proposal {:op :coordinate-responsible-gambling-referral
                 :facility-id :racebook-metro
                 :effect :propose
                 :content (str "ギャンブルの相談窓口として" counsel
                               "の案内カードを" racebook "の客室係へ配布する。")}}

     {:id "S06"
      :title "Safety concern (escalation)"
      :note "Always routed to a human; never auto-committed at any phase."
      :expect :escalate
      :proposal {:op :flag-safety-concern
                 :facility-id :casino-downtown
                 :effect :propose
                 :content (str "Guest at the " downtown " services desk asked how to enrol in "
                               selfexcl "; hand over to the duty manager.")}}

     {:id "S07"
      :title "Maintenance request carrying an odds instruction"
      :note "The maintenance half is fine; the second clause is not this actor's territory."
      :expect :hold
      :proposal {:op :schedule-facility-maintenance
                 :facility-id :casino-downtown
                 :effect :propose
                 :content (str "Schedule HVAC maintenance at " downtown
                               " and set the odds board for the weekend card.")}}

     {:id "S08"
      :title "Supply request carrying a patron-verification instruction"
      :note "Age/identity verification of patrons is permanently outside this actor's scope."
      :expect :hold
      :proposal {:op :coordinate-supply-request
                 :facility-id :racebook-metro
                 :effect :propose
                 :content (str "Restock consumables at " racebook
                               " and verify patron age at the door.")}}

     {:id "S09"
      :title "Maintenance mis-addressed to a referral programme"
      :note (str "Addressed to a REAL seeded id (" selfexcl ") that is a referral resource, "
                 "not a facility -- so it is not registered/verified as one.")
      :expect :hold
      :proposal {:op :schedule-facility-maintenance
                 :facility-id :self-exclusion-program
                 :effect :propose
                 :content "Schedule quarterly inspection of the signage cabinet."}}

     {:id "S10"
      :title "Supply request submitted with :commit effect"
      :note "This actor may only ever propose. A submitted effect other than :propose is refused outright."
      :expect :hold
      :proposal {:op :coordinate-supply-request
                 :facility-id :racebook-metro
                 :effect :commit
                 :content (str "Restock guest services stationery at " racebook ".")}}

     {:id "S11"
      :title "Shift proposal, mis-addressed AND carrying a payout instruction"
      :note "Two HARD checks fail independently -- the checks are atomic, not short-circuited."
      :expect :hold
      :proposal {:op :schedule-staff-shift-proposal
                 :facility-id :counseling-service
                 :effect :propose
                 :content "Adjust the weekend roster and reconcile the payout sheet."}}

     {:id "S12"
      :title "Safety concern, mis-addressed"
      :note (str "The governor REFUSES (facility check), but `decide` routes any "
                 ":flag-safety-concern to escalation regardless, so this refusal never "
                 "becomes a hold. Nothing commits either way; a human sees it.")
      :expect :escalate
      :proposal {:op :flag-safety-concern
                 :facility-id :counseling-service
                 :effect :propose
                 :content (str "Guest asked for the " helpline
                               " number at the front desk; escalating to the duty manager.")}}

     {:id "S13"
      :title "Supply request carrying a self-exclusion ENROLMENT instruction"
      :note (str "OBSERVED GAP, shown deliberately: enrolling a person in a self-exclusion "
                 "programme is an eligibility determination, but the phrase is not in HARD "
                 "CHECK 3's keyword list, so the proposal is approved. Recorded here because "
                 "this page reports what the actor does, not what it ought to do.")
      :expect :commit
      :proposal {:op :coordinate-supply-request
                 :facility-id :racebook-metro
                 :effect :propose
                 :content (str "Restock guest services stationery at " racebook
                               " and add the guest at table five to the self-exclusion list.")}}]))

;; ---------------------------------------------------------------------------
;; run
;; ---------------------------------------------------------------------------

;; `governor/govern` returns its checks positionally, in this order.
(def ^:private check-kinds [:facility-verified :effect-propose :scope-exclusion])

(defn- failed-kinds [governor-result]
  (->> (map vector check-kinds (:checks governor-result))
       (remove (comp :pass? second))
       (mapv first)))

(defn- passed-notes
  "`:note` values attached to a check that PASSED -- this is how the qualified
  scope carve-out announces itself."
  [governor-result]
  (->> (:checks governor-result) (filter :pass?) (keep :note) vec))

(defn run-demo!
  "Drives every scenario through the real workflow against one seeded store,
  under phase 1 (auto-commit with escalation) and, for the same formatted
  results, phase 0 (read-only audit). Returns the store, the register value
  before and after the run (so retention can be MEASURED rather than assumed),
  and one record per scenario."
  []
  (let [s (store/make-store)
        before @s
        scs (scenarios s)
        rows (mapv (fn [sc]
                     (let [final     (op/run-workflow s (:proposal sc))
                           formatted (op/format-result final)
                           gr        (:governor-result final)]
                       (assoc sc
                              :advisor    (:advisor-result final)
                              :governor   gr
                              :result     formatted
                              :violations (vec (:governance-failures formatted))
                              :failed-kinds (failed-kinds gr)
                              :notes      (passed-notes gr)
                              :phase1     (phase/apply-phase 1 formatted)
                              :phase0     (phase/apply-phase 0 formatted))))
                   scs)]
    {:store s :before before :after @s :rows rows}))

(defn- governor-refused? [row] (false? (get-in row [:result :governor-approved?])))
(defn- action [row] (get-in row [:phase1 :action]))
(defn- hard-hold? [row] (and (governor-refused? row) (= :hold (action row))))

;; ---------------------------------------------------------------------------
;; build-time invariants -- the page is not written unless these hold
;; ---------------------------------------------------------------------------

(defn- assert-traceable!
  "Every id addressed by a scenario must exist in the seeded store. An invented
  patron/facility id fails the build rather than reaching the page."
  [s rows]
  (let [known (seed-ids s)
        bad (remove #(contains? known (get-in % [:proposal :facility-id])) rows)]
    (when (seq bad)
      (throw (ex-info "refusing to render: scenario addresses an id absent from the seeded store"
                      {:unknown (mapv #(vector (:id %) (get-in % [:proposal :facility-id])) bad)
                       :seed-ids (vec (sort known))})))))

(defn- assert-expectations! [rows]
  (let [bad (remove #(= (:expect %) (action %)) rows)]
    (when (seq bad)
      (throw (ex-info "refusing to render: a scenario did not reach its expected phase-1 action"
                      {:diverged (mapv #(hash-map :id (:id %) :expected (:expect %)
                                                  :actual (action %))
                                       bad)})))))

(defn- assert-hard-holds!
  "The point of this console is that the governor genuinely refuses things, so
  a run that produced no HARD hold must NOT be written out. A hold with an empty
  violation list is a phase/rollout gate, not a governor refusal -- counting it
  would let the invariant pass on a run where the governor never fired, so those
  are rejected too."
  [rows]
  (let [holds (filterv hard-hold? rows)
        kinds (into (sorted-set) (mapcat :failed-kinds holds))
        empty-violations (filterv #(empty? (:violations %)) holds)]
    (when (zero? (count holds))
      (throw (ex-info "refusing to write the console: the run produced ZERO HARD governor holds"
                      {:rows (count rows)
                       :actions (frequencies (map action rows))})))
    (when (seq empty-violations)
      (throw (ex-info "refusing to write the console: a HARD hold carried no violations (phase gate misread as a governor refusal)"
                      {:ids (mapv :id empty-violations)})))
    (when (< (count kinds) (count check-kinds))
      (throw (ex-info "refusing to write the console: not every HARD check was exercised"
                      {:exercised (vec kinds) :expected check-kinds})))
    {:holds holds :kinds kinds}))

;; ---------------------------------------------------------------------------
;; approver attribution -- MEASURED at render time, not assumed
;; ---------------------------------------------------------------------------

(def ^:private approver-key-re
  #"(?i)approver|approved[-_]by|authoriz(er|ed[-_]by)|signed[-_]?off|sign[-_]?off|reviewer|granted[-_]by")

(def ^:private approval-shaped-key-re #"(?i)appro|authoriz|sign[-_]?off|review")

(defn- keys-matching
  "Walk any nested value and collect every map key whose name matches `re`.
  Derived from the live value at render time, so if the store ever starts
  retaining an approver this section reports it without an edit here."
  [re v]
  (let [acc (volatile! (sorted-set))]
    (walk/postwalk
     (fn [x]
       (when (map? x)
         (doseq [k (keys x)]
           (when (and (or (keyword? k) (string? k) (symbol? k))
                      (re-find re (name k)))
             (vswap! acc conj (str k)))))
       x)
     v)
    (vec @acc)))

(defn- retention-report
  "What this actor actually retains, measured by comparing the store registers
  before and after the run and by scanning both the registers and the produced
  records for approver-shaped keys."
  [{:keys [before after rows]}]
  (let [mutated? (not= before after)
        register-approver-keys (keys-matching approver-key-re after)
        record-approver-keys (keys-matching approver-key-re rows)
        register-approval-shaped (keys-matching approval-shaped-key-re after)
        record-approval-shaped (keys-matching approval-shaped-key-re (mapv :result rows))]
    {:mutated? mutated?
     :register-approver-keys register-approver-keys
     :record-approver-keys record-approver-keys
     :register-approval-shaped register-approval-shaped
     :record-approval-shaped record-approval-shaped
     :verdict
     (cond
       (seq record-approver-keys)
       (str "An approver identity IS carried on the produced records ("
            (str/join ", " record-approver-keys) ").")

       (seq register-approver-keys)
       (str "An approver identity IS retained in the store registers ("
            (str/join ", " register-approver-keys) ").")

       mutated?
       (str "The run mutated the store registers, but no approver-identity key was "
            "found in them or on the produced records.")

       :else
       (str "No approval path exists in this actor. The store registers are "
            "byte-identical before and after the run — the workflow ends at "
            ":commit / :hold / :escalate as a returned state and writes nothing "
            "back, so there is no committed record for an approver to survive "
            "onto. The only approval-shaped keys anywhere in the run are "
            "boolean verdicts, not identities."))}))

;; ---------------------------------------------------------------------------
;; rendering
;; ---------------------------------------------------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (str ":" (name v)) (str v)))

(defn- code [v] (str "<code>" (esc (kw v)) "</code>"))

(defn- row-tag [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" (esc %) "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (if (seq rows) (str (str/join "\n" rows) "\n") "")
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       (if lede (str "    <p class=\"muted\">" lede "</p>\n") "")
       body
       "  </section>\n"))

(defn- yes-no [b]
  (if b "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>"))

(defn- disposition-cell [row]
  (case (action row)
    :commit "<span class=\"ok\">committed</span>"
    :escalate "<span class=\"warn\">escalated to human</span>"
    :hold (if (governor-refused? row)
            "<span class=\"critical\">HARD hold</span>"
            "<span class=\"warn\">held</span>")
    (str "<span class=\"muted\">" (esc (kw (action row))) "</span>")))

(defn- facility-rows [s]
  (mapv (fn [{:keys [id name registered? verified? location]}]
          (row-tag (code id) (esc name) (esc location)
                   (yes-no registered?) (yes-no verified?)))
        (sort-by :id (store/list-facilities s))))

(defn- resource-rows [s]
  (mapv (fn [{:keys [id name phone url available?]}]
          (row-tag (code id) (esc name) (esc (or phone url "—")) (yes-no available?)))
        (sort-by :id (store/list-referral-resources s))))

(defn- staff-rows [s]
  (mapv (fn [{:keys [id name role facility]}]
          (row-tag (code id) (esc name) (esc role) (code facility)))
        (sort-by :id (vals (staff-register s)))))

(defn- disposition-rows [rows]
  (mapv (fn [row]
          (row-tag (esc (:id row))
                   (code (get-in row [:proposal :op]))
                   (code (get-in row [:proposal :facility-id]))
                   (code (get-in row [:proposal :effect]))
                   (esc (get-in row [:advisor :score]))
                   (if (governor-refused? row)
                     "<span class=\"critical\">refused</span>"
                     "<span class=\"ok\">approved</span>")
                   (esc (get-in row [:result :decision]))
                   (disposition-cell row)))
        rows))

(defn- hold-rows [rows]
  (mapv (fn [row]
          (row-tag (esc (:id row))
                   (code (get-in row [:proposal :op]))
                   (str/join ", " (map code (:failed-kinds row)))
                   (str "<span class=\"critical\">"
                        (esc (str/join " / " (map :reason (:violations row))))
                        "</span>")
                   (esc (:title row))))
        (filter hard-hold? rows)))

(defn- refused-not-held-rows [rows]
  (mapv (fn [row]
          (row-tag (esc (:id row))
                   (code (get-in row [:proposal :op]))
                   (str/join ", " (map code (:failed-kinds row)))
                   (esc (str/join " / " (map :reason (:violations row))))
                   (esc (kw (action row)))
                   (esc (get-in row [:phase1 :reason]))))
        (filter #(and (governor-refused? %) (not= :hold (action %))) rows)))

(defn- phase0-rows [rows]
  (mapv (fn [row]
          (row-tag (esc (:id row))
                   (code (get-in row [:proposal :op]))
                   (esc (kw (get-in row [:phase1 :action])))
                   (esc (kw (get-in row [:phase0 :action])))
                   (esc (get-in row [:phase0 :reason]))
                   (esc (count (:violations row)))))
        rows))

(defn- scenario-note-rows [rows]
  (mapv (fn [row]
          (row-tag (esc (:id row)) (esc (:title row))
                   (str "<code>" (esc (get-in row [:proposal :content])) "</code>")
                   (esc (:note row))))
        rows))

(defn- carve-out-rows [rows]
  (mapv (fn [row]
          (row-tag (esc (:id row))
                   (code (get-in row [:proposal :op]))
                   (esc (str/join " / " (:notes row)))
                   (esc (get-in row [:advisor :score]))
                   (esc (get-in row [:advisor :reasoning]))))
        (filter #(seq (:notes %)) rows)))

(def ^:private hard-check-rows
  ;; Documentation of this actor's fixed gate, taken from the docstrings of
  ;; `gamblingfacilityops.governor` -- fixed behaviour, not runtime telemetry,
  ;; so it is legitimately described rather than derived. Which of them FIRED
  ;; is derived, in the HARD-hold table above.
  [(row-tag "<code>:facility-verified</code>"
            "HARD CHECK 1 — the addressed facility must exist in the store and be both <code>:registered?</code> and <code>:verified?</code>.")
   (row-tag "<code>:effect-propose</code>"
            "HARD CHECK 2 — the submitted effect must be exactly <code>:propose</code>. No auto-commit, no execute.")
   (row-tag "<code>:scope-exclusion</code>"
            "HARD CHECK 3 — bilingual (EN/JA) substring scan for restricted territory: wager, odds, payout, currency/chip/token handling, age &amp; identity verification, KYC/AML, gaming-licence determination, clinical or diagnostic judgement. Qualified carve-out for <code>:flag-safety-concern</code> and <code>:coordinate-responsible-gambling-referral</code>, which must be able to name the harm they are referring on.")])

(defn- approver-list [ks]
  (if (seq ks)
    (str/join ", " (map #(str "<code>" (esc %) "</code>") ks))
    "<span class=\"muted\">none</span>"))

(defn render
  "Renders the full operator-console document from a completed `run-demo!`."
  [{:keys [store rows] :as run}]
  (let [s store
        report (retention-report run)
        holds (filterv hard-hold? rows)
        refused-not-held (filterv #(and (governor-refused? %) (not= :hold (action %))) rows)
        kinds (into (sorted-set) (mapcat :failed-kinds holds))
        acts (frequencies (map action rows))]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"en\">\n<head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-920 &middot; gambling facility operations &mdash; Operator Console</title>"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head>\n<body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Gambling &amp; betting facility administration (ISIC 920) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · this actor may only ever <code>:propose</code></span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "What this page is"
      (str "Build-time snapshot generated by <code>gamblingfacilityops.render-html</code> "
           "(<code>clojure -M:render-html</code>) by RUNNING the real actor — "
           "<code>gamblingfacilityops.operation/run-workflow</code> "
           "(intake → advise → govern → decide) over a freshly seeded "
           "<code>gamblingfacilityops.store</code>, then "
           "<code>gamblingfacilityops.phase/apply-phase</code>. Nothing below is hand-written: "
           "every id is asserted at build time to exist in the seeded store, and the build "
           "refuses to write this file if the run produces no HARD governor hold, if a hold "
           "carries no violations, if any HARD check went unexercised, or if any scenario "
           "reaches a disposition other than the one it declares.")
      (table ["Measure" "Value"]
             [(row-tag "Scenarios run" (esc (count rows)))
              (row-tag "Committed" (esc (get acts :commit 0)))
              (row-tag "Escalated to a human" (esc (get acts :escalate 0)))
              (row-tag "Held" (esc (get acts :hold 0)))
              (row-tag "Governor refusals (HARD checks failed)"
                       (esc (count (filter governor-refused? rows))))
              (row-tag "…of which became HARD holds" (esc (count holds)))
              (row-tag "…of which were routed to escalation instead"
                       (esc (count refused-not-held)))
              (row-tag "Distinct HARD checks that fired"
                       (str/join ", " (map code kinds)))]))

     (section
      "Facility register (seed)"
      "Only a facility that is present here and both registered and verified can be addressed by a proposal — HARD CHECK 1."
      (table ["Id" "Name" "Location" "Registered?" "Verified?"] (facility-rows s)))

     (section
      "Responsible-gambling referral resources (seed)"
      "The routes this actor may point a guest or a staff member at. It coordinates the referral; it never makes a clinical or eligibility determination."
      (table ["Id" "Name" "Contact" "Available?"] (resource-rows s)))

     (section
      "Staff register (seed)"
      "Named in shift proposals below. Read from the store's <code>:staff</code> register."
      (table ["Id" "Name" "Role" "Facility"] (staff-rows s)))

     (section
      "Dispositions (phase 1 — auto-commit with escalation)"
      "One row per scenario, straight off the workflow. The advisor score is advisory only; the governor is the gate."
      (table ["#" "Op" "Addressed to" "Effect" "Advisor" "Governor" "Decision" "Phase-1 action"]
             (disposition-rows rows)))

     (section
      "HARD governor holds"
      (str "Refusals produced by the governor itself. Each carries at least one violation; "
           "none of them can be overridden, and none of them reached a human queue — they "
           "stopped at the gate. Compare with the phase-0 table further down, whose holds "
           "carry <em>zero</em> violations because they are a rollout gate, not a refusal.")
      (table ["#" "Op" "HARD check(s) failed" "Governor reason" "Scenario"] (hold-rows rows)))

     (if (seq refused-not-held)
       (section
        "Governor refusals that did NOT become holds"
        (str "Measured, not assumed: <code>operation/decide</code> routes any "
             "<code>:flag-safety-concern</code> to escalation before it looks at the governor "
             "verdict, so a safety concern that fails a HARD check is still handed to a human "
             "rather than held. Nothing commits either way — but a naive count of holds would "
             "miss these refusals, so they are listed separately.")
        (table ["#" "Op" "HARD check(s) failed" "Governor reason" "Phase-1 action" "Phase-1 reason"]
               (refused-not-held-rows rows)))
       "")

     (section
      "The gate (three HARD checks)"
      "Permanent, un-overridable, applied to every proposal. Structural, not policy — there is no override flag anywhere in this actor."
      (table ["Check" "What it enforces"] hard-check-rows))

     (let [rs (carve-out-rows rows)]
       (if (seq rs)
         (section
          "Qualified scope carve-out (fired this run)"
          (str "A referral or safety-concern op has to be able to name the harm it is "
               "referring on, so HARD CHECK 3 passes it with a note instead of refusing. "
               "The advisor scores the same text on the same vocabulary and returns 0.0 — "
               "that divergence is expected and is shown here rather than hidden, because "
               "the advisor does not gate anything.")
          (table ["#" "Op" "Governor note" "Advisor score" "Advisor reasoning"] rs))
         ""))

     (section
      "Phase-0 rollout gate (same proposals, read-only audit)"
      (str "Phase 0 holds <em>everything</em>, including the proposals phase 1 commits, and "
           "its holds carry no violations at all. This is a rollout gate, not a governor "
           "refusal; the two are counted separately everywhere on this page.")
      (table ["#" "Op" "Phase-1 action" "Phase-0 action" "Phase-0 reason" "Violations"]
             (phase0-rows rows)))

     (section
      "Record retention &amp; approver attribution (measured this run)"
      (str "Measured by comparing the store registers before and after the run and by "
           "scanning both the registers and the produced records for approver-shaped keys "
           "at render time — so this section self-corrects if the store ever starts "
           "retaining an approver.")
      (table ["Question" "Answer"]
             [(row-tag "Did the run mutate the store registers?"
                       (if (:mutated? report)
                         "<span class=\"warn\">yes</span>"
                         "<span class=\"muted\">no — byte-identical before and after</span>"))
              (row-tag "Approver-identity keys in the store registers"
                       (approver-list (:register-approver-keys report)))
              (row-tag "Approver-identity keys on the produced records"
                       (approver-list (:record-approver-keys report)))
              (row-tag "Approval-shaped keys anywhere on a record (audit only — not retained on record)"
                       (approver-list (:record-approval-shaped report)))
              (row-tag "Verdict" (str "<span class=\"muted\">" (esc (:verdict report)) "</span>"))]))

     (section
      "Scenario texts"
      "The exact proposal text each row above was built from. Facility, resource and staff names are read out of the store rather than retyped."
      (table ["#" "Scenario" "Proposal content" "Note"] (scenario-note-rows rows)))

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-920 · gamblingfacilityops · AGPL-3.0 · generated by <code>clojure -M:render-html</code> from a real actor run; no timestamps, byte-identical across reruns against the same seed.</p>\n"
     "</footer>\n"
     "</body>\n</html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [store rows] :as run} (run-demo!)]
    (assert-traceable! store rows)
    (assert-expectations! rows)
    (let [{:keys [holds kinds]} (assert-hard-holds! rows)
          html (render run)
          report (retention-report run)]
      (spit out html)
      (println "wrote" out
               (str "(" (count rows) " scenarios, "
                    (count holds) " HARD governor holds over "
                    (count kinds) " distinct checks " (vec kinds) ", "
                    (count (filter #(and (governor-refused? %) (not= :hold (action %))) rows))
                    " refusal(s) routed to escalation instead, "
                    (count html) " bytes)"))
      (println "retention:" (:verdict report)))))
