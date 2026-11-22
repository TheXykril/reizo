# Agent protocol

Every agent (and human) follows this at the start and end of a working session. The repository,
not chat history, is the shared memory.

## Start of session

1. `git status`, `git remote -v`, `git branch -a`: note uncommitted work. **Never overwrite or
   discard changes you did not make.** If the tree is dirty and not yours, stop and report.
2. Read [AGENTS.md](../AGENTS.md).
3. Read [HANDOFF.md](HANDOFF.md).
4. Read the architecture docs relevant to your task (ARCHITECTURE.md, FORMAT.md).
5. List open issues and pull requests (`gh issue list`, `gh pr list`); check who is working on what
   (assignee, `status: in progress` label, open branches).
6. Verify you are on the right branch; create `feat/...`/`fix/...` from up-to-date `main` for new
   work.
7. `git log --oneline -20` on `main` and the working branch.
8. Read the actual source of the area you will touch. **HANDOFF.md may be stale: when it conflicts
   with code or git history, trust code and history, then fix the doc.**
9. Pick the next valid task: the issue HANDOFF.md recommends, else the lowest open milestone's
   `status: ready` issue. Check nothing already implements it (`grep`, closed PRs).
10. Comment on the issue and set `status: in progress` so others do not duplicate it.

## During the session

- Small, reviewable commits with Conventional Commit messages.
- Run the relevant tests after each meaningful change; never claim a test passed without running it.
- Architectural changes: ADR in `docs/DECISIONS/` and a PR the owner reviews before merge.
- Do not edit files another open PR is changing unless coordinated in the issue.

## End of session

1. Review every modified file (`git diff`).
2. Run relevant tests: at least `./gradlew build`; gametests if `fabric` changed.
3. Record test outcomes (command, pass/fail counts, date) in HANDOFF.md section 8.
4. Update FEATURES.md statuses with evidence.
5. Rewrite HANDOFF.md (all 12 sections, concise, current).
6. Update issues: progress comment, labels, close what is done (via PR `Closes #N`).
7. Commit completed work. Incomplete work: commit on the branch with `WIP:` in HANDOFF.md section
   4, never on `main`.
8. Push the branch (`git push -u origin <branch>`). Never force-push shared branches.
9. Report progress and blockers to the owner, including whether another agent can continue
   without extra context.

## Conflict prevention

- One agent per issue; claim it first.
- Branch per task; no direct commits to `main` except docs-only handoff fixes by the owner.
- Never silently discard, revert or reformat someone else's changes.
- If two branches touch the same file, the later one rebases and resolves; ask in the issue if
  unclear.
- Record incomplete work accurately; "almost done" is not a status.

## Issue workflow

Labels `status: backlog` → `status: ready` → `status: in progress` → `status: review` → closed
(done). Plus `type: feature|bug|docs|chore|test` and `area: core|fabric|editor|export|audio|ci`.
