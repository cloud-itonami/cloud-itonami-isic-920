# Contributing

Thank you for your interest in contributing to cloud-itonami-isic-920!

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch
4. Make your changes
5. Run tests: `kbb -M:test -m gamblingfacilityops.test`
6. Commit with a clear message
7. Push to your fork
8. Create a pull request

## Code Guidelines

- All code must be in `.cljc` (portable Clojure).
- Follow existing naming conventions (kebab-case for functions, :colon-case for keywords).
- Add tests for new functionality.
- Ensure all tests pass before submitting a PR.
- Keep commits atomic and well-documented.

## Critical Domain Constraint

This actor operates in the **real-money wagering domain**. Any changes must preserve:

1. **Three HARD governor checks** (no overrides, ever)
2. **Scope exclusion** (no wager/odds/payout/verification/clinical content)
3. **Escalation semantics** (safety concerns always escalate to human)

If your change touches the governor logic, scope exclusion keywords, or escalation behavior, please open an issue for discussion first.

## Testing

Run the full test suite before submitting:

```bash
kbb -M:test -m gamblingfacilityops.test
```

Run the demo scenarios:

```bash
kbb -M -m gamblingfacilityops.sim
```

## Reporting Issues

Please report bugs and security issues privately to the maintainers.

## License

All contributions are licensed under AGPL-3.0.
