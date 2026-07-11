---
name: code-review
description: Reviews Java/Spring Boot ecommerce backend code (CartService, OrderService, ProductService, and related controllers/repositories) for bugs (race conditions, null pointer risks, business logic errors, security/injection risks), naming/structure/code standards, and clean code practices. Asks before applying any fix, then offers a CodeRabbit CLI second-pass review. Use when the user asks to review, audit, or check code quality/style/bugs/security in the cart, checkout, order, or product modules. Does NOT commit/push/open a PR — for that, use the ship skill once review is clean.
---

# Code Review

Review only. Never touches git (no branch switch, no commit, no push, no PR)
— that's the **ship** skill's job, once this one is clean.

## Part 1 — Review

When reviewing code, check specifically for the following categories. For each
issue found, report: **file/method**, **category**, **why it's a problem**, and
**a concrete fix**.

### 1. Race conditions
- In OrderService.checkout(): stock is decremented via a direct read-modify-write
  (`item.getProduct().getStockQty() - item.getQuantity()`) with no locking
  (`@Version`, pessimistic lock, or atomic DB update) and no re-validation at
  decrement time. Check whether concurrent checkouts referencing the same
  product can both pass through and drive stock negative.
- In CartService.addItem(): the stock check (`product.getStockQty() < quantity`)
  happens well before the actual decrement in checkout — flag this time-of-check
  vs. time-of-use gap even if each method looks correct in isolation.
- Optimistic-lock failures (`@Version` conflicts / `OptimisticLockException`):
  confirm these are caught and turned into a clean retry or a client-facing
  "stock changed, try again" error, not a raw 500.

### 2. Null pointer risks
- Any chained getter access on nested objects (address, customer, shipping
  info) without a null check, especially in checkout() or order confirmation
  paths reachable when a customer has no saved address.
- `.orElseThrow()` vs `.orElseGet()` usage — confirm the right one is used per
  context (e.g. getCart creates a cart if missing; getOrderForCustomer should
  throw, not silently return null).

### 3. Business logic errors
- Discount/coupon logic: confirm discount is applied to the correct base
  (pre-tax vs post-tax subtotal) and that tax, if present, is calculated on
  the discounted total, not the original total.
- Coupon stacking: confirm a coupon can't apply on top of an already-discounted
  price without explicit business approval.
- Total calculation in OrderService.checkout(): confirm `total` accumulates
  correctly across all cart items before any discount/tax step is added.

### 4. Money handling
- Confirm all money fields and calculations use BigDecimal (not double/float),
  including any new discount or tax fields added.
- Confirm consistent scale/rounding mode (e.g. `setScale(2, RoundingMode.HALF_UP)`)
  is applied at the point money is persisted or returned to the client, not
  left to default/varying precision across services.

### 5. Transactional integrity
- OrderService.checkout() is @Transactional — confirm stock decrement, order
  save, and cart clear all happen inside that boundary, and that no external
  call (e.g. payment gateway) is included inside it in a way that holds the
  transaction open too long.
- Checkout/payment endpoints: confirm an idempotency mechanism (idempotency
  key, unique constraint, or "already processed" check) prevents a duplicate
  network retry or double-click from creating two orders or charging twice.

### 6. Authorization
- OrderService.getOrderForCustomer() already checks `order.getCustomerId().equals(customerId)`
  — confirm this check isn't bypassed anywhere else that fetches an order by ID
  directly (e.g. in OrderController).
- Any endpoint taking an ID (order, cart, address) from the request path/body:
  confirm ownership is re-checked server-side, not inferred from a client-
  supplied customerId field (IDOR risk).

