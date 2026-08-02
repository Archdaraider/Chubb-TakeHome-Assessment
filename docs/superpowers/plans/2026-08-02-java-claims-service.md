# java claims service implementation plan

> **for agentic workers:** required sub-skill: use `superpowers:executing-plans` to implement this plan task by task. steps use checkbox (`- [ ]`) syntax for tracking. the user requires inline execution, a review stop after every task, and separate approval before every commit and push.

**goal:** build a tested spring boot backend for claim intake, officer work, lifecycle decisions, claimant information, audit history, and open exposure.

**architecture:** use one spring boot process with domain, application, persistence, and api packages. keep the claim aggregate free of spring and jpa, save claim changes with timeline and outbox evidence in one transaction, and expose immediate user operations through rest.

**tech stack:** java 21 lts, spring boot 4.0.7, maven wrapper 3.3.4, spring web mvc, validation, data jpa, flyway, h2, actuator, springdoc openapi 3.0.3, junit 5, assertj, mockmvc, and spotless 3.6.0.

## global constraints

- use java 21 and compile with `--release 21`.
- use spring boot 4.0.7 and do not override spring framework versions.
- use `spring-boot-starter-webmvc`; the older `spring-boot-starter-web` is deprecated in spring boot 4.
- use the maven wrapper; do not require a system maven installation.
- keep local java downloads under ignored `.tools/` and never commit them.
- keep the domain package free of spring, jpa, jackson, and validation annotations.
- use h2 file mode locally, h2 memory mode in tests, and flyway for all schema changes.
- write money as `BigDecimal` and never add different currencies together.
- write timestamps as `Instant` from an injected `Clock`.
- keep one transaction around the claim update, timeline append, and outbox append.
- use stable lower snake case error codes and lower camel case json enum values.
- use fictional seed data and opaque person identifiers.
- do not add live kafka, authentication, uploads, payments, fraud scoring, or microservices.
- begin behavior changes with a focused failing test and record the red and green result in the ai journal.
- use short lower-case commit messages with real timestamps.
- stop after each task for user review; commit and push only after separate approval.

## file map

```text
pom.xml
.mvn/wrapper/maven-wrapper.properties
mvnw
mvnw.cmd
src/main/java/com/archdaraider/chubb/claims/ClaimsApplication.java
src/main/java/com/archdaraider/chubb/claims/claim/domain/*
src/main/java/com/archdaraider/chubb/claims/claim/application/*
src/main/java/com/archdaraider/chubb/claims/claim/persistence/*
src/main/java/com/archdaraider/chubb/claims/claim/api/*
src/main/java/com/archdaraider/chubb/claims/shared/api/*
src/main/java/com/archdaraider/chubb/claims/bootstrap/*
src/main/resources/application.yml
src/main/resources/application-demo.yml
src/main/resources/db/migration/V1__create_claims_schema.sql
src/test/java/com/archdaraider/chubb/claims/*
src/test/resources/application-test.yml
scripts/live-check.ps1
requests/claims.http
README.md
docs/ai-journal.md
```

---

### task 1: spring boot baseline

**files:**

- create: `pom.xml`
- create: `.mvn/wrapper/maven-wrapper.properties`
- create: `mvnw`
- create: `mvnw.cmd`
- create: `src/main/java/com/archdaraider/chubb/claims/ClaimsApplication.java`
- create: `src/main/java/com/archdaraider/chubb/claims/bootstrap/TimeConfiguration.java`
- create: `src/main/resources/application.yml`
- create: `src/test/resources/application-test.yml`
- create: `src/test/java/com/archdaraider/chubb/claims/HealthTest.java`
- modify: `docs/ai-journal.md`

**interfaces:**

- produces: an executable spring boot application, `Clock` bean, `/actuator/health`, maven verification, and formatting checks.
- consumed later: every application service receives the `Clock` bean; every later test runs under the `test` profile.

- [ ] **step 1: prepare the ignored java toolchain**

