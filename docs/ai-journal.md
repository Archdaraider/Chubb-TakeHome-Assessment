# ai working journal

this is a factual record of how ai was directed, checked, challenged, and constrained during the assessment. times use singapore time. entries are written after the work they describe and are not backdated.

## 2026-08-02 17:07

### goal

choose a clean java direction before generating application code.

### prompt summary

asked ai to reassess the existing backend work for a full java rewrite, use the original assessment brief as the source of requirements, compare a modular monolith with simpler crud and microservice options, and plan small approval-gated steps. required ai to stop after every step so decisions and implementation details can be reviewed or changed before continuing.

### ai suggestion

start a fresh repository using java 21, spring boot, maven, jpa, flyway, h2, junit, mockmvc, optimistic locking, and a transactional outbox. keep one deployable service with domain, application, persistence, and api boundaries. retain the focused claim lifecycle rather than expanding into a full insurance platform.

### decision

accepted the fresh java repository and modular monolith. rejected a line-by-line port of the earlier .net structure, a basic database crud design, and multiple services. required real timestamps, lower-case commit messages, separate permission before each implementation step, and separate review before every commit and push.

### codex skills used

- `superpowers:using-superpowers` identified the required workflow before repository work.
- `superpowers:brainstorming` forced architecture options, scope limits, an approved design, and a review gate before code generation.
- `documents:documents` guided structural reading of the original backend brief. native visual rendering was unavailable because neither word nor libreoffice is installed.
- `github:github` checked that the target repository was empty, public, and available to the connected account before cloning it.
- `superpowers:systematic-debugging` traced an access-denied cleanup failure to read-only git objects instead of retrying broad deletion commands.
- `superpowers:verification-before-completion` requires fresh evidence before each commit or completion claim.

### check

the backend brief explicitly permits java with spring boot. official documentation was checked for the java and spring baseline. the new github repository was confirmed empty and public before cloning. no application code, commit, or push was produced in this step.

## 2026-08-02 21:19

### goal

turn the approved design into a test-first file plan before creating the spring project.

### prompt summary

asked ai to map every design requirement to an exact file, public interface, failing test, minimal implementation step, verification command, and permission gate. required the plan to use spring boot 4 artifact names, keep the domain free of framework annotations, include claimant-supplied information, prove transaction rollback and stale-write behavior, and stop before generating application code.

### ai suggestion

split execution into a health baseline, pure claim rules, persistence and evidence, transactional commands, operational queries, rest api, demo material, and a final clean gate. use the spring boot 4 `webmvc` and `flyway` starters, an apache maven wrapper, and spotless bound to `verify`.

### decision

accepted the eight task sequence and the explicit ports between the domain, application, and persistence packages. kept command and query services separate so each future approval step has a clear test boundary. required inline execution with no subagents because the repository owner wants to review and inject changes after every step.

### codex skills used

- `superpowers:writing-plans` required exact paths, interfaces, red-green commands, small task boundaries, and a self-review against the approved design.
- `superpowers:verification-before-completion` required a fresh structure, coverage, content, whitespace, and repository check before presenting the plan as ready for review.

### check

official spring boot 4.0.7 documentation confirmed the non-deprecated `spring-boot-starter-webmvc`, jpa, flyway, actuator, validation, and test starters. apache documentation confirmed wrapper plugin 3.3.4. springdoc and spotless release documentation confirmed the planned plugin versions. no java source, build file, wrapper, commit, or push was created during planning.

## 2026-08-02 22:07

### goal

create the smallest runnable java service with a real health check and a repeatable local build.

### prompt summary

asked ai to execute the approved plan without more permission stops while keeping changes in meaningful commits. constrained the work to java 21, spring boot 4.0.7, a local ignored toolchain, the maven wrapper, test-first behavior, lower-case messages, real timestamps, and no manufactured delays or backdated activity.

### ai suggestion

download temurin 21 from adoptium, verify its published sha-256, generate only the standard wrapper assets through spring initializr, then write a black-box health test before the spring application. use actuator for health and keep both local and test database configuration ready for the later flyway migration.

### decision

accepted temurin 21.0.12+8, wrapper 3.3.4 with maven 3.9.16, and the spring boot baseline. when the first initializr request returned `400`, checked current metadata and found that the generator requires `4.0.7.RELEASE` and dependency id `web`; used those values only to obtain wrapper assets while retaining `spring-boot-starter-webmvc` in the reviewed project pom.

### codex skills used

