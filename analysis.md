# assessment analysis

this file records the decisions, evidence, and tradeoffs behind the submitted backend. it gives reviewable conclusions rather than private chain-of-thought.

## assessment choice

the backend assessment is the strongest fit for a java submission because it can show business rules, data integrity, api design, testing, and safe use of ai in one small system. a broad insurance platform would be hard to finish and hard to explain. the chosen slice proves one claim from intake through review to approval or rejection.

## brief alignment

the implementation follows the assessment's request to "decide what matters most and build that well." it also provides each named deliverable: a "git repository with meaningful commit history," a "working application that can be started locally," an "ai working journal," and supporting documentation for the panel walkthrough.

the brief says the platform uses "both rest/http and kafka" and asks the candidate to decide which operations use each. this submission uses rest for immediate claim commands and reads, then uses a transactional outbox as the durable boundary for later broker delivery. the current relay is intentionally in-process and log-only, so the repository demonstrates the boundary without claiming that kafka delivery exists.

## options considered

| option | benefit | cost | decision |
| --- | --- | --- | --- |
| simple database crud | fastest first endpoint | lifecycle rules leak into controllers and stale writes are easy to miss | rejected |
| several microservices | shows distributed patterns | adds deployment, messaging, and failure cases before the core workflow is sound | rejected |
| modular monolith | one process and transaction with clear internal boundaries | requires deliberate package mapping | accepted |

the modular monolith gives the assessment one command to run while keeping domain, application, persistence, and api concerns separate. a service can be extracted later if team ownership or scaling creates a real boundary.

## focused workflow

the implemented lifecycle is:

```text
submitted -> underReview -> moreInformationRequired -> underReview
                       `-> approved
                       `-> rejected
```

claimant information is an audit event, not an automatic state change. the claim stays in `moreInformationRequired` until the assigned officer resumes review. this keeps ownership visible and prevents a claimant action from silently returning work to an officer.

approved and rejected claims are terminal. decisions and information requests require reasons. only the assigned officer can review or decide a claim, and only the matching claimant can answer an information request.

## architecture decision

`claim.domain` owns all state changes and stable rule codes without spring, jpa, validation, or json imports. `claim.application` owns transactional commands, query models, and ports. `claim.persistence` maps snapshots to jpa and stores evidence. `claim.api` owns validation and wire values. `shared.api` maps known failures to problem details.

this direction keeps business rules testable without a framework and stops database or http details from becoming domain behavior. it also keeps the code small enough to explain file by file.

## data integrity

each accepted command saves three related records in one transaction:

1. the current claim snapshot;
2. one ordered timeline event;
3. one outbox message with valid json.

an integration test forces the evidence writer to fail after the claim save and proves the complete change rolls back. `@Version` protects claim rows, and a test with two persistence contexts proves the stale writer is rejected. the application translates that persistence error into the stable `claim_conflict` code.

flyway owns the three-table schema and hibernate only validates it. local work uses an h2 file and tests use h2 in memory. h2 is a review convenience; postgres and testcontainers are the production direction.

## api decision

rest is used because each command needs an immediate accepted or rejected result. the outbox is the safe asynchronous boundary for later kafka delivery. a bounded in-process relay reads pending rows oldest-first, logs the claim id, event type, and occurrence time, then stamps `processed_at`. this proves the relay lifecycle and pending-row index are active, but it does not claim broker delivery, retries, or cross-process reliability.

the api has separate routes for claim intake, assignment, lifecycle actions, claimant information, claim detail, work queue, and exposure. lower camel case values are mapped at the api edge. known errors use problem details with lower snake case codes and do not expose stack traces or database messages.

request fields carry claimant and officer ids only because authentication is outside the sprint. they demonstrate ownership rules but are not trusted identity. production needs oauth2 authentication, roles, and authorization before these routes handle real data.

## operational reads

the work queue returns only open claims and can filter by state and officer. status and officer predicates are pushed into spring data queries before entities are mapped, including the combined predicate. the queue is intentionally unpaged in this assessment.