download temurin java 21 from the adoptium binary api into `.tools/`, verify the archive checksum returned by the api when available, extract it, and set session-only values:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools -Directory -Filter 'jdk-21*' | Select-Object -First 1).FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

expected: `java -version` reports a temurin 21 build. no system environment variable changes.

- [ ] **step 2: add the build and wrapper**

create `pom.xml` with this dependency and plugin contract:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.7</version>
  <relativePath/>
</parent>
<groupId>com.archdaraider.chubb</groupId>
<artifactId>claims-service</artifactId>
<version>0.0.1-SNAPSHOT</version>
<properties>
  <java.version>21</java.version>
  <springdoc.version>3.0.3</springdoc.version>
  <spotless.version>3.6.0</spotless.version>
</properties>
```

add these dependencies with no version except springdoc:

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
<dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${springdoc.version}</version></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
```

configure `spring-boot-maven-plugin` and `spotless-maven-plugin`. spotless must use `googleJavaFormat`, remove unused imports, trim trailing whitespace, end files with a newline, and bind `check` to `verify`.

use the apache wrapper 3.3.4 script distribution and pin its `distributionUrl` to an apache maven 3.9 release. run:

```powershell
.\mvnw.cmd --version
```

expected: the wrapper runs with java 21 and reports the pinned maven version.

- [ ] **step 3: write the failing health test**

create `HealthTest` with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `@ActiveProfiles("test")`. use Java `HttpClient` and `@LocalServerPort` to request `/actuator/health`:

```java
@Test
void healthIsAvailable() throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
            .GET()
            .build();

    var response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
}
```

- [ ] **step 4: run the health test red**

run:

```powershell
.\mvnw.cmd -Dtest=HealthTest test
```

expected: compilation fails because `ClaimsApplication` does not exist.

- [ ] **step 5: add the smallest application**

create:

```java
@SpringBootApplication
public class ClaimsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaimsApplication.class, args);
    }
}
```

create `TimeConfiguration` with one bean:

```java
@Bean
Clock clock() {
    return Clock.systemUTC();
}
```

configure `application.yml` with file h2, temporary `ddl-auto: none`, `open-in-view: false`, flyway enabled, health exposure only, and no server error message or stack trace. configure `application-test.yml` with `jdbc:h2:mem:claims-test;DB_CLOSE_DELAY=-1` and temporary `ddl-auto: none`. task 3 changes both values to `validate` in the same change that adds the first migration.

- [ ] **step 6: run the baseline green**

run:

```powershell
.\mvnw.cmd -Dtest=HealthTest test
.\mvnw.cmd spotless:apply
.\mvnw.cmd verify
```

expected: one health test passes, formatting is clean, and the jar is created under `target/`.

- [ ] **step 7: record and review**

append a real-time journal entry with the requested baseline, dependency choices, the failing result, the minimal fix, and the exact final commands. name `superpowers:test-driven-development` and `superpowers:verification-before-completion` if used.

stop and show the changed files, dependency tree summary, test count, jar name, and proposed commit `start java service`. wait for commit approval, then wait again for push approval.

---

### task 2: claim lifecycle rules

**files:**

- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/ClaimType.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/ClaimStatus.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/ClaimAction.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/ClaimSubmission.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/ClaimChange.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/ClaimSnapshot.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/DomainRuleException.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/domain/Claim.java`
- create: `src/test/java/com/archdaraider/chubb/claims/claim/domain/ClaimTest.java`
- modify: `docs/ai-journal.md`

**interfaces:**

- produces: `Claim.submit`, `Claim.rehydrate`, `assign`, `apply`, `provideInformation`, and `snapshot`.
- consumed later: persistence maps `ClaimSnapshot`; application services persist each returned `ClaimChange`.

- [ ] **step 1: define the public domain contract in a failing test**

use these exact signatures:

```java
public static Claim submit(ClaimSubmission submission, Instant now)
public static Claim rehydrate(ClaimSnapshot snapshot)
public ClaimChange assign(String officerId, Instant now)
public ClaimChange apply(ClaimAction action, String officerId, String reason, Instant now)
public ClaimChange provideInformation(String claimantId, String information, Instant now)
public ClaimSnapshot snapshot()
```

`ClaimSubmission` contains `claimantId`, `ClaimType`, `market`, `incidentAt`, `description`, `estimatedLoss`, and `currency`. `ClaimSnapshot` adds `id`, status, assignee, decision reason, submitted time, updated time, and a nullable `Long version`. a new aggregate has a null version; only `rehydrate` accepts a non-null stored version. `ClaimChange` contains `eventType`, resulting status, actor id, detail, and occurred time.

start with these tests:

```java
@Test void validSubmissionCreatesSubmittedClaim()
@ParameterizedTest void invalidSubmissionReturnsStableCode(ClaimSubmission input, String code)
@Test void unassignedSubmittedClaimCanBeAssignedOnce()
@Test void reviewRequiresAssignedOfficer()
@Test void assignedOfficerCanCompleteTheFullReviewPath()
@Test void requestForInformationRequiresReason()
@Test void claimantCanProvideInformationOnlyWhenRequested()
@Test void differentClaimantCannotProvideInformation()
@Test void differentOfficerCannotChangeTheClaim()
@Test void approvalAndRejectionRequireReason()
@Test void invalidTransitionsReturnClaimTransitionInvalid()
@Test void terminalClaimRejectsFurtherChanges()
```

assert stable codes: `claimant_required`, `claim_type_required`, `market_invalid`, `incident_time_invalid`, `description_invalid`, `estimated_loss_invalid`, `currency_invalid`, `officer_required`, `claim_already_assigned`, `claim_officer_mismatch`, `claimant_mismatch`, `reason_required`, `information_required`, `claim_transition_invalid`, and `claim_closed`.

the invalid-submission parameter source must include a blank claimant id, null type, three-letter market, incident one second after `now`, nine-character description, zero loss, and two-letter currency. assert the matching code for each row.

- [ ] **step 2: run the domain test red**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimTest test
```

expected: compilation fails because the domain types do not exist.

- [ ] **step 3: implement intake and assignment only**

add enums with lower camel values:

```text
type: motor, property
status: submitted, underReview, moreInformationRequired, approved, rejected
action: startReview, requestMoreInformation, resumeReview, approve, reject
```

validate input in `Claim.submit`. trim identifiers and codes, uppercase market and currency for storage, reject a future incident, require description length at least 10, and require loss greater than zero. use `UUID.randomUUID()` and the supplied `now`.

implement assignment only for an unassigned submitted claim and return `ClaimChange("claim_assigned", SUBMITTED, officerId, null, now)`.

- [ ] **step 4: run the intake and assignment subset green**

run:

```powershell
.\mvnw.cmd -Dtest='ClaimTest#validSubmissionCreatesSubmittedClaim+invalidSubmissionReturnsStableCode+unassignedSubmittedClaimCanBeAssignedOnce' test
```

expected: the selected tests pass while lifecycle tests remain failing or uncompilable.

- [ ] **step 5: implement the explicit transition table**

implement only these state and action pairs:

```text
submitted + startReview -> underReview, review_started
underReview + requestMoreInformation -> moreInformationRequired, more_information_requested
moreInformationRequired + resumeReview -> underReview, review_resumed
underReview + approve -> approved, claim_approved
underReview + reject -> rejected, claim_rejected
```

require the assigned officer for every action. require a reason for request, approval, and rejection. store the reason as the decision reason only for approval or rejection.

`provideInformation` requires the matching claimant and `moreInformationRequired`; it returns `ClaimChange("additional_information_provided", MORE_INFORMATION_REQUIRED, claimantId, information, now)` without changing status.

