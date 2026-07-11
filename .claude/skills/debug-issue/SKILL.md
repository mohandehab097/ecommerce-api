---
name: debug-issue
description: Structured debugging for the ecommerce backend — classifies a bug as runtime/crash (exception, stack trace, app won't start/respond) vs logical/business-logic (app runs, but behavior is wrong), then drives the right investigation path to root cause and a minimal fix. Use whenever the user reports a bug, error, crash, exception, stack trace, 500, timeout, or "this is behaving wrong" / "wrong total/discount/stock" issue — including production incidents that need a fast diagnosis.
---

# Debug Issue

Two bug types need two different investigations. Don't start digging until
you know which one this is — ask first if it isn't obvious from what the
user already gave you.

## Hard rules (both paths)

- **Root cause, not symptom.** Fix where the defect originates. A change that
  hides the symptom without addressing why it happens is a band-aid, not a
  fix — say so explicitly if that's all that's possible right now, don't
  present it as done.
- **Smallest change that fixes it.** No drive-by refactors, renames, or
  "while I'm here" cleanup in the same diff — even if you spot something else
  wrong nearby. Note it separately, don't fold it in.
- **Test proves the fix, not just describes it.** Write the regression test
  *before* applying the fix, confirm it fails for the expected reason, then
  fix, then confirm it passes. A fix with no test that would have caught the
  original bug is incomplete.
- **Stuck after 2 attempts → stop and reconsider the diagnosis, don't
  keep patching.** If a fix doesn't resolve it (test still fails, bug still
  reproduces) twice in a row, the root cause is probably wrong — go back to
  A2/B2 with what you learned, don't try a third variation of the same guess.

## Step 0 — Classify

If the user's report already makes it obvious (they pasted a stack trace, or
said "app crashes" → runtime; they said "total is wrong" / "discount not
applying" → logical), skip the question and state your classification back
to them in one line before proceeding.

Otherwise ask directly:

> "Is this (a) a crash/error — app throws an exception, won't start, returns
> 500/timeout, or (b) a logical bug — app runs fine but the result/behavior
> is wrong (wrong total, stock, order status, etc.)?"

If it's a **production incident** (user says "prod", "production down",
"customers affected", "urgent"), flag that now — it changes priority in both
paths below (mitigate first, root-cause second).

---

## Path A — Runtime / crash bug

### A1. Gather evidence (ask if missing, don't guess)
- Full stack trace or error message. If the user only has "it crashed" with
  no trace, ask where to find it: app log file, console output, APM/log
  aggregator (e.g. CloudWatch, ELK, Datadog), or ask them to reproduce it and
  paste the output.
- What request/action triggered it (endpoint, input payload, user action).
- When it started (always broken vs. started after a specific deploy/change —
  check `git log` around that time if a regression is suspected).

### A2. Read the trace correctly
- Find the **first frame that's project code** (not Spring/Hibernate/JDK
  internals) — that's usually where the fix belongs, even if the exception
  originates deeper.
- Identify the exception type and map it to a likely cause:
  - `NullPointerException` → missing null check on a chained getter or
    optional dependency (see code-review Part 1 §2).
  - `OptimisticLockException`/constraint violation → concurrent write, see
    code-review §1 (race conditions).
  - `DataIntegrityViolationException` → bad input reaching the DB
    unvalidated, see code-review §8 (input validation).
  - `LazyInitializationException` → entity/collection accessed after its
    transaction/session closed — usually an entity leaked straight out of a
    controller or accessed outside `@Transactional` (see code-review §17).
  - `StackOverflowError` on serialization or logging an entity → Lombok
    `@ToString`/`@EqualsAndHashCode` (or default `toString`) walking a
    bidirectional relation with no `@JsonIgnore`/exclusion (see code-review
    §17).
  - Timeout / connection exhausted → long-held transaction, external call
    inside `@Transactional`, or missing pagination — see code-review §5, §11.
  - `ClassCastException`/deserialization error → DTO/entity mismatch.
