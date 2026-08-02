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