- [ ] **step 6: run domain verification**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimTest test
.\mvnw.cmd verify
```

expected: every named domain test and the earlier health test pass.

- [ ] **step 7: record and review**

update the journal with the state table, any suggestion challenged, the red and green commands, and exact test count. stop for review with proposed commit `add claim rules`, then wait separately for push approval.

---

### task 3: persistence, audit, and outbox

**files:**

- create: `src/main/resources/db/migration/V1__create_claims_schema.sql`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ClaimStore.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ClaimEvidenceStore.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/TimelineItem.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/ClaimEntity.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/TimelineEntity.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/OutboxEntity.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/ClaimJpaRepository.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/TimelineJpaRepository.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/OutboxJpaRepository.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/ClaimJpaAdapter.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/persistence/ClaimEvidenceJpaAdapter.java`
- create: `src/test/java/com/archdaraider/chubb/claims/claim/persistence/ClaimPersistenceTest.java`
- modify: `src/main/resources/application.yml`
- modify: `src/test/resources/application-test.yml`
- modify: `docs/ai-journal.md`

**interfaces:**

- `ClaimStore`: `Optional<Claim> findById(UUID)`, `Claim save(Claim)`, `List<Claim> findForQueue(ClaimStatus, String)`, and `List<Claim> findOpenByMarket(String)`.
- `ClaimEvidenceStore`: `void append(UUID claimId, ClaimChange change)` and `List<TimelineItem> findTimeline(UUID claimId)`.

- [ ] **step 1: write the migration test red**

create `ClaimPersistenceTest` with `@SpringBootTest` and `@ActiveProfiles("test")`. query all three repositories and expect the test to fail because the tables are absent. run:

```powershell
.\mvnw.cmd -Dtest=ClaimPersistenceTest test
```

expected: the first repository query fails because the tables do not exist.

- [ ] **step 2: create the schema**

write `V1__create_claims_schema.sql` with:

```sql
create table claims (
    id uuid primary key,
    claimant_id varchar(100) not null,
    claim_type varchar(30) not null,
    market varchar(2) not null,
    incident_at timestamp with time zone not null,
    description varchar(2000) not null,
    estimated_loss decimal(19, 2) not null,
    currency varchar(3) not null,
    status varchar(40) not null,
    assignee_id varchar(100),
    decision_reason varchar(2000),
    submitted_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null
);
create table claim_timeline (
    id uuid primary key,
    claim_id uuid not null references claims(id),
    event_type varchar(80) not null,
    resulting_status varchar(40) not null,
    actor_id varchar(100),
    detail clob,
    occurred_at timestamp with time zone not null
);
create table outbox_messages (
    id uuid primary key,
    claim_id uuid not null references claims(id),
    event_type varchar(80) not null,
    payload clob not null,
    occurred_at timestamp with time zone not null,
    processed_at timestamp with time zone
);
create index ix_claims_status_assignee on claims(status, assignee_id);
create index ix_claims_market_status on claims(market, status);
create index ix_timeline_claim_time on claim_timeline(claim_id, occurred_at);
create index ix_outbox_pending_time on outbox_messages(processed_at, occurred_at);
```

rerun the test and expect context startup to pass.

change `spring.jpa.hibernate.ddl-auto` from `none` to `validate` in both application configuration files. from this task onward, flyway must create a schema that matches every jpa mapping.

- [ ] **step 3: write adapter tests red**

add tests that:

- save and reload every `ClaimSnapshot` field;
- append one change and find one ordered timeline item;
- append one outbox row with valid json containing `claimId`, `eventType`, `status`, and `occurredAt`;
- query queue by status and assignee;
- query open claims by market while excluding approved and rejected claims;
- load the same claim in two persistence contexts and prove the second stale save throws `ObjectOptimisticLockingFailureException`.

run the focused class and expect missing adapter compilation failures.

- [ ] **step 4: implement jpa mappings and adapters**

