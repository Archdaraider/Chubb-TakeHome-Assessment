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