### 7. Empty/invalid state handling
- Confirm checkout() properly rejects empty carts (already present — verify
  it isn't accidentally removed or weakened by other changes).

### 8. Input validation & injection risk
- Controller DTOs use Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@Min`,
  `@Positive` on quantity/price fields) rather than relying on service-layer
  checks alone — flag any endpoint accepting a request body with no validation
  annotations.
- No raw/concatenated SQL (`createNativeQuery` with string concatenation,
  JPQL built via string formatting) — must use parameter binding
  (`@Param`, `?1`, named params). Flag anything else as SQL injection risk.
- No mass assignment: request DTOs shouldn't map 1:1 onto entities in a way
  that lets a client set fields like `id`, `customerId`, `status`, `price`
  that should be server-controlled.

### 9. Error handling & API contract
- Exceptions map to correct HTTP status via a `@ControllerAdvice`/
  `@ExceptionHandler` (404 for not found, 400 for bad input, 409 for stock/
  version conflicts, 403 for authorization) — flag anything returning a bare
  500 for an expected business error.
- No stack traces or internal exception messages leaked into the HTTP
  response body (info disclosure).
- Error response shape is consistent across endpoints (same error DTO/fields).

### 10. Logging & sensitive data
- No PII, passwords, tokens, card numbers/CVV, or full addresses logged —
  flag any `log.info/debug(...)` that includes a full request/entity dump
  on checkout/payment/customer paths.
- Appropriate log level (don't log expected business exceptions — e.g. empty
  cart, invalid coupon — at ERROR; reserve ERROR for unexpected failures).

### 11. Query performance
- Watch for N+1 queries: iterating a collection and calling a lazy-loaded
  association per item (e.g. `cart.getItems()` then `item.getProduct()` in a
  loop without a fetch join/`@EntityGraph`) inside checkout or list endpoints.
- List/search endpoints (products, orders) return paginated results
  (`Pageable`), not an unbounded `findAll()`, once data volume matters.

### 12. Configuration & secrets
- No hardcoded secrets, API keys, DB credentials, or environment-specific
  URLs in source — must come from `application.yml`/env vars/config server.

### 13. Test coverage
- New or changed business logic (discount math, stock decrement, checkout
  flow) has a corresponding unit or integration test covering the new
  behavior and at least one edge case (empty cart, insufficient stock,
  concurrent checkout if feasible). Flag logic changes shipped with no
  test changes at all.

### 14. Naming conventions
- Classes: PascalCase and descriptive (e.g. `OrderService`, not `OrderSvc` or `Mgr`).
- Methods/variables: camelCase, verb-first for actions (`getCart`, `addItem`,
  `removeItem`) — flag ambiguous names like `process()`, `handle()`, `doStuff()`.
- Booleans: prefixed with `is`/`has`/`can` (e.g. `isEmpty`, `hasStock`).
- Constants: `UPPER_SNAKE_CASE`.
- DTOs vs entities: confirm consistent suffixing (`OrderDto` vs `Order`) so
  the two aren't confused at a glance.
- No abbreviations that aren't domain-standard (`qty` is fine/established in
  this codebase; avoid introducing new inconsistent ones like `cnt`, `amt`
  unless already a convention).

### 15. Code standards & structure
- Consistent use of constructor injection (already the pattern in this
  codebase) — flag field injection (`@Autowired` on fields) if introduced.
- Consistent exception handling: confirm exceptions map to the existing
  patterns (`NoSuchElementException`, `IllegalStateException`, `SecurityException`)
  rather than introducing ad hoc new exception types without reason.
- Layering: controllers should not contain business logic — that belongs in
  services; services should not directly build HTTP responses.
- Repository methods should stay data-access only (no business rules leaking
  into repository/query layer).
- Consistent use of Optional for "may not exist" lookups, and consistent
  handling of nulls vs Optional across the same layer.
- Package structure follows the existing convention (e.g. by feature/domain,
  not a grab-bag `util`/`misc` package) — flag new classes dropped in the
  wrong package or layer.

### 16. Clean code
- Method length/complexity: flag long methods that should be broken into
  smaller named steps (readability over cleverness).
- No duplicated logic across services (e.g. repeated "find cart or create
  cart" logic — should be a single reusable method, as it already is in
  CartService.getCart()).
- No magic numbers/strings (discount percentages, tax rates, status codes)
  — should be named constants or config values.
- Single Responsibility: each service method should do one clear thing;
  flag methods that mix calculation, persistence, and validation without
  clear separation.
- Meaningful comments only where logic isn't self-explanatory — flag
  comments that just restate the code, and flag complex logic that has
  no comment at all.

### 17. Spring Boot & JPA best practices
- Controllers must not return `@Entity` objects directly (`Cart`, `Order`,
  `Product`, `CartItem`) — map to a DTO. Returning an entity risks serializing
  a lazy Hibernate proxy outside the transaction (`LazyInitializationException`)
  and leaks persistence-layer shape into the API contract.
- Any `@OneToMany`/`@ManyToOne` added or changed: confirm fetch type is
  deliberate (`LAZY` unless a real reason for `EAGER`) and cascade is scoped
  to genuinely owned children (`CascadeType.ALL` + `orphanRemoval` is correct
  for `Cart.items`/`Order.items` since items can't outlive their parent —
  flag it if applied to a non-owned/shared reference like `Product`).
- Confirm `@Version` fields (e.g. `Product.version`) are actually exercised —
  the optimistic-lock check only works if the update path goes through
  JPA's managed entity (`save()`/dirty-checking), not a native/bulk update
  that bypasses versioning.
- Lombok on entities: this codebase uses `@Getter @Setter @NoArgsConstructor`
  only — flag `@Data`, `@EqualsAndHashCode`, or `@ToString` added to an
  `@Entity` with bidirectional relations (`Cart`↔`CartItem`,
  `Order`↔`OrderItem`) — generates recursive `equals`/`hashCode`/`toString`
  → stack overflow. If entity equality is needed, it must be ID-based and
  hand-written, not Lombok-generated field-based.
- Bidirectional back-references must stay `@JsonIgnore`'d (as `CartItem.cart`
  already is) — flag any new back-reference field missing it (circular JSON
  serialization).

### 18. Bean scope & statelessness
- `@Service`/`@Component` beans are singletons by default. Flag any mutable
  instance field on a service class used to hold per-request or per-customer
  state — that state is shared/overwritten across concurrent requests. All
  per-call state must be method params/locals/return values, never instance
  fields (distinct from thread-safety of *shared data*, which is category 1 —
  this is thread-safety of the *service object itself*).

### 19. Configuration & environment
- Flag hardcoded environment-specific values (URLs, timeouts, limits) in
  Java code that belong in `application.yml`/`application-{profile}.yml`.
- If a feature accumulates 3+ related `@Value` injections, flag it as a
  candidate for a single typed `@ConfigurationProperties` class instead —
  easier to validate and test.
- If profile-specific config exists (`application-dev.yml`, `-prod.yml`,
  etc.), confirm no dev-only secrets/relaxed settings leak into a prod
  profile and vice versa.

### Review output format

Summarize findings as a checklist grouped by category, then a table.

**Checklist (per category, pass/fail with note):**
- [ ] Race conditions
- [ ] Null pointer risks
- [ ] Business logic errors
- [ ] Money handling
- [ ] Transactional integrity
- [ ] Authorization
- [ ] Empty/invalid state handling
- [ ] Input validation & injection risk
- [ ] Error handling & API contract
- [ ] Logging & sensitive data
- [ ] Query performance
- [ ] Configuration & secrets
- [ ] Test coverage
- [ ] Naming conventions
- [ ] Code standards & structure
- [ ] Clean code
- [ ] Spring Boot & JPA best practices
- [ ] Bean scope & statelessness
- [ ] Configuration & environment

**Detailed findings table:**
Severity | Category | File/Method | Issue | Fix
Order by severity (Critical > High > Medium > Low).

If the checklist comes back fully clean (no findings at all), say so plainly
and skip straight to the CodeRabbit offer in Part 3 — don't invent an ask-to-fix
step with nothing to fix.

## Part 2 — Ask before fixing

**Never auto-fix.** After presenting the findings table, stop and ask the user
directly, e.g.:

> "Found N issues (X critical, Y high, Z medium, W low). Want me to fix these?
> All of them, or just critical/high?"

Wait for an explicit answer before changing any code. Respect the scope they
pick:
- **All** — fix everything in the table.
- **Critical/High only** — fix those, leave Medium/Low listed as-is (carry them
  forward to Part 3/ship notes as still-open).
- **Specific ones** — fix only what they point at.
- **None right now** — skip fixing, go straight to Part 3 (a second CodeRabbit
  pass on unfixed code is still useful, and is the user's call, not yours).

When fixing:
- Fix the smallest correct change per finding — no drive-by refactors beyond
  what the finding calls for.
- Re-check the fixed area against the same Part 1 category before moving to
  the next finding.
- After all requested fixes are applied, report back what changed per finding
  (one line each) and what's still open (if anything was deliberately left).

## Part 3 — CodeRabbit second pass (mandatory checkpoint, do not skip)

**Hard gate: this question must be asked and answered before this skill ends
— even if the user already said "ship it", "ok now push", or otherwise
signaled they're done.** A "ship it" that arrives before this question has
been asked is answering the *wrong* question — treat it as "yes, and once
you're through this step, go ahead and ship" (see below), not as permission
to skip straight to git. Never silently proceed past this part.

Immediately after Part 2 concludes (fixes applied, or user chose not to
fix), before anything else, ask:

> "Want me to run `coderabbit review` for a second-pass AI review before you
> ship this?"

If yes:
1. Run `coderabbit review --plain` (or `cr review --plain`) from the repo root
   against the current changes.
2. Present its findings plainly — don't filter or reinterpret them.
3. If it surfaces new issues, loop back to Part 2's ask-before-fix pattern for
   those findings specifically (same rules: ask scope, don't auto-fix).
4. If it comes back clean, say so.

If the user's reply to *anything* in Part 2 (or earlier) already included
"ship it" / "push it" pre-emptively, don't treat that as skipping this
question — ask it anyway, then proceed to ship once it's answered. The only
way this step is skipped is an explicit "no" / "skip coderabbit" in direct
response to the question above.

Once resolved (declined, or ran clean, or its findings are resolved), close
with:

> "Review done. Say 'ship it' when ready to commit, push, and open a PR."

(That hands off to the **ship** skill — this skill stops here regardless of
outcome; it never runs git commands itself.)