map enum values with `EnumType.STRING`, money with precision 19 and scale 2, and timestamps as `Instant`. put `@Version` on nullable `ClaimEntity.version`. map between `ClaimSnapshot` and `ClaimEntity` in `ClaimJpaAdapter`; do not expose entities outside the persistence package. when the snapshot version is null, call `EntityManager.persist` and flush. when it is non-null, call `EntityManager.merge` on a detached entity carrying that exact version and flush. map the managed entity back to a rehydrated aggregate so the returned snapshot contains the database version.

use ordered repository methods:

```java
List<TimelineEntity> findByClaimIdOrderByOccurredAtAsc(UUID claimId);
List<ClaimEntity> findByStatusOrderBySubmittedAtAsc(ClaimStatus status);
List<ClaimEntity> findByAssigneeIdOrderBySubmittedAtAsc(String assigneeId);
```

filter the small assessment queue in the adapter when both optional filters are present. query only the three open states for exposure.

the queue adapter also always restricts results to submitted, under-review, and more-information-required states, even when both filters are null.

serialize outbox payload through the injected Spring `ObjectMapper`. a serialization failure throws an `IllegalStateException("outbox_payload_invalid", cause)` so the transaction rolls back.

- [ ] **step 5: run persistence verification**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimPersistenceTest test
.\mvnw.cmd verify
```

expected: migration, round-trip, timeline, outbox, query, and stale-write tests pass.

- [ ] **step 6: record and review**

record the real h2 behavior, transaction decisions, concurrency proof, and exact commands. stop with proposed commit `add claim storage`, then wait separately for push approval.

---

### task 4: transactional claim commands

**files:**

- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/SubmitClaimCommand.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/AssignClaimCommand.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ApplyClaimActionCommand.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ProvideInformationCommand.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ClaimNotFoundException.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ClaimConflictException.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ClaimsCommandService.java`
- create: `src/test/java/com/archdaraider/chubb/claims/claim/application/ClaimsCommandServiceTest.java`
- modify: `docs/ai-journal.md`

**interfaces:**

```java
ClaimSnapshot submit(SubmitClaimCommand command)
ClaimSnapshot assign(AssignClaimCommand command)
ClaimSnapshot apply(ApplyClaimActionCommand command)
ClaimSnapshot provideInformation(ProvideInformationCommand command)
```

each method is transactional, uses `clock.instant()`, and writes exactly one timeline and one outbox row for each accepted command.

- [ ] **step 1: write service tests red**

write integration tests proving:

```text
submit -> 1 claim, 1 claim_submitted timeline row, 1 outbox row
assign -> updated assignee, claim_assigned timeline row, matching outbox row
start review -> underReview and two new evidence rows
provide information -> status unchanged and evidence contains claimant text
approve -> terminal status and decision reason
missing id -> ClaimNotFoundException with claim_not_found
domain rule -> unchanged claim, timeline count, and outbox count
stale write -> ClaimConflictException with claim_conflict
```

for rollback proof, use a nested `@TestConfiguration` with a `@Primary` `ClaimEvidenceStore` decorator that receives the concrete `ClaimEvidenceJpaAdapter` and throws on append. use `JdbcTemplate` to count persisted rows and assert the claim update is rolled back.

- [ ] **step 2: run service tests red**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimsCommandServiceTest test
```

expected: compilation fails because commands and service do not exist.

- [ ] **step 3: implement the transaction boundary**

for every command:

1. load or create the domain aggregate;
2. apply exactly one domain method;
3. save the aggregate;
4. append the returned change to timeline and outbox;
5. return the saved snapshot.

catch `ObjectOptimisticLockingFailureException` and throw `ClaimConflictException("claim_conflict", "the claim changed; reload and try again")`. do not catch domain errors.

- [ ] **step 4: run service verification**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimsCommandServiceTest test
.\mvnw.cmd verify
```

expected: atomicity, stable error, evidence, and full-suite checks pass.

- [ ] **step 5: record and review**

