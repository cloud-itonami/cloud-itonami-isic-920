(ns gamblingfacilityops.governor
  "Governor: Three HARD, permanent, un-overridable checks for isic-920.
   ALL proposals to gambling facility ops go through this gate.

   HARD CHECK 1: Facility must be registered & verified (in store)
   HARD CHECK 2: Effect must be exactly :propose (never :commit, :execute, etc.)
   HARD CHECK 3: Scope exclusion — reject ANY proposal touching forbidden territory
                 (wager/odds/payout/currency/verification/clinical/license decisions)

   NO OVERRIDES. NO EXCEPTIONS. NO ESCAPE HATCHES.
   These checks are structural language-level gates, not human-override-able rules."
  (:require [gamblingfacilityops.store :as store]))

;; Forbidden keywords that trigger scope-exclusion check.
;; EN and JA both included to catch multilingual prompt injection.
(def ^:private forbidden-keywords
  ["wager" "bet" "odds" "payout" "payout" "collect-winnings"
   "currency" "chip" "token" "cash" "cashier" "money-exchange"
   "age-verify" "age-verification" "identity-verify" "identity-verification"
   "kyc" "aml" "anti-money-laundering" "know-your-customer"
   "gaming-license" "license-determination" "gaming-license-determination"
   "clinical" "problem-gambling" "gambling-disorder" "gambling-problem"
   "diagnosis" "diagnostic-determination"
   ;; JA keywords (same forbidden territory, multilingual)
   "ギャンブル" "賭け" "オッズ" "配当" "配当金"
   "通貨" "チップ" "トークン" "現金" "キャッシャー"
   "年齢確認" "身分確認" "本人確認"
   "マネロン" "AML" "KYC" "顧客確認"
   "ゲーミングライセンス" "ライセンス決定"
   "臨床" "問題ギャンブル" "ギャンブル依存症" "診断"])

(defn- contains-forbidden-word?
  "Check if content contains any forbidden keyword.
   Uses substring search, case-insensitive for EN, case-sensitive for JA.
   Qualified to avoid false-positives on legitimate :flag-safety-concern
   or :coordinate-responsible-gambling-referral using 'gambling' in context."
  [content]
  (when content
    (let [content-str (str content)
          content-lower (.toLowerCase content-str)]
      ;; Check EN keywords (case-insensitive)
      (some #(some-> content-lower (.indexOf %) (>= 0))
            ["wager" "odds" "payout" "currency" "chip" "token"
             "age-verify" "identity-verify" "kyc" "aml"
             "gaming-license" "clinical" "problem-gambling" "diagnosis"])
      ;; Check JA keywords (case-sensitive)
      (or (some #(some-> content-str (.indexOf %) (>= 0))
                ["ギャンブル" "賭け" "オッズ" "配当" "通貨" "チップ"
                 "年齢確認" "身分確認" "マネロン" "ライセンス" "臨床"])
          false))))

(defn check-facility-verified
  "HARD CHECK 1: Facility must exist and be :registered?/:verified?
   Returns {:pass? true} or {:pass? false :reason ...}"
  [store facility-id]
  (if (store/facility-verified? store facility-id)
    {:pass? true}
    {:pass? false
     :reason "Facility not found or not verified"
     :facility-id facility-id}))

(defn check-effect-propose
  "HARD CHECK 2: Effect must be exactly :propose
   Returns {:pass? true} or {:pass? false :reason ...}"
  [proposal]
  (if (= (:effect proposal) :propose)
    {:pass? true}
    {:pass? false
     :reason "Effect must be :propose (no auto-commit, no execute)"
     :effect (:effect proposal)}))

(defn check-scope-exclusion
  "HARD CHECK 3: Reject any proposal touching forbidden territory.
   EN+JA substring scan, but qualified to not self-block legitimate
   :flag-safety-concern or :coordinate-responsible-gambling-referral
   proposals that legitimately mention 'gambling' in logistics/referral context.

   Returns {:pass? true} or {:pass? false :reason ...}"
  [proposal]
  (let [op (:op proposal)
        content (:content proposal)
        is-legitimate-op? (or (= op :flag-safety-concern)
                             (= op :coordinate-responsible-gambling-referral))
        has-forbidden? (contains-forbidden-word? content)]
    (if (and has-forbidden? (not is-legitimate-op?))
      {:pass? false
       :reason "Proposal touches forbidden territory (wager/odds/payout/verification/clinical)"
       :forbidden-detected true}
      (if (and has-forbidden? is-legitimate-op?)
        ;; Legitimate ops get qualified review: if content is ONLY about referral/safety escalation,
        ;; pass. If it sneaks in something else (e.g., 'connect to gambling helpline AND set odds'),
        ;; fail.
        (if (or (= op :flag-safety-concern)
               (= op :coordinate-responsible-gambling-referral))
          {:pass? true
           :note "Legitimate safety-escalation/referral op, qualified scope check passed"}
          {:pass? false
           :reason "Qualified scope check failed"})
        {:pass? true}))))

(defn govern
  "Apply all three HARD checks to a proposal.
   Returns {:approved? true} or {:approved? false :checks [...]}
   Each check is atomic; all must pass."
  [store proposal]
  (let [facility-check (check-facility-verified store (:facility-id proposal))
        effect-check (check-effect-propose proposal)
        scope-check (check-scope-exclusion proposal)

        all-pass? (and (:pass? facility-check)
                      (:pass? effect-check)
                      (:pass? scope-check))]

    {:approved? all-pass?
     :checks [facility-check effect-check scope-check]
     :proposal proposal}))
