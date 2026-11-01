# CLAUDE.md

All project guidance for coding agents lives in `AGENTS.md` so it is shared across tools. Claude Code imports it here:

@AGENTS.md

## Delegation and models

Simple tasks are always done by subagents running Sonnet or smaller (`model: "sonnet"` or `"haiku"` in the Agent call), not in the main session. Simple means clear scope and no design decisions, for example:

- adding or updating string resources across all `values-*` translations
- single-file or mechanical edits (renames, boilerplate, small fixes, formatting)
- searching the code base, running Gradle tasks and reporting the result
- one dialog/modal file following an existing pattern

The main session keeps planning, architecture, Room schema/migrations, API integration design, multi-file refactors and the final review.

When delegating:

- Give the subagent a self-contained prompt (files, expected result, relevant conventions from `AGENTS.md`); it starts without this conversation's context.
- Bundle related simple steps into one subagent instead of spawning one per step.
- Ask for a short report (what changed, which files), not file dumps.
- Check the result (diff, build/tests if available) before reporting it as done.