record the application prompt, any transaction suggestion rejected, rollback evidence, and test count. stop with proposed commit `add claim commands`, then wait separately for push approval.

---

### task 5: claim reads, work queue, and exposure

**files:**

- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ClaimDetails.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/WorkQueueItem.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ExposureItem.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/application/ClaimsQueryService.java`
- create: `src/test/java/com/archdaraider/chubb/claims/claim/application/ClaimsQueryServiceTest.java`
- modify: `docs/ai-journal.md`

**interfaces:**

```java
ClaimDetails get(UUID claimId)
List<WorkQueueItem> workQueue(ClaimStatus status, String assigneeId)
List<ExposureItem> exposure(String market)
```

nullable filters mean no filter. `ClaimDetails` contains the snapshot and ordered timeline. exposure items contain currency, amount, and claim count.

- [ ] **step 1: write query tests red**

seed submitted, under-review, more-information-required, approved, and rejected claims across `SG` and `AU` with `SGD` and `AUD` amounts. assert:

- get returns all timeline rows in occurrence order;
- missing get throws `claim_not_found`;
- queue returns all open claims and excludes approved and rejected claims when filters are null;
- queue filters status, assignee, and both together;
- exposure includes only the three open states;
- exposure groups `SGD` and `AUD` separately;
- market `SG` excludes `AU` claims;
- approving an open claim reduces its currency amount and count.

- [ ] **step 2: run query tests red**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimsQueryServiceTest test
```

expected: compilation fails because query records and service do not exist.

- [ ] **step 3: implement reads without currency errors**

load only filtered open rows from `ClaimStore`, then group in Java using:

```java
Map<String, List<Claim>> byCurrency = claims.stream()
        .collect(Collectors.groupingBy(claim -> claim.snapshot().currency(), TreeMap::new, Collectors.toList()));
```

sum with `BigDecimal.ZERO` and `BigDecimal::add`; never convert currency. sort queue by submitted time and exposure by currency.

- [ ] **step 4: run query verification**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimsQueryServiceTest test
.\mvnw.cmd verify
```

expected: every query test and the full suite pass.

- [ ] **step 5: record and review**

record exposure semantics, why grouping happens after database filtering, and exact results. stop with proposed commit `add claim queries`, then wait separately for push approval.

---

### task 6: rest api and problem details

**files:**

- create: `src/main/java/com/archdaraider/chubb/claims/claim/api/ClaimRequests.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/api/ClaimResponses.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/api/ClaimApiMapper.java`
- create: `src/main/java/com/archdaraider/chubb/claims/claim/api/ClaimsController.java`
- create: `src/main/java/com/archdaraider/chubb/claims/shared/api/ApiInputException.java`
- create: `src/main/java/com/archdaraider/chubb/claims/shared/api/ApiExceptionHandler.java`
- create: `src/test/java/com/archdaraider/chubb/claims/claim/api/ClaimsApiTest.java`
- modify: `src/main/resources/application.yml`
- modify: `docs/ai-journal.md`

**interfaces:**

implement every endpoint and status from the design spec. request records use validation annotations; response records use strings for lower camel enum values so domain enums remain framework-free.

- [ ] **step 1: write raw-json api tests red**

build `MockMvc` from `WebApplicationContext`. send literal json rather than serializing request records. cover:

```text
post /claims -> 201, location, submitted body
get location -> 200 and claim_submitted timeline
post assignment -> 200 and assignee
post startReview -> underReview
post requestMoreInformation -> moreInformationRequired
post information -> same status and new timeline row
post resumeReview -> underReview
post approve -> approved
get work-queue?status=underReview&assigneeId=... -> filtered result
get exposure?market=SG -> currency-separated result
bad submission -> 400 with code description_invalid
unknown claim -> 404 with code claim_not_found
invalid transition -> 409 with code claim_transition_invalid
wrong officer -> 409 with code claim_officer_mismatch
bad query enum -> 400 with code query_invalid
get /v3/api-docs -> contains every public route
```

- [ ] **step 2: run api tests red**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimsApiTest test
```

