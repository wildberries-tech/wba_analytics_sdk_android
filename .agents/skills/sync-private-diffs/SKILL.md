---
name: sync-private-diffs
description: >-
  Port and synchronize diffs, commits, and changes from the private Android SDK repo (wbanalytics2android) to the public repo (wbanalytics2android-public).
  Handles package transformations (ru.wildberries -> ru.wildberries.pub), strictly excludes ecommerce and private/local configs, preserves minSdk 23 compatibility, and verifies quality gates.
---

# Skill: Sync Private Diffs (wbanalytics2android -> wbanalytics2android-public)

Use this skill when porting commits, diffs, bugfixes, or feature updates from the private repository (`wbanalytics2android`) to the public open-source repository (`wbanalytics2android-public`).

## Principles & Boundaries

1. **Strict Exclusion of E-Commerce**:
   - `library/src/*/java/ru/wildberries/analytics/ecommerce/**` is strictly private and must NEVER be copied or referenced in the public SDK.
   - Tests `WBAnalytics2ProductTest.kt`, `WBAnalyticsAffiliatedDiscountTest.kt` and `ecommerce.md` are excluded.
2. **Strict Exclusion of Private & Local Edits**:
   - Internal credentials, auth tokens, internal hosts/endpoints.
   - Internal CI/CD files (`.gitlab-ci.yml`, internal gitlab pipelines).
   - Internal Nexus publishing configurations.
   - Unmerged local/experimental features (unless explicitly requested).
3. **Namespace Relocation**:
   - Private `ru.wildberries.<pkg>` maps to `ru.wildberries.pub.<pkg>` in both file paths and source code imports/declarations.
4. **Public Compatibility Invariants**:
   - `minSdk = 23`: In `SendOperationScheduler.kt`, use `scheduler.allPendingJobs.any { it.id == SEND_OPERATION_JOB_ID }` (API 21+) instead of `scheduler.getPendingJob` (API 24+), and guard `NETWORK_TYPE_NOT_ROAMING` with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.N`.
   - Preserve `OldDatabaseMigrationHelper.kt` for migrating legacy DB instances into Room.

For full rules and mappings, see [transform_rules.md](./references/transform_rules.md).

---

## Step-by-Step Execution Workflow

### Step 1: Preview the Changes
Before applying changes, preview and classify the files modified in a commit, range, or working tree.

```bash
# Preview changes from a single commit:
python .agents/skills/sync-private-diffs/scripts/sync_diff.py preview --commit <commit_hash>

# Preview changes from a commit range (e.g. master~3..master):
python .agents/skills/sync-private-diffs/scripts/sync_diff.py preview --commit <base>..<head>

# Preview uncommitted changes in private working tree:
python .agents/skills/sync-private-diffs/scripts/sync_diff.py preview --working-tree
```

Verify that:
- Any `ecommerce` files are flagged under `[EXCLUDED FILES]`.
- Internal files like `.gitlab-ci.yml` are excluded.
- Modified files are appropriately mapped to `ru/wildberries/pub/...`.

### Step 2: Apply the Transformed Diff
Port the files into the public repository:

```bash
# Apply changes from a commit:
python .agents/skills/sync-private-diffs/scripts/sync_diff.py apply --commit <commit_hash>

# Apply changes from a range:
python .agents/skills/sync-private-diffs/scripts/sync_diff.py apply --commit <base>..<head>

# Or test in dry-run mode first:
python .agents/skills/sync-private-diffs/scripts/sync_diff.py apply --commit <commit_hash> --dry-run
```

The script automatically:
1. Replaces `package` and `import` declarations with `ru.wildberries.pub.*`.
2. Places files in the correct directory under `ru/wildberries/pub/`.
3. Fixes `SendOperationScheduler.kt` for `minSdk = 23`.

### Step 3: Audit Target Repository
Run the automated sanity check:

```bash
python .agents/skills/sync-private-diffs/scripts/sync_diff.py audit
```

Verify:
- 0 occurrences of `ecommerce` in `:library`.
- 0 references to `.gitlab-ci.yml`.
- `SendOperationScheduler.kt` satisfies `minSdk = 23`.

### Step 4: Run Quality Gates
Ensure the public library compiles and all tests pass:

```bash
./gradlew :library:testDebugUnitTest
./gradlew :library:assembleRelease
./gradlew :demoapp:assembleDebug
```

---

## Quick Reference / Commands

| Task | Command |
| :--- | :--- |
| Preview commit | `python .agents/skills/sync-private-diffs/scripts/sync_diff.py preview --commit <sha>` |
| Apply commit | `python .agents/skills/sync-private-diffs/scripts/sync_diff.py apply --commit <sha>` |
| Audit repo | `python .agents/skills/sync-private-diffs/scripts/sync_diff.py audit` |
| Test SDK | `./gradlew :library:testDebugUnitTest` |
| Assemble Release | `./gradlew :library:assembleRelease` |
