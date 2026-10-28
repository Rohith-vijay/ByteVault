# ByteVault Media — Microservices Simplification & Syllabus-Verification Prompt (v2)

## Scope and grading context

This is an academic capstone project graded strictly against a fixed set of course outcomes (CO-1 through CO-5). CO-5 (Terraform, Vagrant, Docker, AWS/Azure deployment) is being tracked and reviewed separately — **out of scope for this document.** Everything here targets CO-1 through CO-4: SOA/microservices foundations, security, service discovery/gateway infrastructure, and testing.

**Do not add anything not justified by a real architectural need or a named CO.** This project was previously over-built with infrastructure (Redis, RabbitMQ, an unused Config Server, 15 services) that didn't earn its place. The correction is not "add different impressive things" — it's "keep only what does real work here."

**Priority 0 discovery has already been completed** and is the empirical source of truth for the current codebase state — see the discovery report. Do not re-assume anything Priority 0 already contradicted.

## Working method — standing rule, applies to every priority from here forward (v2: optimized for speed without sacrificing correctness)

This replaces the stricter v1 version of this rule. v1 (full workflow spec, then one module fully completed before the next begins) is correct in spirit but too slow by construction — it's sequential for services that don't actually depend on each other, and it risks discovering a contract mismatch only after a module has already been built to full depth, forcing rework of something that was supposedly "done." The goal now is explicit: **fast, correct, and accurate at the same time — and specifically, do not rebuild or restructure a module repeatedly.** The method below is designed around that, not around maximum process ceremony.

1. **Lock shared contracts once, in `common-library`, before any feature-specific work.** Security header propagation (`X-Gateway-Secret`, correlation ID), the DTO response envelope shape, and entity conventions (`BaseAuditableEntity`, no Lombok `@Data`) are already established — any *new* cross-cutting concern a feature needs must be added here first and consumed by every service that needs it from the start. This is the direct fix for the exact rework that already happened once: the gateway-secret header had to be retrofitted across five services because it wasn't locked centrally before those services were first built. Do not let that pattern repeat.
2. **For each feature or decision, write a short contract spec — not a full workflow document.** Just enough to answer: which services does this touch, what are the DTO/endpoint shapes between them, does it need anything new from step 1. This should take minutes, not be a planning exercise in itself.
3. **Prove the contract with a thin walking skeleton before deepening any module.** Wire the real endpoint shapes and real shared security patterns end-to-end across every service the feature touches, with minimal business logic — enough to prove the actual request path works. This is the cheap point to catch a contract mismatch; catching it here instead of after full implementation is the entire reason this method is faster, not slower, than building one module to full depth at a time.
4. **Once the skeleton proves the contract, deepen each module to full business logic and full test coverage (unit, integration, security, and a contract test if a Feign relationship is involved) in one pass.** Because the integration risk was already retired in step 3, this pass should be final — not provisional, not something expected to need a second pass later.
5. **Run the Priority 6 verification protocol once per feature, after all its modules are deepened** — not after every small increment, and not separately per module when the modules only make sense verified together. Checking cross-module compatibility before the feature is actually complete is wasted effort.
6. **No reactive patching.** If deepening one module reveals another module also needs a change, that becomes its own explicitly-scoped step — not an inline fix made in passing. If this happens, it means step 2's contract spec was incomplete; update it so the same gap doesn't recur.
7. **A module is only reopened for a deliberately planned, explicitly scoped future iteration** (e.g., CO-5 containerization later touching its Dockerfile) — never for ad hoc "this also needs fixing" discovery after the fact.

### Pre-flight checklist — consult before starting any new or modified module
Before writing code for any module, confirm it will use, not reinvent:
- The shared `CommonFeignConfig`/`FeignCorrelationInterceptor` for every new Feign client (not a one-off interceptor).
- The existing response envelope/error-response shape already used across the other seven services.
- `BaseAuditableEntity` and the established entity conventions for any new JPA entity.
- The existing JWT/role-check pattern at the Gateway and `DownstreamSecurityFilter` pattern downstream — do not hand-roll a new auth check.
If a module needs something not on this list, that's a signal to extend `common-library` (step 1), not improvise locally.

### Local dev environment — keep it running, don't cold-start it every verification pass
Start Eureka, the Gateway, and PostgreSQL once at the beginning of a work session and leave them running; only restart the specific service being changed. Repeated full-stack cold starts for every verification step are a direct cause of slow iteration (this is almost certainly why Priority 1–6 took as long as it did in one unbroken run). Where feasible, use Testcontainers for integration/contract tests so they don't require the full live stack to be up at all.


## Confirmed architecture decisions (do not re-litigate these)