expected: compilation fails because the api types do not exist.

- [ ] **step 3: add validated request records**

define:

```java
record SubmitClaimRequest(
        @NotBlank String claimantId,
        @NotBlank String type,
        @Pattern(regexp = "[A-Za-z]{2}") String market,
        @NotNull Instant incidentAt,
        @Size(min = 10, max = 2000) String description,
        @DecimalMin(value = "0.01") BigDecimal estimatedLoss,
        @Pattern(regexp = "[A-Za-z]{3}") String currency) {}

record AssignmentRequest(@NotBlank String officerId) {}
record ActionRequest(@NotBlank String action, @NotBlank String officerId, String reason) {}
record InformationRequest(@NotBlank String claimantId, @NotBlank @Size(max = 2000) String information) {}
```

parse type, action, and query status with case-insensitive helpers that accept the documented lower camel value. an unknown value throws `ApiInputException("query_invalid", ...)` or `ApiInputException("action_invalid", ...)`.

`ApiInputException` extends `RuntimeException`, stores a non-blank `code`, and exposes it through `code()`.

- [ ] **step 4: add controllers and mapping**

use these route methods:

```java
@PostMapping("/claims") ResponseEntity<ClaimResponse> submit(...)
@GetMapping("/claims/{claimId}") ClaimDetailsResponse get(...)
@PostMapping("/claims/{claimId}/assignment") ClaimResponse assign(...)
@PostMapping("/claims/{claimId}/actions") ClaimResponse apply(...)
@PostMapping("/claims/{claimId}/information") ClaimResponse provideInformation(...)
@GetMapping("/work-queue") List<WorkQueueResponse> workQueue(...)
@GetMapping("/exposure") List<ExposureResponse> exposure(...)
```

submission returns `ResponseEntity.created(URI.create("/claims/" + id)).body(response)`.

- [ ] **step 5: add stable problem details**

use `@RestControllerAdvice` and return Spring `ProblemDetail`. map:

```text
MethodArgumentNotValidException, ApiInputException -> 400
ClaimNotFoundException -> 404
DomainRuleException, ClaimConflictException -> 409
unexpected Exception -> 500
```

set title, detail, status, instance, and `code`. never include stack traces or database messages. validation chooses the first field error ordered by field name and returns a stable code derived from that field.

- [ ] **step 6: run api verification**

run:

```powershell
.\mvnw.cmd -Dtest=ClaimsApiTest test
.\mvnw.cmd verify
```

expected: all api cases, openapi route assertions, formatting, and the full suite pass.

- [ ] **step 7: record and review**

record the wire contract, identity shortcut, problem mapping, and any binder behavior discovered. stop with proposed commit `add claims api`, then wait separately for push approval.

---

### task 7: fictional demo, live check, and guide

**files:**

- create: `src/main/java/com/archdaraider/chubb/claims/bootstrap/DemoData.java`
- create: `src/main/resources/application-demo.yml`
- create: `src/test/java/com/archdaraider/chubb/claims/bootstrap/DemoDataTest.java`
- create: `requests/claims.http`
- create: `scripts/live-check.ps1`
- create: `README.md`
- modify: `docs/ai-journal.md`

**interfaces:**

- `DemoData` runs only under the `demo` profile and is idempotent.
- `scripts/live-check.ps1` accepts no required arguments, uses a temporary h2 database and free local port, and always stops the child process.

- [ ] **step 1: write demo seed test red**

assert two calls produce exactly:

```text
3 claims
1 submitted SG motor claim for 1800 SGD
1 underReview AU property claim for 6500 AUD
1 moreInformationRequired SG property claim for 12000 SGD
8 timeline rows
8 outbox rows
```

every claimant id starts with `demo-claimant-`; every officer id starts with `demo-officer-`.

