# Governance

cloud-itonami-isic-920 is governed by a simple, conservative model:

## Decision Making

- **Architect** (Jun Kawasaki) has final decision authority on scope, design, and releases.
- **Contributors** submit PRs for review; PRs are reviewed against three criteria:
  1. Do the three HARD governor checks remain unconditional?
  2. Does the change preserve scope exclusion (no wagering/odds/payout/verification/clinical content)?
  3. Does the change maintain escalation semantics (safety concerns always escalate)?

## Stability

- The three HARD governor checks are **frozen**. They may only be modified via ADR (architectural decision record) with explicit approval.
- Scope exclusion keywords are **frozen** unless directly addressing a known false-positive or false-negative.
- Escalation behavior is **frozen**.

## Releases

Releases are tagged `vX.Y.Z` and must pass:

1. Full test suite (`kbb -M:test -m gamblingfacilityops.test`)
2. Demo scenarios (`kbb -M -m gamblingfacilityops.sim`)
3. Review by architect
4. Registry update (kotoba-lang/industry entry 920)

## Zero Tolerance

This is a **real-money wagering domain**. The actor's default is to say NO. Any deviation from this principle requires explicit ADR and approval before implementation.
