# cloud-itonami-isic-920: Gambling and Betting Activities

**ISIC 920** — Gambling and betting activities (casinos, betting operations, lottery operators).

**CRITICAL CONSTRAINT**: This actor's scope is **STRICTLY limited to back-office facility administration and responsible-gambling referral logistics only**. It must NEVER touch wagering transactions, odds-setting, payout decisions, currency/chips, age/identity verification determinations, AML/KYC decisions, or any clinical determination about whether a patron has a gambling problem.

## Operations (Closed Allowlist)

- **`:schedule-facility-maintenance`** — Non-gaming-equipment/facility maintenance (housekeeping, HVAC, general upkeep). NOT gaming-equipment calibration/certification.
- **`:coordinate-responsible-gambling-referral`** — Administrative logistics connecting patrons to support resources. NEVER a determination that someone has a problem; triggered by patron request or independent flag.
- **`:coordinate-supply-request`** — Non-gaming consumables (F&B, housekeeping). NOT gaming chips/tokens/currency.
- **`:schedule-staff-shift-proposal`** — Administrative shift proposals only. NEVER gaming-license/table-assignment determinations.
- **`:flag-safety-concern`** — Facility/problem-gambling risk signals for HUMAN follow-up. ALWAYS escalates; NOT a clinical determination.

## Three HARD Governor Checks

1. **Facility unverified** — Target must exist AND be registered/verified.
2. **Effect not `:propose`** — All proposals must have effect `:propose`.
3. **Scope exclusion (critical)** — Any proposal touching wager/odds/payout/currency/verification/clinical/license decisions is rejected unconditionally.

NO OVERRIDES. NO EXCEPTIONS. These are structural language-level gates.

## Harm Minimization

Real-money wagering + problem-gambling risk demands **MAXIMALLY CONSERVATIVE** design:

- The actor NEVER determines whether a patron has a gambling problem (clinical determination is EXCLUSIVELY human/expert).
- The actor NEVER makes AML/KYC compliance decisions.
- The actor NEVER makes gaming-license/regulatory determinations.
- The actor NEVER handles currency, chips, or wagering transactions.
- The actor NEVER sets odds or payout decisions.
- Responsible-gambling referral is logistics only (connecting to resources); the actor does NOT diagnose.

## Modules

- **store.cljc** — In-memory facility/resource directory (demo; production needs persistent backing).
- **advisor.cljc** — Proposal scoring (deterministic demo; production needs real LLM + prompt-injection safeguards).
- **governor.cljc** — Three HARD checks (refuse-only gate, no overrides).
- **operation.cljc** — StateGraph workflow (intake → advise → govern → decide → commit|hold|escalate).
- **phase.cljc** — Rollout phases (0=read-only audit, 1=auto-commit with escalation).
- **sim.cljc** — Demo driver (5 scenarios: happy path, hard checks, scope violations, escalation).
- **test.cljc** — Comprehensive test suite (16 tests covering all critical paths).

## Build & Test

```bash
# Dependencies
clj -M:deps

# Run demo
clj -M -m gamblingfacilityops.sim

# Run tests
clj -M:test -m gamblingfacilityops.test
```

## Registry

This actor updates `kotoba-lang/industry` registry entry 920:
- `:maturity` → `:implemented`
- `:repo` → `cloud-itonami/cloud-itonami-isic-920`
- `:business-id` → `itonami-isic-920`

## License

AGPL-3.0. See LICENSE file.

## ADR

ADR-2607154300: Design, scope constraints, and governance architecture.

---

**ALWAYS REMEMBER**: In real-money wagering, the DEFAULT is to say NO. Every operation must pass three unconditional governor checks. If in doubt, escalate to human.