- [ ] **step 2: implement idempotent demo data**

use application commands, not direct repositories, so seed data exercises the same rules and writes evidence. exit immediately when any claim exists. keep all descriptions and identifiers fictional.

- [ ] **step 3: write the request collection**

`requests/claims.http` must contain runnable examples for health, submit, get, assign, start review, request information, provide information, resume review, approve, queue, exposure, and openapi. use lower-case section titles and environment variables for base url and captured claim id.

- [ ] **step 4: write the live process check**

the powershell script must:

1. locate java from `.tools/` or `JAVA_HOME`;
2. build the jar with `mvnw.cmd package` unless `-SkipBuild` is passed;
3. choose a free port;
4. start the jar hidden with `demo` profile and a temporary h2 path;
5. poll health with a bounded loop;
6. verify three seed queue items;
7. submit a 2800 SGD claim;
8. assign, start review, and approve it;
9. verify SG exposure changes from 16600 across three open claims to 13800 across two;
10. verify four timeline entries for the new claim;
11. verify openapi contains queue, exposure, and information routes;
12. stop the process in `finally` and remove only its temporary files.

- [ ] **step 5: write the readme**

use lower-case headings. cover what works, architecture, state diagram, rest versus kafka, requirements, exact run/test/demo commands, endpoint table, error model, data location, privacy, shortcuts, next production work, ai use, and source map. state that h2, demo identity fields, and no live kafka are sprint choices.

- [ ] **step 6: run demo verification**

run:

```powershell
.\mvnw.cmd -Dtest=DemoDataTest test
.\mvnw.cmd verify
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\live-check.ps1 -SkipBuild
```

expected: seed test passes, full suite passes, and live output shows health `UP`, three seed claims, final status `approved`, four timeline rows, exposure `16600` then `13800`, and openapi `ok`.

- [ ] **step 7: record and review**

complete the journal with actual prompts, accepted and rejected suggestions, skills used, failures, and final evidence. stop with proposed commit `add demo guide`, then wait separately for push approval.

---

### task 8: clean verification and handoff

**files:**

- modify only files required by a verified failure.

**interfaces:**

- produces: a clean repository whose local `main` and `origin/main` match after the final approved push.

- [ ] **step 1: run the complete clean gate**

from a clean process with java 21:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd clean verify
.\mvnw.cmd dependency:tree
.\mvnw.cmd org.owasp:dependency-check-maven:12.2.2:check -DfailBuildOnCVSS=7
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\live-check.ps1 -SkipBuild
```

read every exit code and the total failed, passed, and skipped test counts. inspect the dependency tree for unexpected duplicate logging, json, database, or test stacks. read the owasp report and treat an unavailable external vulnerability feed as a reported verification limit, not as a clean security result.

- [ ] **step 2: run repository content checks**

scan tracked files for conflict markers, unfinished marker text, secrets, real personal data, uppercase markdown headings, generated build output, local databases, and `.tools/`. run `git diff --check` and confirm `git status --short` is empty after any approved fix.

- [ ] **step 3: review the assessment contract**

check each acceptance criterion in the design spec against a test, live result, or named document section. report any gap instead of weakening the criterion.

- [ ] **step 4: present final evidence before the last push**

show:

- commit list with real timestamps;
- exact test totals;
- build and formatting results;
- live flow values;
- dependency review result;
- clean status;
- remaining documented shortcuts.

wait for final push approval. after pushing, compare `git rev-parse HEAD` with `git ls-remote origin refs/heads/main` and report the github url.

## source checks used for this plan

- spring boot 4.0.7 build-system documentation for managed dependencies and boot 4 starter names
- spring boot 4.0.7 testing documentation
- apache maven wrapper plugin 3.3.4 documentation
- springdoc openapi 3.0.3 boot 4 release notes
- spotless maven plugin 3.6.0 release and configuration documentation
- owasp dependency-check maven plugin 12.2.2 usage documentation
