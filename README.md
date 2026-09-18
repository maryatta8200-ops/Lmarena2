# Lmarena2

This repository is at an early state: `main` currently tracks this `README.md` only. There is no
master plan, decision log, documented project phase, stage gate, `CONTRIBUTING.md`,
`SECURITY.md`, test suite, or CI workflow on the default branch yet. Related work proposed on
other branches is unmerged and unreviewed — see
[`docs/automation/lmarena-agent.md`](docs/automation/lmarena-agent.md#current-repository-state).

## Documentation

| Document | Purpose |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Operating contract for an LMArena/LMarena or other coding agent: precedence, hard boundaries, execution loop, completion report |
| [`docs/automation/lmarena-agent.md`](docs/automation/lmarena-agent.md) | Operator guide: how to submit bounded work, preflight and validation lifecycle, least-privilege GitHub access, review requirements, known risks in pending work |
| [`.github/ISSUE_TEMPLATE/lmarena-work-order.yml`](.github/ISSUE_TEMPLATE/lmarena-work-order.yml) | Structured work-order form used to hand a bounded task to an agent |

## LMArena automated work

The repository includes a provider-neutral [`AGENTS.md`](AGENTS.md) contract for an LMArena (also
written "LMarena") coding agent. Each task must have a bounded objective, named affected files,
traceability to a controlling document or an explicit maintainer instruction, measurable
acceptance criteria, scope exclusions, risks, and a rollback path. The contract requires the agent
to re-verify the live phase and gate state from the repository before every task, to stop and ask
rather than invent a missing gate or decision, and to stop for maintainer approval before touching
a phase or stage gate, a schema, public behavior, artifact compatibility, dependencies, golden
outputs, benchmark or research claims, datasets, sensitive-data handling, security or network
exposure, or an existing decision record.

For GitHub-based handoffs, open the
[LMArena work-order form](.github/ISSUE_TEMPLATE/lmarena-work-order.yml). It captures the
objective, traceability, stage impact, acceptance evidence, safeguards, rollback path, and any
authorization required.

All work happens on an isolated task branch and reaches `main` only through a pull request that a
maintainer reviews. The agent does not push to `main`, auto-merge, force-push, rewrite history, or
weaken CI.

This repository intentionally contains **no** provider or model credentials, API keys, GitHub
tokens, browser credentials, keystores, secrets, webhooks, autonomous scheduling, self-invoking
runners, browser automation, background runners, or auto-merge permissions. No agent is running
here: `AGENTS.md` is a contract that an operator's chosen agent host must load. Repository files
are not a security boundary — enforce the real limits with GitHub branch protection, required
status checks, least-privilege tokens, secret scanning, and human review, as described in the
[operator guide](docs/automation/lmarena-agent.md#least-privilege-github-access).
