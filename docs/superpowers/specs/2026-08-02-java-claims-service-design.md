# java claims service design

date: 2026-08-02

status: ready for user review

## problem and sprint boundary

chubb needs a backend that gives claimants visibility and gives claims staff a reliable work queue. the assessment is a short sprint, so the service will prove one complete motor and property claim path instead of attempting the whole insurance platform.

the implemented path will let a claimant submit a claim, read its state and timeline, and provide requested information. a claims officer will take a claim, review it, request more information, resume review, and approve or reject it. operational queries will show open work and outstanding estimated exposure.

## goals

- enforce claim lifecycle rules in one domain model
- prevent two officers from silently taking the same claim
- persist a readable audit timeline for every accepted change
- expose a small, consistent rest api with useful errors
- show current work and exposure without adding different currencies together
- record integration events transactionally for later kafka delivery
- start locally with one command after a java 21 installation
- keep every submitted line small enough to explain during the walkthrough

## non-goals

- authentication, authorization, or real user accounts
- document upload or object storage
- payment and settlement processing
- fraud scoring, policy coverage, reserves, or pricing
- team performance metrics
- multiple deployable services
- a running kafka broker or event consumer
- production infrastructure, caching, or multi-region deployment

## architecture

the application will be a modular monolith: one spring boot process and one database with clear internal packages.

```text
com.archdaraider.chubb.claims
|-- claim.domain
|-- claim.application
|-- claim.persistence
|-- claim.api
|-- shared.api
`-- bootstrap
```

- `claim.domain` owns the claim aggregate, states, actions, and rule errors. it has no spring or persistence dependency.
- `claim.application` owns transactional use cases and query services.
- `claim.persistence` owns jpa mappings, repositories, flyway migrations, timeline rows, and outbox rows.
- `claim.api` owns http request and response records plus controllers.
- `shared.api` maps known failures to problem details.
- `bootstrap` owns application startup and fictional demo data.

this design preserves transaction integrity and fast local startup. a service boundary can be extracted later only when independent ownership, scaling, deployment, or isolation requirements justify it.

## technology baseline

- java 21 lts
- spring boot 4.0.7
- apache maven through the maven wrapper
- spring web mvc and jakarta validation
- spring data jpa
- flyway database migrations
- h2 in file mode locally and in-memory mode for tests
- spring boot actuator health endpoint
- springdoc openapi 3.0.3 and swagger ui
- junit 5, assertj, and mockmvc

a local temurin java 21 distribution may be placed under ignored `.tools/` for development on this machine. it will not change the system java installation or enter git. the repository will contain the maven wrapper so a separate maven installation is not required.

## domain model

### claim

- id: uuid
- claimant id: opaque string
- type: motor or property
- market: two-letter market code
- incident time: utc instant
- description: claimant-provided summary
- estimated loss: positive `BigDecimal`
- currency: three-letter code
- status: lifecycle state
- assignee id: optional opaque officer id
- decision reason: optional text
- submitted and updated times: utc instants
- version: optimistic concurrency value

### timeline entry

records the claim id, event type, resulting status, actor id, reason or supplied information, and event time. the timeline is append-only through application use cases.

### outbox message

records the message id, claim id, event type, json payload, occurrence time, and optional processing time. it is saved in the same database transaction as the claim change and timeline entry.

## lifecycle and rules

```text
submitted -> under review -> more information required -> under review
                         `-> approved
                         `-> rejected
