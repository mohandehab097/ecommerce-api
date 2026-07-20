---
name: ship
description: Commits, pushes, and opens a PR for the current ecommerce backend branch. Use when the user says ship it, push this branch, commit and push, or open a PR. Assumes code-review already ran (or the user is explicitly skipping it) — this skill does not review or fix code, only git/PR mechanics.
---

# Ship

Git mechanics only. Never edits application code, never fixes findings — if
the user hasn't reviewed yet, recommend the **code-review** skill first, but
proceed if they say they want to skip it.

## Part 0 — Branch check (run first)

1. `git branch --show-current` and `git status`.
2. Decide if the current branch is related to the change being shipped:
   - Related = branch name plausibly matches the work (e.g. `feature/checkout`,
     `fix/cart-stock`), or the user just confirms "yes this is the right branch".
   - Unrelated = user is on `main`/`master`/`dev`, or on a branch that clearly
     belongs to different work, or has no uncommitted change tied to this task.
3. If unrelated (or ambiguous), **ask the user**: "You're on `<branch>` — is
   this the right branch for this change?" If they say no, or if on
   main/master/dev:
   - `git checkout main` (or the repo's actual default branch), `git pull`
   - Ask the user for a branch name if not given (suggest one from the change,
     e.g. `fix/checkout-stock-race`)
   - `git checkout -b <branch-name>`
4. Never force-switch branches or discard uncommitted work without asking —
   if `git status` shows uncommitted changes on the "wrong" branch, ask the
   user how to handle them (stash / commit here / move) before switching.

## Part 1 — Pre-flight

If there's no sign a review already happened in this conversation (no
findings table, no "review done" handoff), ask once:

> "Want me to run code-review first, or ship as-is?"

If the user says ship as-is, proceed — don't insist.

## Part 2 — One bundled confirmation

Before touching git, summarize in one short message: what's being shipped
(brief), and the plan ("commit, push, open PR — ok?"). Get a single go-ahead
for all three steps, not three separate asks. Only exception: ask separately
if something is still ambiguous (see below) — don't bundle an ambiguous
decision into the yes/no confirmation.

**Ask about anything still unknown** — don't guess:
- Commit message scope if it's not obvious from the diff.
- Target branch for the PR (default `main` — confirm if unsure).
- PR title/description — draft one, confirm before opening it.

If the user's request implies commit/push only, no PR ("just push, no PR"),
skip Part 4.

## Part 3 — Commit & push

1. **Commit.** Stage only files relevant to this change (never blind `git add -A`).
   Write a commit message that's short, clear, plain language — anyone on the
   team should understand it without reading the diff:
   - Subject line: one line, under 60 chars, imperative mood ("Fix stock race
     condition in checkout", not "Fixed a bug" or "Update OrderService.java").
   - Body (only if the *why* isn't obvious from the subject): 1-3 short
     sentences max. No restating the diff line-by-line.
   - Follow this repo's existing style (`git log` for reference) for anything
     not covered above (e.g. ticket refs, prefixes).
2. **Push.** Push the branch to `origin` with upstream tracking if not already
   set.

## Part 4 — Open PR

Use `gh pr create` with a title under 70 chars and a short, to-the-point body
— describe the *real* change, not padding:
- **Summary**: 2-4 bullets max, each one concrete change (what changed and
  why), no filler sentences, no restating the title.
- **Test plan**: short checklist of what was actually verified.
- **Review notes** (only if code-review or CodeRabbit left Medium/Low findings
  unfixed by choice): one line per finding, not the full table.
- Skip anything that doesn't add information a reviewer needs — if a
  section would just repeat the title or the diff, leave it out.
- Report back the PR URL.

Never force-push, never skip hooks, never amend existing commits, never
discard uncommitted work — ask the user if any of those seem necessary.