- **Active services (7):** Identity (merged Auth+User), Product, Cart, Order, Payment, Fulfillment, **Notification**.
- **Explicitly excluded from the active runtime:** Support (not merged into Notification — different bounded contexts; a forced merge doesn't reduce real complexity, it just hides two responsibilities behind one deployable).
- **Infrastructure kept:** Eureka, Spring Cloud Gateway, PostgreSQL with database-per-service, Spring Data JPA.
- **Infrastructure removed from the active runtime:** Redis (Cart already migrated — done), RabbitMQ (for the digital purchase path — see Priority 2), Config Server (confirmed fully disconnected already).
- **Kafka: explicitly NOT implemented in this phase** — CO-1 names it, but whether it needs a running implementation or a written comparison is still unconfirmed with faculty. Do not add Kafka or any other broker until resolved separately.
- **OpenFeign: kept.** Spring Cloud library, not a separate infrastructure process; also the standard way to demonstrate CO-1's load-balancing requirement via Spring Cloud LoadBalancer.
- **Physical services (Inventory, Shipping, Warehouse):** source retained, deactivated from Eureka/Gateway in this phase (currently still active per discovery — needs disabling).

---

## Priority 1 — Identity Service (merge Auth + User) and the confirmed vendor-onboarding model

- Merge Auth Service and User Service into a single Identity Service. Replace the current asynchronous RabbitMQ registration handoff (`AuthService` publishes → `UserRegisteredEventListener` consumes, currently silently stalls if `RABBITMQ_ENABLED=false` and no broker is running) with a single synchronous transaction/service call.
- **Registration now accepts a role selection at signup: CUSTOMER or VENDOR.** This is a confirmed change from the code's current behavior.
  - CUSTOMER registration: unchanged, standard flow.
  - VENDOR registration: credentials are created with role VENDOR, **and a `vendor_profile` row is created immediately with status `PENDING`** as part of the same registration flow — not as a separate later action.
- **Two-factor auth: deferred, not built.** The `is_two_factor_enabled`/`two_factor_secret` columns carry forward unused. Do not add a 2FA endpoint this phase. The confirmed minimum auth surface is: register/login, JWT access + refresh tokens, OAuth, forgot/reset password, email verification.
- Preserve the existing Auth/User API contract at the Gateway level wherever reasonable; document any endpoint that must change.
- Follow the established entity conventions (`BaseAuditableEntity`, no Lombok `@Data` on JPA entities, explicit equality/hash handling) — same reasoning as the Cart migration.
- Run the full Priority 6 verification protocol on Identity before calling this done.

---

## Priority 1a — Product Service: vendor-status gate at both write and read time

This is a direct consequence of Priority 1's confirmed vendor model, not a separate feature request:

- **Write gate** (likely already correct per the original design): an unapproved/non-APPROVED vendor cannot create or publish products.
- **Read gate (the part to verify/add):** every public catalog listing and product-detail query must check the vendor's **current** approval status before returning that product — not rely on a value cached at product-creation time. If a previously-approved vendor is later **suspended**, their already-published products must stop appearing in public queries automatically, without any change to the product rows themselves.
- Confirm during Priority 0 follow-up whether this read-time check already exists (via the Identity internal endpoint, or a synced/denormalized flag) or needs to be added. If it needs a live Feign call per listing query, consider whether that's cached with a short TTL to avoid a Feign round-trip per catalog item — this is a performance judgment call for the agent to flag, not silently decide alone.
- The product schema's unused `PENDING_APPROVAL`/`REJECTED` per-product statuses can be left as-is or cleaned up later — the vendor-level gate above is the actual enforcement mechanism, so this is not a blocker.

---

## Priority 2 — Remove RabbitMQ from the active runtime

- Remove RabbitMQ dependencies and listener/publisher code from every currently-active service in the digital purchase path: `auth-service`/`user-service` (subsumed into Identity per Priority 1), `order-service`, `payment-service`, `fulfillment-service`.
- **Do not delete** RabbitMQ-related code in Inventory/Shipping/Warehouse — retain as future-scope source.
- **Order → Fulfillment**: discovery confirms Order Service already calls Fulfillment **synchronously and directly** (`FulfillmentClient`), in addition to publishing `order.paid` via its outbox to RabbitMQ, which Fulfillment also separately consumes (`OrderPaidEventListener`, currently handled idempotently but redundant). **Keep only the direct synchronous call; remove the RabbitMQ leg of this specific handoff** — the outbox pattern in Order Service can stay as internal reliability infrastructure for the synchronous call (retry-on-outbox-row if the direct call fails), but it no longer needs to publish anywhere external for this handoff.
- **Payment's remaining RabbitMQ events (`payment.failed`, `refund.completed` to `order.exchange`)**: still an open decision (see "Still open" section) — do not remove or convert these without an explicit answer, since it wasn't part of the confirmed decisions this round.

---

## Priority 3 — Bring Notification Service into the active runtime

- Notification Service becomes the 7th active service, scoped narrowly to **transactional/confirmation emails only** for this phase:
  - Identity → Notification: verification email on registration.
  - Order/Payment → Notification: order confirmation on successful payment.
  - Fulfillment → Notification: entitlement-ready / download-available email.
- **Must be called defensively**: a short Feign timeout plus catch-and-log on the calling side (Identity/Order/Payment/Fulfillment) — a failure or slowness in Notification (or its upstream SMTP/email provider) must never fail or delay the calling transaction. No message broker needed for this narrow scope; a direct, strictly-timed Feign call with swallowed/logged failure is sufficient.
- Register Notification with Eureka and route it through the Gateway consistent with the other six active services, including the `X-Gateway-Secret` header on its Feign clients (see Priority 4).
- Support Service stays fully out of the active runtime this phase — confirmed, not merged into Notification.

---

## Priority 4 — Security hardening (CO-2's explicit items + the gateway-secret audit findings)

Priority 0's audit found inter-service perimeter security only **partially** implemented — this is the concrete fix list, not a general reminder:

- **Systematic gateway-secret fix** — apply a single reusable `FeignClientConfig`/`RequestInterceptor` (ideally exported once from `common-library` so every service configures it the same way) across every Feign client found broken or fragile in the audit:
  - `order-service` → `ProductClient`: missing entirely.
  - `order-service` → `InventoryClient`: missing entirely.
  - `order-service` → `FulfillmentClient`: fragile (manual `@RequestHeader` parameter, no interceptor).
  - `payment-service` → `OrderClient`: partially broken (`updateOrderStatus` missing the header even though `markOrderPaid` has it).
  - `fulfillment-service` → `ProductClient`: partially broken (`getProductById` missing it, `getDownloadUrlInternal` has it).
  - Any new Feign clients introduced by Priorities 1–3 (Identity's internal endpoints, Notification's clients) must use the same shared interceptor from the start — not repeat this inconsistency.
- **CORS**: already correctly configured at the Gateway only, with downstream services correctly disabling their own CORS — no change needed, just confirm this stays true as new services (Identity, Notification) are added.
- **TLS/SSL**: confirmed fully absent. Configure HTTPS for local development (a self-signed certificate is acceptable — document that choice explicitly).
- **Explicit security tests**: for at least one representative endpoint per service, prove an unauthenticated/unauthorized request is rejected (401/403) and a properly authenticated one succeeds — CO-2 names this as its own deliverable.

---

## Priority 5 — Contract testing (CO-4's explicitly named, currently-missing category)

- Confirmed: **zero contract tests exist anywhere in the repository today** (0 Spring Cloud Contract, 0 WireMock stubs, 0 Pact contracts) — every module's tests are unit or integration only.
- Add a consumer-driven contract test for the Cart→Product Feign relationship (the best-understood, already-fixed relationship) using Spring Cloud Contract or a WireMock-stub-based equivalent.
- Document in the test README which tests are unit, integration, and contract — trivial to point to when asked directly.

---

## Priority 6 — Mandatory verification protocol (applies to every module in every priority above)

**A module is not "complete" on the strength of code review or unit tests alone.** For every module touched:

1. **Build verification** — `mvn test -pl <module>` and `mvn clean package -pl <module>`; a full `mvn clean install` across affected modules at each phase boundary. Report actual output.
2. **Service discovery verification** — confirm registration and UP status via the Eureka dashboard or `/eureka/apps`.
3. **Gateway routing verification** — a real HTTP request through the Gateway's configured route, not a direct bypass call.
4. **End-to-end auth verification** — obtain a real JWT via login (through the Gateway), use it as a Bearer token on a protected endpoint through the Gateway; confirm success with a valid token and explicit rejection with none/invalid.
5. **API documentation verification** — Swagger/OpenAPI UI reachable and accurate for the module.
6. **Postman/API-level verification** — a maintained collection covering success, auth-failure, and validation-failure cases, actually run.

**Required report format per module:** files changed → Maven build result → Eureka registration evidence → Gateway routing evidence → auth flow evidence → Swagger check → Postman run result. All six, every time.

---

## Explicit non-goals for this phase

- No Kafka, no RabbitMQ, no Redis anywhere in the active runtime path.
- No Terraform/Vagrant/Docker/cloud deployment work — CO-5 tracked separately.
- No Support Service in the active runtime, and no merging Support into Notification.
- No new microservice boundaries beyond the confirmed seven (Identity, Product, Cart, Order, Payment, Fulfillment, Notification).
- Do not activate Inventory/Shipping/Warehouse's Eureka registration or Gateway routes this phase.
- No 2FA endpoint work this phase.

## Still open — not resolved this round, do not assume an answer

1. **Physical orders with no live tracking** while Shipping is inactive — is this an acceptable gap for this phase, or does it need a minimal stub?
2. **Payment's remaining RabbitMQ events** (`payment.failed`, `refund.completed`) — convert to synchronous now, or explicitly deferred?
3. **Vendor "my earnings" view** — does this endpoint need to be built, or does it already exist somewhere not yet confirmed?
4. CO-1's SOAP-vs-REST comparison and load-balancing/clustering demonstration — documentation/presentation vs. new code, still unconfirmed.
5. Agile artifacts (Jira, sprints, standups, retrospectives) — process evidence, tracked separately, not part of this coding prompt.

---

## Priority 7 — Close the verification gaps (v3 addition — do this before touching the "Still open" decisions)

The Priority 6 report claimed several things as done that were configured or asserted but never actually demonstrated. Close these three specifically — they're engineering follow-through, not new business decisions:

### 7a — Prove TLS actually works, not just that it's configured
- Issue a real request to the Gateway over `https://` (not `http://`) and show it succeed with the configured PKCS12 keystore.
- Confirm plain HTTP is either redirected or rejected, not silently still accepted alongside HTTPS (if both are currently accepted, decide and document whether that's intentional for local dev or should be tightened).

### 7b — Exercise the outbox retry-on-failure path under a real forced failure
- Stop Fulfillment Service (or otherwise make its endpoint unreachable).
- Trigger a payment through to PAID on Order Service while Fulfillment is down — confirm the synchronous Feign call fails as expected, and the outbox row remains unpublished/pending rather than being lost.
- Restart Fulfillment Service and confirm the `OutboxPublisherService` retry mechanism picks up the pending row and successfully completes the fulfillment, without manual intervention.
- Report this as its own explicit test — this is the single correctness property that justified keeping the outbox pattern after removing RabbitMQ, and it has not yet been shown working under failure.

### 7c — Confirm the SOAP-vs-REST and load-balancing artifacts actually exist
- Locate (or produce, if it doesn't exist) the documentation/presentation material comparing SOAP and REST, and orchestration/choreography/event-driven patterns, as named in CO-1.
- For load balancing specifically: run two instances of one service (Product Service is a reasonable choice) registered under the same Eureka application name, issue several requests through the Gateway, and confirm requests are distributed across both instances (log the instance/port handling each request as evidence) — this is a concrete, demonstrable artifact, not just a configuration claim.

**Reporting requirement, same as before:** each of 7a/7b/7c needs actual evidence (request/response, logs, or a short recording) — not a restated claim that it's "configured" or "ready."

## Still open — resolved items removed, three remain

1. Physical orders with no live tracking while Shipping is inactive — accept the gap, or add a minimal stub?
2. Payment's remaining RabbitMQ events (`payment.failed`, `refund.completed`) — convert to synchronous, or explicitly deferred?
3. Vendor "my earnings" view — build it, or confirmed not needed this phase?

~~4. Kafka (CO-1)~~ — **resolved**: satisfied via the written architectural treatise, no broker code added. Do not revisit unless this document is reopened.

---

## Priority 8 — Hold for decisions, then documentation/demo readiness (do not guess the gated items)

### 8a — Hard gate: do not implement any of these without an explicit answer from the project owner
- Physical order tracking while Shipping is inactive.
- Payment's remaining RabbitMQ events.
- Vendor earnings view.

These are product decisions, not engineering ones — continuing to build around them without an answer risks work that gets thrown away. Surface this list and wait.

**Once each is answered, it follows the v2 working method above before any code is written:** write the short contract spec for that decision across every module it touches (e.g., a vendor earnings view likely touches Payment Service for the data, possibly Identity for auth/role checks, and the Gateway for routing), prove it with a thin walking skeleton across those modules, then deepen each to full business logic and full verification in one pass — not a separate full-spec-then-complete-one-module-at-a-time cycle per decision.

### 8b — Decide and lock the demo entry point (TLS vs plain HTTP)
- Priority 7a proved TLS works on :8443, but the system still also accepts plain HTTP on :8080. Confirm explicitly: does the actual demo/presentation run through :8443, or does the frontend still point at :8080?
- If :8443 is the intended demo path, update the frontend's API base URL and CORS configuration accordingly, and re-run the Priority 6 verification suite against :8443 specifically — the existing evidence was gathered via `curl` directly, not through a real frontend session.
- If :8080 remains the demo path for simplicity, document that explicitly as a deliberate choice (e.g., "TLS is implemented and verified; the live demo uses the plain-HTTP port for convenience") so it's not mistaken for an oversight when presented.

### 8c — Documentation and architecture-diagram refresh for presentation
- The architecture has changed substantially since the original 15-service design (7 active services, Redis/RabbitMQ/Config Server removed, Notification added, vendor gating added). Regenerate the system architecture diagram and README to reflect the **current, final** state — the capstone presentation should not be working from a stale diagram.
- Consolidate the three verification reports (Priority 0 discovery, Priority 6 module verification, Priority 7 gap closure) into one coherent "final architecture and verification" document suitable for direct inclusion in the capstone submission, rather than leaving them as three separate agent outputs.

### Explicitly out of scope for this priority
- CO-5 (Terraform/Vagrant/Docker/cloud deployment) — tracked and reviewed separately; do not start this here without those artifacts being brought into this document first.
- Agile artifacts (Jira, sprints, standups, retrospectives) — process evidence, not code; confirm separately whether these are being maintained, since they carry real grade weight across every CO and haven't been discussed at all in this execution track.

---

## Priority 9 — Execute the validated RED + ORANGE list (from the agent's own empirical v2 plan)

The agent's self-produced gap analysis (re-inspected against the real codebase, not prior reports) is accurate and well-sequenced. Execute it as follows, under the v2 working method:

### Execute now — RED (the actual CO-4 blocker + evidence gathering)
- **C1**: Cart→Product contract test, exactly as specified (WireMock, the three scenarios listed, in `cart-service`'s test scope). This is the one real remaining CO-4 gap — treat it as such.
- **C2, C3, C4**: these are evidence-gathering, not code changes (TLS proof, outbox-retry proof via the existing `verify_7b_outbox_retry.ps1`, multi-instance load-balancing proof). Run them, capture real output, same evidentiary bar as Priority 7.

### Execute now — ORANGE (quick, low-risk cleanup, no gated decision involved)
- **H1**: add correlation-ID propagation to `product-service`'s `UserClient` — two lines, per the pre-flight checklist, use the existing `CorrelationIdUtil` pattern already standard elsewhere.
- **H3**: actually disable Eureka registration for Inventory/Shipping/Warehouse (`eureka.client.enabled: false`) — this was specified before and wasn't actually done; verify it sticks this time by checking the Eureka dashboard shows them absent, not just that the config line was added.
- **H4**: remove `auth-service`/`user-service` from the active Maven reactor (or skip their tests via `<skip>true</skip>`) now that `identity-service` has fully superseded them — don't leave superseded modules building alongside their replacement.

### One addition before YELLOW/BLUE — check `VendorSaleResponse` before building anything new
Before treating "vendor earnings view" as an open BLUE item needing new work: inspect what `VendorSaleResponse` in Order Service already exposes. Report back whether it already satisfies a vendor-facing earnings view (gross/commission/net, filtered to that vendor) or not. This may resolve one of the three gated decisions without writing any new code — confirm, don't assume either way.

### Still deferred — YELLOW (M1-M3) and the remaining BLUE items
Documentation consolidation (M1-M3) waits until RED+ORANGE are done and evidenced, consistent with "verify once the feature is actually complete." The three gated decisions (physical tracking, payment events, and vendor earnings pending the `VendorSaleResponse` check above) still require the project owner's answer before any related code is written.

---

## Priority 10 — Close two evidence gaps found in the Priority 9 report before moving to YELLOW

Priority 9's RED and ORANGE work is real and mostly solid, but two things in its own evidence don't hold up and need re-checking before this is treated as closed:

### 10a — Re-verify the full Eureka registry shows all 8 active services
The H3 evidence listed only 6 of the 8 expected active services (API-GATEWAY, FULFILLMENT-SERVICE, ORDER-SERVICE, CART-SERVICE, IDENTITY-SERVICE, PRODUCT-SERVICE) — **PAYMENT-SERVICE and NOTIFICATION-SERVICE are missing from that list**, despite both having been started earlier in the same session. Run a fresh `GET /eureka/apps` query and confirm explicitly that all 8 active services (the six listed plus Payment and Notification) are present and UP, with instance counts shown. If either is genuinely missing, find out why before reporting this done — don't re-run the same incomplete query and assume it was a fluke.

### 10b — Check the correct artifact for vendor earnings: `VendorEarningLedger`, not `VendorSaleResponse`
`VendorSaleResponse` (Order Service) only shows gross sale amounts — it does not apply or expose the platform commission split. The project's original requirement was a 10%/90% commission/net split, tracked via `VendorEarningLedger` in **Payment Service**. Inspect `VendorEarningLedger`'s fields and any existing endpoint exposing it, and report explicitly: (a) does the commission calculation actually run on real payments/refunds, and (b) is there (or does there need to be) a vendor-facing endpoint returning net payout, not just gross sales. This — not `VendorSaleResponse` — is the real answer to the vendor-earnings gated decision.

Do not proceed to YELLOW (documentation consolidation) until 10a and 10b are both resolved with real evidence.

---

## Priority 10 outcome (recorded)

- **10a closed**: all 8 active services registered and UP; Inventory/Shipping/Warehouse/Support absent.
- **10b found a real gap**: `VendorLedger` + `VendorLedgerService` (10% fee / 90% net, unique `(vendor_id, order_id)`) and `GET /api/v1/payments/vendor/earnings` (anti-IDOR, admin override) all exist and work, but **`recordTransaction` is never called — `vendor_ledger` has 0 rows.** An earlier report's claim of "verified ledger entry creation" was incorrect.
- The older "Still open" item on vendor earnings is superseded by Priority 11 below. The "payment events" item is reframed as an audit (11d).

---

## Priority 11 — Wire the vendor ledger (first feature run under the v2 working method)

### 11a — Investigation first, no code: why is it unwired?
- Report the result of `git log -S "recordTransaction"` and `git log -S "vendorLedgerService"`: was the ledger ever credited in a previous version, and if so, which commit removed that path?
- Specifically check whether the RabbitMQ removal (Priority 2) deleted a listener or publisher in payment-service/order-service that fed the ledger. If yes, this is a regression from our own refactor — state that plainly, and treat it as a signal to check what else that removal silently dropped.
- Reconcile the earlier report that claimed ledger entry creation was verified: say what that check actually exercised.

### 11b — Contract spec (short, per working-method step 2) — recommended design, agent may propose a justified alternative
- **Direction of dependency stays one-way: Payment → Order.** Do not have Order call into Payment (that creates a Payment→Order→Payment cycle).
- Add an internal Order endpoint, e.g. `GET /api/v1/orders/internal/{orderId}/vendor-breakdown`, returning one entry per vendor: `{vendorId, grossSubtotal}` computed from that order's `order_items` (sum of line-item subtotals per `vendor_id`). Gross is item subtotals only; shipping/tax are not attributed to vendors in this phase — document that assumption.
- After Payment successfully syncs PAID to Order, it calls this endpoint and writes one `vendor_ledger` row per vendor via the existing `recordTransaction` (10% fee rounded HALF_UP at 2 dp; net = gross − fee, so fee + net == gross exactly). Multi-vendor orders produce one row per vendor.
- **Idempotency**: rely on the existing unique `(vendor_id, order_id)` constraint; a repeated mark-paid or retry must not double-credit.
- **Failure handling**: a ledger write failure must not fail or roll back the payment/order transition, but it must not be silently lost either (this is financial data, unlike an email). Log it as an error and add a small scheduled reconciliation in Payment that finds PAID transactions with no ledger rows and backfills them (safe because of the idempotent write). Keep it minimal — one job, no new infrastructure.
- **Refund path**: refunds must adjust the ledger or vendors stay credited for refunded sales. Default policy: proportional reversal (platform fee reversed proportionally with the vendor net). Because `(vendor_id, order_id)` is unique, propose the smallest schema change that supports this (e.g., a refunded-amount column or REFUNDED/PARTIALLY_REFUNDED status on the existing row) using whatever schema-management approach the project already uses — no new migration tooling.
- Use the shared `CommonFeignConfig` for the new Feign call (pre-flight checklist) and the standard envelope/security patterns.

### 11c — Walking skeleton, then deepen, then verify (v2 method steps 3–5)
1. **Skeleton**: new Order endpoint returning real shapes + Payment calling it through the shared Feign config and writing one ledger row. Prove the real request path end-to-end once before building the rest.
2. **Deepen in one pass**: refund reversal, reconciliation job, validation, and tests — unit (rounding edge cases such as 0.05 and 33.33, multi-vendor split, idempotent replay), integration, security (internal endpoint rejects requests without the gateway secret; a vendor cannot read another vendor's earnings), and a **consumer-driven contract test for Payment→Order's new endpoint** (a second contract test, alongside Cart→Product).
3. **Verification — real evidence, not claims**: make a real purchase through the Gateway of a product owned by a known vendor, then show (a) `SELECT * FROM vendor_ledger` with the correct gross/10%/90% numbers, (b) `GET /api/v1/payments/vendor/earnings` for that vendor returning non-zero values, (c) replaying mark-paid does not create a second row, (d) a refund adjusts the ledger as specified, (e) a vendor token cannot see another vendor's earnings.

### 11d — Audit payment-failure and refund paths for silent inconsistency (report before building anything)
- What currently happens to an order when payment fails or times out? Does it move to a terminal state, stay PENDING forever, or get updated via Feign? Same question for refunds end to end.
- Reconcile the contradiction: an earlier report said `payment.failed`/`refund.completed` RabbitMQ events were preserved, but a later inspection found no RabbitMQ code in payment-service. State what actually exists now.
- Report findings only. If an order can be left in an inconsistent state, propose the smallest synchronous fix as its own spec step — do not patch inline.

### 11e — Regression guard
- Add a step to `backend/postman/run-verification.ps1`: after the digital purchase step, assert the purchased product's vendor now has a non-zero ledger entry via `/api/v1/payments/vendor/earnings`. This turns "built but never wired" into a failing check instead of a silent gap.

### 11f — Credentials hygiene pass (CO-2 best practices; small, do after 11c)
- Scan the repo for hardcoded secrets: database passwords in `application.*` and in scripts (including `verify_7b_outbox_retry.ps1` and any `PGPASSWORD` usage), the keystore password, the gateway secret, JWT signing secret, and any payment-provider keys.
- Move them to environment variables with safe local defaults, add a `.env.example` documenting them, and confirm `.gitignore` excludes real values. Report anything that was already committed so the owner can rotate it.

### Order of work and what stays deferred
Do 11a and 11d (both investigations) first and report; 11b–11c proceed once 11a's finding is in, since a confirmed regression could widen the spec. YELLOW documentation consolidation waits until Priority 11 is evidenced. **Physical order tracking remains the project owner's decision** — do not build a Shipping stub without an answer.

---

## Priority 11 — outcomes of 11a/11d and amendments (these SUPERSEDE any conflicting text in 11b–11d above)

### 11a result (accepted)
`recordTransaction` was introduced in a single commit (`d475b39`) and never called from production code. It was born unwired; it was not removed by the RabbitMQ refactor. The earlier "ledger entry creation verified" claim was a unit test being mistaken for an integration check. Two cheap follow-ups to close the method gap:
1. **Protect the work.** Run `git status` / `git log`. `git log -S` cannot see uncommitted deletions, and if Priorities 1–10 are uncommitted, the whole v2 migration has no safety net. If so, commit it now to a branch (e.g. `v2-simplification`) in logical chunks, one per priority, before any further change. Keep committing per priority from here on (this also doubles as Agile evidence).
2. `git grep -n "recordTransaction" d475b39 -- "*/src/main/*"` — confirm the baseline's only production match is the definition.

### 11d result (accepted, with corrections)
- payment-service still has `spring-boot-starter-amqp` and two live `convertAndSend` calls (`payment.failed` in `PaymentController`, `refund.completed` in `RefundService`). Remove them — no consumer exists for these events any more. **Correction:** do not report this as a startup blocker unless demonstrated. Spring AMQP connects lazily and payment-service has registered UP throughout without a broker. Verify by starting payment-service with no broker running and reporting what happens. The accurate severity is: dead dependency plus a failure path that does nothing useful.
- The previous report's "no RabbitMQ code in payment-service" was wrong and the earlier "preserved" note was right; also note the Priority 6 executive summary ("RabbitMQ completely removed from the active path") was overstated. Do not copy any of these claims forward.

### Amended spec for 11b (replaces the corresponding items above)
1. **No reuse of the admin-only `updateOrderStatus` for payment-driven changes.** It requires ROLE_ADMIN and the shared Feign interceptor forwards the *calling user's* roles, so a customer-triggered call would 403 and a catch-and-log would hide it. Add a dedicated **internal** Order endpoint (secured like `mark-paid`: gateway-secret/internal filter, no end-user role) that accepts only whitelisted transitions — currently just full-refund `PAID → CANCELLED`. Prove it with a test: the call succeeds when made inside a CUSTOMER-authenticated request context.
2. **Failed payment attempt ≠ failed order.** Record the failed attempt on `PaymentTransaction` (already done) and return a clear error. The order stays PENDING and retryable — a terminal FAILED would block retry. A failed verify is client-supplied input: confirm `verifyPayment` enforces that the authenticated user owns the transaction/order before it changes anything, and add a test for that. Only if a signature-verified Razorpay webhook for failure already exists may it set an explicit failed state; do not invent one.
3. **Expire abandoned orders.** Add a small scheduled job in Order Service that moves PENDING orders older than a configurable timeout (default 30 minutes, property e.g. `app.orders.pending-expiry-minutes`) to the existing cancelled/expired terminal status. Idempotent, bounded batch size, no new statuses unless unavoidable.
4. **A refund must revoke access.** Find out whether the refund/cancel path currently revokes the customer's entitlement in Fulfillment. If not, add an internal, idempotent Fulfillment endpoint "revoke by orderId", called by Order Service when it applies the refund-driven CANCELLED transition — keeping the dependency direction Payment → Order → Fulfillment with no cycles. Rule: full refund revokes the entitlement; partial refund reverses the ledger proportionally and does not revoke.
5. **Verify before "fixing" the Feign header.** The 11d table lists `OrderClient.updateOrderStatus` as missing `X-Gateway-Secret`, but the master plan's Feign audit and the Priority 6 report show it covered by the shared interceptor. These contradict each other. Check current behavior empirically (a test asserting the headers on every `OrderClient` method). Do not add a manual header parameter on top of the interceptor.
6. Remove the AMQP dependency and both `convertAndSend` calls in the same pass. They are replaced by items 1–2, not by additional Feign calls that duplicate them.
7. **Tests required:** customer-context call to the internal endpoint succeeds; failed verify leaves the order PENDING and a retry then succeeds; non-owner cannot verify someone else's payment; expiry job; full refund → ledger reversed + entitlement revoked; partial refund → proportional ledger reversal, entitlement intact; plus consumer-driven contract tests for each new Payment→Order and Order→Fulfillment endpoint.
8. Order of work is unchanged: finalize this spec → walking skeleton → deepen once → verify with real evidence (DB rows, endpoint output, a real refund end to end).

### Documentation consolidation rule (YELLOW, when it comes)
Three earlier report claims turned out inaccurate (ledger "verified", "RabbitMQ completely removed", "no RabbitMQ code in payment-service"). The consolidated capstone document must be built only from **freshly re-run evidence**, not copied forward from earlier reports.

### Still the project owner's call
Physical order tracking while Shipping is inactive. Defaults chosen above (order stays retryable on failed attempts, 30-minute expiry, proportional refund reversal) can be overridden.

---

## Priority 11 closeout — build accepted, priority NOT yet complete (paste this section to the agent as-is)

Commit `7496461` is accepted as a **build**. It is not accepted as **complete**. Unit and contract tests passing is not completion for this priority: the original defect (ledger written, unit-tested, never called) also passed unit tests. Nothing below has been shown yet. Do not report Priority 11 done until every item has real output attached.

### A. Live verification — start Eureka, Gateway, Postgres and all active services (start payment-service fresh so the new ledger columns/statuses are exercised against the real schema)
1. **Real purchase** through the Gateway of a product owned by a known vendor. Show `SELECT * FROM vendor_ledger` for that order: gross, 10% fee, 90% net, and fee + net == gross. Show `GET /api/v1/payments/vendor/earnings` for that vendor returning non-zero values. If a second vendor's product exists, repeat with a two-vendor order; otherwise state that it was not tested.
2. **Replay** the mark-paid for the same order — show no second ledger row.
3. **Reconciler on a scheduler thread:** delete that ledger row (or insert a successful transaction with no ledger row), wait one cycle, and show it backfilled. This proves background calls authorize correctly with no user request context.
4. **Full refund:** show order → CANCELLED, ledger → REFUNDED, the entitlement revoked in `fulfillment_db`, and the download endpoint now refusing access. **Partial refund:** ledger → PARTIALLY_REFUNDED with a proportional reversal, entitlement still active.
5. **Expiry:** with `app.orders.pending-expiry-minutes` set low for the test (or a backdated PENDING order), show the order move to CANCELLED.
6. **Security:** vendor A's token cannot read vendor B's earnings (403). Internal endpoints hit directly without the gateway secret return 403. A customer-authenticated request context can still trigger the Payment→Order internal calls (this settles the `ROLE_INTERNAL_SERVICE` question for real).
7. Run `backend/postman/run-verification.ps1` in full, including the new Test 11, and paste the complete output.

### B. Tests that are missing (order-service stayed at 35 and fulfillment-service at 10 — both must grow)
Add and report new counts per module for: the expiry job; the refund-cancel transition triggering revocation; authorization on each new internal endpoint; revocation idempotency (call twice, no error, no change); a consumer-driven contract test for the Order→Fulfillment revoke endpoint; and a test that a non-owner cannot verify or fail another user's payment/order.

### C. Failed-payment semantics — the implementation deviates from the amended spec
The spec: a failed verification attempt is recorded on `PaymentTransaction` and the order **stays PENDING and retryable**. The build instead calls `markOrderFailed` (PENDING → FAILED). Resolve one of two ways and say which:
- **Preferred (matches the spec):** remove the `markOrderFailed` call and the dormant `mark-failed` endpoint; add a test that a failed verify followed by a successful verify on the same order succeeds.
- **If you believe terminal FAILED is better:** report it as a deliberate deviation with your reasoning, add the ownership test from B, and wait for the project owner's decision before keeping it.
Either way, confirm `verifyPayment` enforces that the authenticated user owns the transaction/order before it changes any state.

### D. Refund partial-failure behavior — state it, don't leave it silent
If the refund row and ledger reversal are saved but the `refund-cancel` call to Order fails, the customer is refunded while the order stays PAID and access stays active. Describe what happens today. Then either handle it (error-level log plus include "refunds whose order is not CANCELLED" in the existing reconciler) or document it explicitly as a known limitation.

### E. Reporting and housekeeping
1. **Module count:** "16 modules" was the original number. Show the actual `<modules>` list in `backend/pom.xml` and confirm auth-service and user-service are excluded (15 expected).
2. **Commit contents:** show `git log --oneline` and `git show --stat 7496461`. If Priorities 1–10 were folded into this one commit, say so.
3. **Credentials (finish 11f):** report which files held hardcoded secrets (file names only, never values). Replace hardcoded fallbacks in application config and scripts with environment variables. Search git history for the hardcoded DB password (`git log -S`) and report whether it is committed; the project owner decides on rotation.
4. **Spec location:** save this prompt document in the repo (e.g. `docs/agent-prompt.md`) and read it from disk. Do not reconstruct it from transcript logs.

### Reporting format
For A1–A7 and B–E: the actual command output or file diff for each. "Implemented" or "tests pass" alone does not count.

### Still deferred
Documentation consolidation (from freshly re-run evidence only), and the owner's decision on physical order tracking.

---

## Priority 11 — final fixes, then Priority 12 (documentation + demo pack)  (paste this section to the agent as-is)

### Accepted from the closeout report (real evidence was shown)
Multi-vendor ledger split (10/90, fee + net == gross), reconciler backfill on a scheduler thread, full and partial refund behavior, entitlement revocation with download refused, order expiry, anti-IDOR on earnings, perimeter 403s, ownership check on payment verification, failed-payment semantics per spec, new tests (order 47, fulfillment 14, payment 42), 15-module reactor, and the checkpoint commit of Priorities 1–10. Do not redo any of these.

### Decision recorded: physical fulfillment is future scope
No Shipping stub. Inventory/Shipping/Warehouse stay in source, inactive. The demo seed data must be digital-only. Document this limitation; do not build around it.

### F — Final fixes (small; do all of these before Priority 12)
1. **Fix the regression guard (Test 11 in `run-verification.ps1`).** It currently passes with `Gross: 0, Net: 0, Orders: 0`, which is exactly the original bug. Step 9 must run the purchase through Payment's verify path (as `verify_priority11_live.ps1` does), not Order's internal mark-paid, and Test 11 must query the vendor who owns the purchased product and **fail** unless gross > 0 and net == 90% of gross. Prove the guard works by showing it fail against a deliberately broken run (e.g. the old mark-paid-only step), then pass against the real one. Also fix the step counter ("[x/10]" then "[11/11]").
2. **Credentials (finish it).** Report which files still contain the DB password in the working tree (file names only, never values). Replace every hardcoded value and fallback with environment variables (`DB_PASSWORD`, etc.). Delete the scratch scripts (`test_psql.ps1`, `test_step.ps1`, `test_reconciler_live.ps1`) or parameterize them; keep `verify_priority11_live.ps1` only if it reads the password from the environment. Confirm `git status` shows no file containing a real secret before the next commit. The password is already in history (commits `0518268` and `5fb17b7`): the owner will rotate it locally; do not rewrite history.
3. **OAuth.** Show what `/api/v1/auth/oauth` does today with a made-up token. If it issues a session without verifying a Google ID token (signature, audience, issuer, expiry), either implement real verification or disable the endpoint by default behind a property, and add a security test that a forged token is rejected. Do not leave it as "unverified, known gap."
4. **Refund endpoint authorization and the refund reconciler.** State who may call refund (admin only, or define it) and add a test that others get 403. Then prove the retry path live once: stop order-service, issue a refund, confirm `REFUND_PENDING`, restart order-service, confirm it reaches `REFUND_SYNCED` and the order is CANCELLED with the entitlement revoked.
5. **Ledger-level idempotency test.** The A2 replay hit verify's early-return and never reached the ledger. Add a test that calls `recordTransaction` twice for the same vendor/order (and, if feasible, the reconciler-vs-sync race) and shows exactly one row and no exception.
6. **Payment simulator safety.** Confirm the simulator (`order_mock_...`) cannot skip signature verification when real Razorpay keys or a non-dev profile are configured, and that it is dev-only by default. State the result plainly; it will be disclosed in the documentation.
7. **Housekeeping.** Commit the closeout changes in logical commits on a branch (not one giant commit). Consolidate startup into one documented path (`start-active-stack.js` or `start-all.ps1`) and remove or clearly mark the others. Replace `docs/agent-prompt.md` with the full current prompt document supplied by the owner; do not keep a reconstruction.

### Priority 12 — Documentation and demo pack, from FRESH evidence only
**Ground rule:** every number and claim in the final documents comes from a command run during this priority. Four earlier report claims turned out wrong (ledger "verified", RabbitMQ "completely removed", "no RabbitMQ code in payment-service", the Test 11 guard passing on zeros). Do not copy numbers forward from earlier reports (for example, payment's "prior count" appeared as 31, 32 and 37 in different reports).

Deliverables in `docs/`:
1. **Architecture document:** diagram (7 services + Eureka + Gateway on :8080/:8443 + PostgreSQL per service; synchronous Feign arrows; no broker), service responsibilities, data ownership table, Customer/Vendor/Admin workflows, and a decisions log explaining why (Redis→PostgreSQL for Cart per CO-3, RabbitMQ and Config Server removed, OpenFeign kept, Kafka covered by the treatise, Support excluded, Notification called defensively, physical fulfillment future scope).
2. **CO coverage matrix (CO-1 to CO-4):** each outcome mapped to concrete evidence files or commands. List CO-5 as tracked separately.
3. **Test report:** fresh `mvn test` output per module, classified as unit / integration / contract / security, with counts. List all contract tests (Cart→Product, Payment→Order, Order→Fulfillment).
4. **Evidence pack** under `docs/evidence/` (timestamped text files, secrets redacted): Eureka registry showing 8/8, the full verification suite passing with the fixed guard, HTTPS on :8443, two-instance load balancing, the outbox failure/recovery run, ledger/refund/expiry checks, and the OAuth forged-token rejection.
5. **Demo script (about 10 minutes):** exact steps in order — start the stack, show Eureka and Swagger, register a vendor, admin approves, vendor publishes, customer buys, entitlement and download, vendor earnings, refund and revocation, TLS, load balancing, kill Fulfillment and show outbox recovery. Include expected output per step and a fallback if a step fails.
6. **README:** prerequisites, `.env.example` variables, the single startup path, ports and URLs, troubleshooting.
7. **Known limitations and future scope:** physical fulfillment (services retained, inactive), Support Service excluded, 2FA columns unused, payments run on a simulator and how real Razorpay keys plug in, vendor-status cache means a suspension can take up to 60 seconds to hide products, pending orders expire after 30 minutes, Kafka covered only in the treatise.

### Out of scope here
CO-5 (Terraform/Vagrant/Docker/cloud) and the Agile artifacts are tracked separately and are not part of this prompt.
