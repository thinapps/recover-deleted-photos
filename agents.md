# Repository Instructions

This file provides persistent instructions for AI agents and coding assistants working in the Recover Deleted Photos repository.

## Required Reading

Before reviewing or modifying this repository:

1. Read `readme.md` for the product overview and documentation index.
2. Read the relevant files under `docs/` and review `changelog.md` when the task affects released or versioned behavior.
3. Consult the shared [ThinApps Android Guidelines](https://github.com/thinapps/android-guidelines) for product, Android, repository, workflow, privacy, Google Play, and release defaults.

If the shared repository is temporarily inaccessible, continue using this file and the app-specific documentation rather than guessing its contents.

## Instruction Precedence

Apply instructions in this order:

1. the user's current request and explicit approvals
2. the mandatory repository policy in this file, unless the user explicitly overrides a rule
3. Recover Deleted Photos app-specific documentation and implemented behavior
4. the shared ThinApps Android Guidelines
5. existing repository conventions not covered above

App-specific documentation is the final source of truth when Recover Deleted Photos intentionally differs from a shared guideline. Do not change working app behavior solely to force consistency with a general guideline when the difference is deliberate and documented.

## Product Guardrails

Preserve Recover Deleted Photos as a focused local media scanning and recovery utility.

- Keep scanning limited to supported MediaStore photos, videos, and audio under the app's documented Android 13+ permission model.
- Preserve recovery destinations and behavior for `Pictures/Recovered` and `Music/Recovered` unless an approved change updates the related code, documentation, and disclosures.
- Preserve the privacy-first baseline: no accounts, ads, analytics, tracking, Internet permission, or cloud processing unless the user explicitly approves and the related documentation and disclosures are updated.
- Treat scan results as temporary in-memory state and preserve safe cancellation, cleanup, provider-failure handling, and partial-copy cleanup.
- Prefer small, reliable changes over speculative features, broad refactors, or additional dependencies.

## Mandatory Repository Policy

- Work directly on the default `master` branch.
- Use exactly one commit per edited file.
- Keep all approved related edits to one file together in that file's single commit.
- Commit separate code, resource, documentation, version, configuration, and workflow files separately.
- Do not split one file into several unnecessary commits unless explicitly requested.
- Do not use temporary branches, pull requests, Git trees or blobs, helper workflows, generated patch workflows, squashing, amending, force-pushing, or history rewriting.
- Never use GitHub Actions to create, apply, or commit normal repository changes.
- Before every commit, inspect active workflow triggers and determine whether the commit would start GitHub Actions directly or indirectly.
- Do not create a commit that would trigger an Actions run unless the user explicitly approves the expected run.
- Routine source, documentation, changelog, versioning, and maintenance edits must not automatically consume Actions minutes.
- After each repository change, report the commit SHA, commit message, and exact file changed.

## GitHub Actions

The release workflow in `.github/workflows/android-release.yml` is manual-only through `workflow_dispatch`.

- Do not dispatch the workflow unless the user explicitly requests or approves the run.
- Do not add `push`, `pull_request`, `schedule`, `workflow_run`, `repository_dispatch`, or another automatic trigger without explicit approval and a documented reason.
- Do not change a manual-only workflow into an automatic workflow as part of unrelated work.
- For a release, complete all approved one-file commits first, confirm that version and documentation sources are aligned, and dispatch the intended workflow from the final commit.
- Inspect failures before rerunning a workflow; do not repeatedly rerun unexplained failures.

## Change Review

Before completing a task:

- review the full affected flow rather than only the edited lines
- confirm code, resources, manifest, Gradle configuration, workflows, and documentation remain consistent where relevant
- avoid unrelated cleanup or cosmetic churn
- update app-specific documentation or the changelog when user-facing or release behavior requires it, using separate one-file commits
- state any limitation, unverified assumption, or intentionally deferred follow-up clearly