- `superpowers:executing-plans` kept this slice aligned with the written file and command contract.
- `superpowers:test-driven-development` required the health behavior to fail before `ClaimsApplication` existed and pass only after the minimum application and configuration were added.
- `superpowers:systematic-debugging` traced the wrapper request failure to invalid current initializr parameter values before retrying.
- `superpowers:verification-before-completion` required fresh test, format, and package evidence before the slice could be committed.

### check

the jdk archive matched sha-256 `9ba963ee2371874a74185d18bc7bb2ab9407df7683300855ed7606e0662321d0`. the red run executed one test and failed because no `@SpringBootConfiguration` existed. after adding two production classes and two yaml files, the focused health test passed. `mvnw.cmd spotless:apply` and `mvnw.cmd verify` both completed successfully, producing `target/claims-service-0.0.1-SNAPSHOT.jar`.

## 2026-08-02 22:10

### goal

make the claim lifecycle explicit and test it without spring, json, or database concerns.

### prompt summary

asked ai to turn the approved state diagram into a pure java aggregate. required stable lower snake case rule codes, normalized market and currency codes, one assigned officer, claimant-only information, reasons for consequential decisions, immutable snapshots, and an explicit list of allowed state and action pairs.

### ai suggestion

use records for submission, snapshot, and change data, small enums with lower camel case external values, and one aggregate as the only place where state changes. suggested treating claimant information as an audited event that leaves the claim in `moreInformationRequired` until the officer explicitly resumes review.

### decision

accepted the explicit transition table and the separate claimant event. rejected an automatic return to review after claimant information because it would hide the officer handoff and make the workflow less clear. kept `decisionReason` only for approval or rejection; the information request reason stays on its timeline change.

### codex skills used

- `superpowers:executing-plans` kept the public methods and stable codes aligned with the agreed domain contract.
- `superpowers:test-driven-development` split the work into missing-type red, intake and assignment green, lifecycle red, and full lifecycle green.
- `superpowers:verification-before-completion` required the focused domain test and the full maven gate before commit.

### check

the first `mvnw.cmd -Dtest=ClaimTest test` failed compilation with the expected missing domain types. the intake and assignment subset then passed 9 parameter-expanded tests. the full suite next ran 18 tests with 6 expected lifecycle errors. after implementing only the five allowed transitions, all 18 domain tests passed. the final full verification also includes the earlier health test.

## 2026-08-02 22:16

### goal

persist claims while keeping an ordered audit trail, a publishable outbox record, and safe concurrent updates.

### prompt summary

asked ai to design the storage boundary from failure cases first: missing schema, full snapshot round-trip, timeline ordering, valid outbox json, open queue and market filtering, and two writers using the same version. required flyway ownership of the schema, hibernate validation, nullable new-aggregate versions, and no jpa entities outside the adapter package.

### ai suggestion

use three linked tables and one evidence append that writes both timeline and outbox records. use `EntityManager.persist` only when the domain version is null and `merge` with the exact detached version otherwise. serialize a small stable outbox envelope with claim id, event type, resulting status, and occurrence time.

### decision

accepted the three-table transaction-ready design and optimistic locking. kept spring boot's managed h2 version even though flyway logs that h2 2.4.240 is newer than its latest verified 2.3.232, because migration and validation pass and pinning an older transitive version only to hide a warning would weaken the managed dependency baseline. corrected one test that assumed insertion order for identical submitted timestamps; the contract orders by time and does not invent an order for ties.

### codex skills used

- `superpowers:test-driven-development` required a missing-table red before the migration and a missing-adapter red before jpa code.
- `superpowers:systematic-debugging` traced the one queue failure to equal test timestamps and used the smallest test correction without changing production behavior.
- `superpowers:verification-before-completion` requires the focused persistence suite, full build, formatting, and diff checks before commit.

### check

the schema test first failed with h2 `table "claims" not found`, then passed after flyway applied version 1. the adapter contract then failed compilation because `ClaimStore` and `ClaimEvidenceStore` did not exist. after implementation, 6 persistence tests passed, including a stale second save rejected by spring's optimistic locking exception. json was parsed back with jackson 3 and all required fields were asserted.

## 2026-08-02 22:20

### goal

make every accepted claim command update business state, timeline, and outbox data as one transaction.

### prompt summary

asked ai to test the application boundary through real spring transactions and h2 storage. required exactly one timeline row and one outbox row per accepted command, stable not-found and conflict codes, no evidence on rejected domain rules, and a forced evidence failure after a claim save to prove the whole update rolls back.

### ai suggestion

