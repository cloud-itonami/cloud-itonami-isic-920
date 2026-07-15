# Security Policy

## Reporting Security Vulnerabilities

Please report security vulnerabilities privately to the maintainers **immediately**. Do not open public issues for security vulnerabilities.

Contact: jun784@gmail.com

## Security Guarantees in This Domain

This actor operates in real-money wagering, where security lapses can cause **direct financial harm and problem-gambling risk**. The following are non-negotiable:

### 1. Governor Hard Checks are Un-Bypassable

The three HARD governor checks (facility verification, effect validation, scope exclusion) are structural language-level gates implemented in `.cljc` and tested exhaustively. There is no override path, no escape hatch, no special privilege that bypasses them.

### 2. Scope Exclusion is EN+JA Scanned

Forbidden keywords (wager, odds, payout, currency, verification, clinical, license) are scanned in both English and Japanese to prevent multilingual prompt injection.

### 3. Safety Concerns Always Escalate

Operations flagged as `:flag-safety-concern` or `:coordinate-responsible-gambling-referral` cannot be auto-committed; they always escalate to human review.

### 4. No Clinical Determination by the Actor

The actor NEVER determines whether a patron has a gambling problem. This is a clinical/expert determination made by qualified professionals, not by an automated system. The actor's role is logistics only (connecting to resources).

## Known Limitations

- **Demo Advisor**: The deterministic demo advisor is toy code. Production requires real LLM with aggressive prompt-injection safeguards.
- **MemStore**: In-memory storage is for demo only. Production needs persistent backing (database, audit ledger, etc.).
- **Language**: This demo is Clojure/.cljc. Translation to other languages must preserve all governor checks exactly.

## Future Work

- [ ] Add rate-limiting to prevent resource exhaustion
- [ ] Add audit logging for all operations
- [ ] Replace demo advisor with production LLM + safeguards
- [ ] Replace MemStore with persistent backing store
- [ ] Add cryptographic signing for audit trail
- [ ] Implement formal verification of governor checks

## References

- ADR-2607154300 (This actor's design and scope constraints)
- GOVERNANCE.md (Approval process for changes)
- README.md (Harm minimization design)
