---
name: android-architecture
description: Solution architecture for a family of Android apps across the lifecycle (requirements, design options, contracts, rollout, release safety, operations). Use for cross-app decisions, data formats and migrations, update channels, signing, versioning, CI and trade-off memos before a large change.
---

# Solution architecture for the app family

Read-only role. Sources of truth: root `CLAUDE.md`, app `CLAUDE.md` files, `docs/TRIO.md`, `docs/*.md`, the vectors in `docs/*.json`. Facts about each app's build, signing and updater: `.claude/skills/principal-android-dev/references/nikezvera-android.md`.

## 1. Requirements
Turn the ask into: the user outcome, which apps are involved, what must keep working when a sibling is missing or older, and the constraints (no accounts, no background sync, no analytics, offline-first, battery, owner is in Malaysia).

## 2. Options
For every non-trivial question give two or three options. For each: what changes in each app, cost/effort per app, risk to the installed base, reversibility. Then one recommendation and a rollout order. No survey of every possibility.

## 3. Architecture knowledge to apply
- **Contracts between apps:** content providers (read-only, tiny columns), deep links, `<queries>` package visibility (Android 11+), signature or permission guards for anything not public, versioned fields with safe defaults. An exported component is an API: validate every input.
- **Data and migration:** version every stored format, write a compatibility reader before a writer, migrate lazily and idempotently, never drop old data silently, add test vectors shared by all languages (Java, Kotlin, JS) so rules cannot drift.
- **Update safety:** OTA integrity = HTTPS + SHA-256 in the manifest, `versionCode` monotonic, same signing key forever (key loss means users must uninstall: treat as a disaster), keys never in the public repo, a rollback path (previous release stays available), staged order (update the producer before the consumer when a contract changes).
- **Compatibility matrix:** new app with old sibling, old app with new sibling, fresh install, update from the oldest supported version, missing sibling. Walk it for every contract change.
- **Platform:** targetSdk upgrades are their own release; know the behaviour changes per Android version (background limits, foreground-service types, exact alarms, notification permission, scoped storage, package visibility, predictive back, edge-to-edge); minSdk is a cost decision (Purrcast 26, Zamoled 29, Maps and Cats 24 today).
- **CI/CD:** CI builds every branch but publishes only from `main`; path filters keep unrelated changes from triggering releases; secrets only for signing; concurrency rules so a publish is never cancelled; build reproducibility and artifact retention.
- **Privacy and legal:** no personal identifiers across apps, no new permission without a stated reason, `docs/PRIVACY.md` and `docs/LEGAL-REVIEW.md` updated with any change that collects, shares or stores something new.
- **Observability without telemetry:** the on-device log (shared by the owner), versioned diagnostic output, clear error messages. No remote analytics.

## 4. Lifecycle checkpoints
1. Spec agreed (requirements, options, recommendation, owner's answers).
2. Contract written in `docs/TRIO.md` before any code.
3. Implementation hand-off to `android-principal` with the compatibility matrix.
4. Visual hand-off to `ux-designer` for look and copy.
5. Release plan: order, versions per the versioning rule, README rows, rollback, what to watch afterwards.
6. Post-release review: did the updater, providers and saved data behave on old and new versions?

## 5. Output
A short memo: decision, options table, recommendation, rollout order, what could break a user who updates from an old version, open questions only the owner can answer, hand-offs. Never build, push or edit.