- Read the actual code at that file:line — don't fix from the exception name
  alone, confirm what the code does there.

### A3. Reproduce (if possible)
- Write or run the smallest input that triggers it (unit test, curl, or
  manual steps). If it's prod-only and not reproducible locally, say so
  explicitly and work from evidence (logs, input payload) instead of
  guessing.
- Reuse the existing test class for that service (e.g. `CartServiceTest`)
  instead of creating a new test file — add a method to it. Only create a
  new `<Service>Test` file if none exists yet for that service.

### A4. Root cause → fix
- State the root cause in one sentence: what input/state condition, hitting
  what code path, causes what failure.
- Before touching code, state the fix plan in one line: file(s) to change,
  what changes, and what it could break (blast radius) — catches an
  over-broad fix before it's written, not after.
- Write the regression test first: reproduce the crash in a test, confirm it
  fails with the same exception/root cause you diagnosed (not a different
  one — if it fails differently, the diagnosis is wrong, go back to A2).
- Apply the smallest correct fix — don't refactor around it. Confirm the
  test now passes.

### A5. If this is a production incident
- Before root-causing, ask: is there a safe **immediate mitigation**
  (rollback last deploy, disable a feature flag, restart, revert one config)
  that stops the bleeding now? Recommend it and let the user decide — don't
  roll back anything yourself without confirmation.
- Do full root-cause + proper fix after mitigation, not instead of it.

---

## Path B — Logical / business-logic bug

### B1. Get expected vs. actual (ask if missing)
- Concrete example: specific input → **expected** output/behavior →
  **actual** output/behavior observed. Vague reports ("discounts are wrong")
  aren't enough to start — ask for one real case (which cart, which coupon,
  expected total vs. what it showed).
- What's the source of truth for "expected"? Ask the user if it's not
  obvious from existing code/comments/tests — a business rule, a ticket, or
  their own explanation of intended behavior. Don't assume.

### B2. Trace the divergence
- Start from the output and walk backward through the code path (e.g.
  checkout → total calc → discount → tax) to find exactly where the computed
  value first diverges from expected.
- Cross-check against known logic bug patterns from code-review Part 1 §3-4:
  discount applied to wrong base, tax computed before discount, coupon
  stacking, accumulation order, rounding.
- If the divergence isn't in calculation but in a different code path being
  hit than expected (wrong branch, wrong status check), trace the condition
  that routes execution there.

### B3. Confirm root cause before fixing
- Write a minimal unit test (or precise manual repro) that encodes the
  expected-vs-actual case from B1. Confirm it fails for the reason you think,
  not a different one — if it fails differently (or passes), the diagnosis
  from B2 is wrong, go back before writing any fix.
- Reuse the existing test class for that service (e.g. `CartServiceTest`)
  instead of creating a new test file — add a method to it. Only create a
  new `<Service>Test` file if none exists yet for that service.
- State the fix plan in one line: what changes, where, and what else touches
  that code path (blast radius) — before editing.

### B4. Fix
- Smallest change that makes expected behavior correct for the reported case
  and doesn't silently change other correct cases — check nearby tests still
  pass.
- Confirm the test from B3 now passes. Keep it as the regression test.

---

## Output format (both paths)

Report back:

- **Type**: Runtime crash / Logical — one line why.
- **Evidence**: stack trace excerpt or expected-vs-actual example used.
- **Root cause**: one sentence, plain language.
- **Location**: file:method/line.
- **Fix**: what changed, why it's minimal/correct.
- **Regression test**: what was added, what it proves.
- **If production incident**: mitigation taken/recommended, separate from
  the root-cause fix.

If you're not confident in the root cause after investigation, say so — don't
present a guess as a diagnosis. Ask for more evidence (logs, a specific
input, DB state) instead of shipping a speculative fix.