```

- intake requires a claimant id, supported claim type, two-letter market, non-future incident time, meaningful description, positive loss, and three-letter currency.
- only an unassigned submitted claim can be assigned.
- review cannot start until an officer is assigned.
- requesting more information requires a reason and is allowed only during review.
- a claimant can provide non-empty information only while more information is required. this appends evidence to the timeline but does not let the claimant change the workflow state.
- only the assigned officer can resume review or make a decision.
- approval and rejection require a reason.
- approved and rejected claims are terminal.
- a stale database version produces a stable conflict instead of silently overwriting a newer change.

## rest api

| method | path | purpose |
| --- | --- | --- |
| `post` | `/claims` | submit a claim |
| `get` | `/claims/{claimId}` | read the claim and timeline |
| `post` | `/claims/{claimId}/assignment` | assign an unowned claim |
| `post` | `/claims/{claimId}/actions` | start, request information, resume, approve, or reject |
| `post` | `/claims/{claimId}/information` | add claimant information when requested |
| `get` | `/work-queue` | list work with status and assignee filters |
| `get` | `/exposure` | group open estimated loss by currency, with optional market filter |
| `get` | `/actuator/health` | show process health |
| `get` | `/v3/api-docs` | return the openapi document |

json enum values will use lower camel case. create returns `201` with a location header. known validation failures return `400`, missing claims return `404`, lifecycle or assignment conflicts return `409`, and unexpected failures return `500` without internal details.

error responses use spring `ProblemDetail` with a stable lower snake case `code` property. domain and application errors do not leak jpa or database exception text.

because authentication is outside this sprint, staff action requests carry an `officerId` and claimant information requests carry a `claimantId`. the application service compares that value with the assigned officer or claim owner. this is an explicit demo shortcut, not a claim that request data is secure identity. production authentication would supply the actor from a verified security context instead.

## rest and kafka decision

rest handles commands and queries where the caller needs an immediate result. claim state is updated synchronously and returned only after the transaction succeeds.

kafka is represented by the transactional outbox because notifications, analytics, fraud checks, and downstream reporting do not need to block the caller. a live publisher is outside the sprint. the walkthrough will state that a later relay publishes unprocessed rows with at-least-once delivery and consumers must be idempotent.

## persistence and concurrency

flyway owns the schema. the initial migration creates `claims`, `claim_timeline`, and `outbox_messages`, including indexes for status and assignee work-queue reads, market and status exposure reads, claim timeline reads, and pending outbox reads.

jpa stores enums as readable strings and times as utc instants. `@Version` protects the claim row. application commands run in one transaction so the aggregate update, timeline append, and outbox append either all succeed or all roll back.

outstanding exposure includes submitted, under-review, and more-information-required claims. approved and rejected claims are excluded. amounts are grouped by currency and are never converted or combined.

## testing

- domain unit tests cover intake, assignment, every allowed transition, required reasons, claimant information, invalid transitions, and terminal states.
- persistence integration tests use flyway and real h2 transactions.
- concurrency tests use separate persistence contexts to prove a stale writer fails.
- application tests prove claim, timeline, and outbox atomicity plus queue and exposure behavior.
- mockmvc tests use raw json to prove the public contract and problem details.
- a process-level powershell check starts the packaged jar against a temporary database and exercises submit, assign, review, approve, timeline, exposure, health, and openapi.

tests will assert observable behavior rather than private method structure. each implementation step begins with a failing focused test and ends with the full relevant suite passing.

## privacy and security boundary

seed data is fictional. identifiers remain opaque and no policy documents, medical information, payment details, secrets, or real personal data enter the repository. local database files and tool downloads are ignored.

authentication and role authorization are deliberately deferred, but controller boundaries keep claimant and officer actions distinct so security can be added without moving lifecycle rules into the web layer.

## demo

fictional seed data will provide one submitted claim, one claim under review, and one claim waiting for more information. the live demo will show an invalid transition, then a valid submit, assignment, review, and approval path. exposure will visibly decrease when the claim becomes terminal.

## acceptance criteria

- a clean clone builds and tests with java 21 through `mvnw`
- the packaged application starts locally and reports healthy
- all listed endpoints behave as specified
- claim rules reject invalid and stale changes with stable codes
- every accepted change creates matching timeline and outbox evidence
- work queue and exposure queries return correct filtered results
- openapi describes the public endpoints
- the live process check passes from a fresh temporary database
- the readme states shortcuts, omissions, and the next production steps
- the ai journal contains factual prompts, suggestions, challenges, decisions, and checks

## review and permission workflow

each planned step stops twice: first after local implementation and verification for user review, then after explicit approval to commit and push. no later step starts until the user approves it. commit messages remain short and lower case, and all timestamps are genuine. each journal entry names any codex skill that materially changed the work and explains its effect.