keep command records small and let one service coordinate the aggregate and the two storage ports. use a test-only primary evidence decorator with a controllable failure switch; it delegates normally for the behavior tests and throws after the claim save only for the rollback proof. translate only stale persistence writes and leave domain rules unchanged.

### decision

accepted one outer `@Transactional` boundary per public command and the test-only failure decorator. rejected separate transactions for claim, timeline, and outbox because that could leave an updated claim without its audit or integration evidence. retained the real adapter stale-write test from the storage slice and added a focused application test for the stable `claim_conflict` translation.

### codex skills used

- `superpowers:test-driven-development` required the nine command scenarios to fail compilation before command records or service code existed.
- `superpowers:executing-plans` kept each accepted operation to load or create, apply one change, save, append evidence, and return.
- `superpowers:verification-before-completion` requires rollback counts, focused service tests, and the full build before commit.

### check

the red command test failed with 22 missing service and command symbols. after implementation, all 9 command tests passed. the forced evidence exception left the assignee null, the claim version at zero, and both evidence tables at one existing submission row. a rejected domain rule also left the stored snapshot and evidence counts unchanged.

## 2026-08-02 22:24

### goal

provide reviewer-friendly claim details, an open work queue, and market exposure without mixing currencies.

### prompt summary

asked ai to seed every lifecycle state across singapore and australia, then test detailed history, missing claims, unfiltered and filtered queues, market isolation, open-state filtering, separate currency groups, and exposure reduction after approval. required database filtering before in-memory grouping and deterministic output ordering.

### ai suggestion

return purpose-built read records instead of persistence entities. query only open rows through `ClaimStore`, sort queue records by submission time, group exposure with a `TreeMap` by currency, and sum each group with `BigDecimal`. keep the detailed view as one snapshot plus its ordered timeline.

### decision

accepted currency-specific exposure rows and open-only queue semantics. while testing, a `100` input returned from the decimal column as `100.00`; fixed the source boundary by storing all accepted losses at two decimal places and rejecting more than two meaningful decimals. rejected a test-only numeric comparator because it would hide inconsistent snapshots and silent database rounding.

### codex skills used

- `superpowers:test-driven-development` required the query contract to fail on 10 missing read symbols before the service was added, then kept the money mismatch as a failing regression until fixed.
- `superpowers:systematic-debugging` traced the snapshot mismatch to `BigDecimal` scale across the `decimal(19,2)` boundary.
- `superpowers:verification-before-completion` requires the focused query and domain tests plus the complete build before commit.

### check

the first query run failed compilation as planned. the initial implementation passed 5 of 6 tests; the remaining failure showed `100` versus `100.00`. after canonical money validation, 6 query tests and 19 domain tests passed together. singapore exposure was `AUD 300.00 / 1` and `SGD 300.00 / 2`; approving the `SGD 200.00` claim reduced that group to `SGD 100.00 / 1` without changing the aud group.

## 2026-08-02 22:28

### goal

expose the full claim workflow as a small, documented json api with stable client-safe errors.

### prompt summary

asked ai to drive the rest boundary with raw json and mockmvc rather than serializing java request objects. required all command and read routes, 201 plus location for intake, lower camel enum values, validation, stable 400/404/409 problem codes, filtered queue and exposure responses, and openapi coverage of every public path.

### ai suggestion

keep json annotations and validation outside the domain. map strings to domain enums with case-insensitive helpers, return string-based response records, and centralize exceptions as spring `ProblemDetail` values that include a stable code but no stack trace or database message. use claimant and officer ids as explicit demo identity inputs only, not as authentication.

### decision

accepted seven public route shapes and a single exception policy. chose 409 for lifecycle and optimistic conflicts, 404 only for missing claims, and 400 for malformed, validation, action, or query inputs. added maximum lengths at the api boundary to match storage limits. retained the sprint identity shortcut and documented that production authentication and authorization remain future work.

### codex skills used

- `superpowers:test-driven-development` required eight black-box api tests before controller code; the real red run reached spring and failed all eight on missing routes or openapi paths.
- `superpowers:systematic-debugging` corrected invalid one-line java text-block syntax before accepting the behavior red, so a test-source mistake was not confused with an application failure.
- `superpowers:verification-before-completion` requires focused api, openapi, full suite, formatting, and response checks before commit.

### check

after the test syntax correction, the red run had 8 failures: required endpoints returned 404 and openapi lacked the paths. after implementation, all 8 api tests passed. the full workflow produced seven ordered events from submission through approval, problem responses exposed the expected stable codes, exposure stayed currency-separated, and `/v3/api-docs` listed claims, assignment, actions, information, queue, and exposure routes.