exposure retrieves open claim rows from the database, optionally filters them by market there, then groups and sums them in application memory. an unfiltered request combines markets only when their currencies match. amounts are never added across currencies. accepted money values are stored with two decimal places, and inputs with more than two meaningful decimal places are rejected instead of rounded silently.

## ai use approach

ai was used as a constrained engineering tool, not as an unchecked code generator. prompts supplied the business goal, exclusions, public contract, failure cases, expected evidence, exact verification command, and stop condition. larger requests were first turned into a structured prompt and file plan. generated suggestions were accepted, changed, or rejected based on tests and repository evidence.

examples include asking for:

- an explicit state and action table before aggregate code;
- failure-first tests for rollback and stale writes;
- exact exposure examples that reveal currency mistakes;
- a process check with a free port, bounded health polling, and safe cleanup;
- a security gate that must fail on cvss 7 or higher and must not use suppressions to hide findings.

the factual prompt and decision history is in `docs/ai-journal.md`.

## evidence map

| requirement | evidence |
| --- | --- |
| java 21 build | maven wrapper reports temurin 21.0.12 and maven 3.9.16 |
| claim rules | 19 parameterized and direct domain tests |
| persistence, stale writes, and database query predicates | 7 h2 and flyway integration tests |
| transactional evidence | 9 command service tests including forced rollback |
| queue and exposure | 7 query service tests |
| public api and openapi | 10 raw json mockmvc tests |
| bounded outbox relay | 3 integration tests covering processing, no-op replay, batch size, and rollback visibility |
| demo safety | idempotent seed test with 3 claims and fictional ids |
| application health | 1 random-port health test |
| process health | packaged jar live check on a free port |
| complete suite | 57 tests, 0 failures, 0 errors, 0 skipped |
| formatting | spotless checks all 49 java files during `verify` |

the live check starts the packaged jar with a fresh temporary database. it verifies three demo claims, submits a `2800.00 SGD` claim, assigns and approves it, checks four timeline events, and proves singapore exposure moves from `16600.00 / 3` to `13800.00 / 2`.

## dependency review

the final runtime uses spring boot `4.1.0`, springdoc `3.1.0`, tomcat `11.0.24`, jackson 2 `2.21.5` for libraries that still use that line, jackson 3 `3.1.4` for the application json stack, log4j api `2.25.5`, and swagger ui `5.32.11`.

the first owasp dependency-check run failed on the earlier managed versions. the dependencies were upgraded to the first compatible fixed lines without a suppression file. the repeated scan analyzed 65 dependencies and reported zero dependencies with findings and zero cvss 7 or higher findings. nvd, known exploited vulnerabilities, and retirejs analysis ran; sonatype oss index was unavailable because that service requires credentials, so this result remains a best-effort scan rather than a security guarantee.

## deliberate limits

- no authentication or authorization;
- no real claimant data, documents, or policy secrets;
- no attachment, payment, fraud, policy, or notification integration;
- the outbox relay is in-process and log-only, with no kafka broker, publisher, or consumer;
- no queue pagination; keyset pagination is the production direction because queue order is stable and offset scans degrade as the table grows;
- exposure aggregates claim rows in application memory rather than using sql `group by` and `sum`;
- no production database, deployment, metrics, tracing, or alerting.

these limits are visible rather than hidden. pagination and sql aggregation were left out because the sprint has a tiny bounded data set and the existing tests can prove the business rules without adding production-scale query complexity. the next production work is identity and access control, postgres with testcontainers, a broker-backed idempotent publisher with retry and monitoring, keyset pagination, a measured sql exposure aggregate, privacy controls, observability, and deployment automation.

## final assessment

the submission favors a complete, explainable vertical slice over feature count. its strongest evidence is not the number of classes: it is the agreement between pure domain tests, database integration tests, black-box api tests, transaction rollback proof, stale-write proof, a real packaged-process run, and a vulnerability gate that found and drove dependency fixes.
