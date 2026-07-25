(ns gamblingfacilityops.advisor
  "Proposal advisor for gambling facility operations.
   DETERMINISTIC DEMO ONLY: production requires real LLM with prompt injection safeguards."
  ;; clojure.string, not JS String methods: `.includes` is a JavaScript method
  ;; with no Java equivalent (Java uses `contains`), so this .cljc could only
  ;; ever run under ClojureScript despite its extension.
  (:require [clojure.string :as str]))

(defn advisability
  "Return advisability score (0–1) and reasoning for a proposal.
   DEMO: deterministic heuristics only.
   PRODUCTION: Replace with real LLM call + prompt-injection safeguards specific to wagering domain."
  [op facility-id content]
  (let [is-maintenance? (= op :schedule-facility-maintenance)
        is-supply? (= op :coordinate-supply-request)
        is-shift? (= op :schedule-staff-shift-proposal)
        is-referral? (= op :coordinate-responsible-gambling-referral)
        is-safety? (= op :flag-safety-concern)

        has-forbidden-words?
        (some #(and (string? content)
                    ;; EN match is case-insensitive; the JA terms are matched
                    ;; as-is, which lower-casing leaves unchanged anyway.
                    (or (str/includes? (str/lower-case (str content)) %)
                        (str/includes? (str content) %)))
              ["wager" "odds" "payout" "currency" "chip" "token"
               "age-verify" "identity" "aml" "kyc" "license"
               "ギャンブル" "賭け" "オッズ" "配当" "チップ"
               "年齢確認" "身分確認" "マネロン"])

        score (cond
                has-forbidden-words? 0.0
                is-safety? 0.95  ; Safety escalations are always high priority
                is-referral? 0.9 ; Referral logistics are high priority
                (or is-maintenance? is-supply?) 0.85  ; Admin ops are solid
                is-shift? 0.8    ; Shift proposals need review
                :else 0.5)

        reasoning (cond
                    has-forbidden-words?
                    "Content contains restricted territory (wager/odds/payout/verification/clinical)"
                    is-safety?
                    "Safety concern flagged for immediate escalation"
                    is-referral?
                    "Responsible-gambling referral logistics"
                    is-maintenance?
                    "Routine facility maintenance (non-gaming equipment)"
                    is-supply?
                    "Non-gaming supply coordination"
                    is-shift?
                    "Staff shift proposal (administrative only)"
                    :else
                    "Unknown operation")]

    {:score score
     :reasoning reasoning
     :confidence (if (> score 0.8) :high (if (> score 0.5) :medium :low))}))

(defn propose
  "Generate a proposal with advisor confidence and reasoning."
  [store op facility-id content]
  {:op op
   :facility-id facility-id
   :content content
   :advisor (advisability op facility-id content)
   ;; Was a bare (js/Date.now) in a .cljc file, so this namespace could not
   ;; compile on the JVM at all -- the file claimed portability it did not have.
   :timestamp #?(:clj (System/currentTimeMillis)
                 :cljs (js/Date.now))})
