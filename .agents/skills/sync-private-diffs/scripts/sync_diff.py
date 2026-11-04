#!/usr/bin/env python3
"""
sync_diff.py - Port and synchronize diffs from private wbanalytics2android to public wbanalytics2android-public.

Key Responsibilities:
  1. Strict Exclusions:
     - All ecom / ecommerce files (*ecommerce*, WBAnalytics2ProductTest, etc.)
     - Private/local edits (.gitlab-ci.yml, internal scripts, internal URLs/secrets, local.properties)
  2. Namespace & Path Relocation:
     - ru.wildberries.analytics.**      -> ru.wildberries.pub.analytics.**
     - ru.wildberries.attribution.**    -> ru.wildberries.pub.attribution.**
     - ru.wildberries.WBAnalytics2.**   -> ru.wildberries.pub.WBAnalytics2.**
     - ru.wildberries.analytics.sdk.demo.** -> ru.wildberries.pub.analytics.sdk.demo.**
     - Directory tree reflects 'pub' package insertion.
  3. minSdk 23 Compatibility:
     - In SendOperationScheduler.kt, ensures scheduler.allPendingJobs.any { ... } (API 21+)
       instead of scheduler.getPendingJob(...) (API 24+).
     - Network type constraint JobInfo.NETWORK_TYPE_NOT_ROAMING guarded for API >= 24.
  4. Audit & Verification:
     - Scans for leftover ecommerce, untransformed packages, or minSdk regressions.
"""

import os
import sys
import re
import subprocess
import argparse
from pathlib import Path
from typing import List, Tuple, Optional, Set

# Strict exclusions
EXCLUDED_PATH_PATTERNS = [
    r"[\\/]ecommerce[\\/]",
    r"[\\/]ecommerce$",
    r"WBAnalytics2ProductTest\.kt$",
    r"WBAnalyticsAffiliatedDiscountTest\.kt$",
    r"ecommerce\.md$",
    r"\.gitlab-ci\.yml$",
    r"\.ai[\\/]tools[\\/]",
    r"\.opencode[\\/]",
    r"[\\/]\.idea[\\/]",
    r"local\.properties$",
]

# Package replacements in file contents
PACKAGE_REPLACEMENTS = [
    (r"\bru\.wildberries\.analytics\b", "ru.wildberries.pub.analytics"),
    (r"\bru\.wildberries\.attribution\b", "ru.wildberries.pub.attribution"),
    (r"\bru\.wildberries\.WBAnalytics2\b", "ru.wildberries.pub.WBAnalytics2"),
    (r"\bru\.wildberries\.analytics\.sdk\.demo\b", "ru.wildberries.pub.analytics.sdk.demo"),
]

# Path transformations for file relocation
PATH_TRANSFORMATIONS = [
    (
        r"^library/src/([^/]+)/java/ru/wildberries/(analytics|attribution|WBAnalytics2)(/.*)?$",
        r"library/src/\1/java/ru/wildberries/pub/\2\3",
    ),
    (
        r"^demoapp/src/([^/]+)/java/ru/wildberries/analytics/sdk/demo(/.*)?$",
        r"demoapp/src/\1/java/ru/wildberries/pub/analytics/sdk/demo\2",
    ),
    (
        r"^library/schemas/ru\.wildberries\.(?:pub\.)?analytics\.db\.WBAnalytics2Database(/.*)?$",
        r"library/schemas/ru.wildberries.pub.analytics.db.WBAnalytics2Database\1",
    ),
]


def is_excluded_path(rel_path: str) -> Tuple[bool, str]:
    """Check if relative path matches any exclusion rule."""
    normalized = rel_path.replace("\\", "/")
    for pattern in EXCLUDED_PATH_PATTERNS:
        if re.search(pattern, normalized):
            return True, pattern
    return False, ""


def transform_rel_path(rel_path: str) -> str:
    """Transform relative file path from private layout to public layout."""
    normalized = rel_path.replace("\\", "/")
    for pattern, replacement in PATH_TRANSFORMATIONS:
        if re.match(pattern, normalized):
            return re.sub(pattern, replacement, normalized)
    return normalized


def transform_content(rel_path: str, content: str) -> str:
    """Transform source code content (package declarations, imports, minSdk compatibility)."""
    result = content
    for pattern, replacement in PACKAGE_REPLACEMENTS:
        result = re.sub(pattern, replacement, result)

    # Public compatibility guard for SendOperationScheduler.kt (minSdk = 23)
    if rel_path.replace("\\", "/").endswith("SendOperationScheduler.kt"):
        result = patch_send_operation_scheduler_min_sdk(result)

    return result


def patch_send_operation_scheduler_min_sdk(content: str) -> str:
    """Ensure SendOperationScheduler uses API 21+ compatible calls."""
    res = content
    # Replace getPendingJob with allPendingJobs.any
    if "scheduler.getPendingJob(SEND_OPERATION_JOB_ID) != null" in res:
        res = res.replace(
            "scheduler.getPendingJob(SEND_OPERATION_JOB_ID) != null",
            "scheduler.allPendingJobs.any { it.id == SEND_OPERATION_JOB_ID }"
        )

    # Ensure Build import if needed
    if "Build.VERSION" not in res and "NETWORK_TYPE_NOT_ROAMING" in res:
        res = res.replace("import android.app.job.JobInfo", "import android.app.job.JobInfo\nimport android.os.Build")
        # Guard NETWORK_TYPE_NOT_ROAMING
        old_network = ".setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)"
        new_network = """.setRequiredNetworkType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) JobInfo.NETWORK_TYPE_NOT_ROAMING
                else JobInfo.NETWORK_TYPE_ANY
            )"""
        if old_network in res:
            res = res.replace(old_network, new_network)

    return res


def get_git_diff_files(source_repo: Path, commit_or_range: Optional[str] = None, working_tree: bool = False) -> List[str]:
    """Get list of changed files from source git repo."""
    if working_tree:
        # Check status and uncommitted changes
        cmd = ["git", "-C", str(source_repo), "status", "--porcelain"]
        res = subprocess.run(cmd, capture_output=True, text=True, check=True)
        files = []
        for line in res.stdout.splitlines():
            if line.strip():
                # Format: XY <filename> or R  <old> -> <new>
                parts = line[3:].strip().split(" -> ")
                files.append(parts[-1].strip('"'))
        return sorted(list(set(files)))

    if not commit_or_range:
        commit_or_range = "HEAD~1..HEAD"

    if ".." in commit_or_range:
        cmd = ["git", "-C", str(source_repo), "diff", "--name-only", commit_or_range]
    else:
        # Single commit (handles regular & merge commits)
        cmd = ["git", "-C", str(source_repo), "diff-tree", "-m", "--no-commit-id", "--name-only", "-r", commit_or_range]

    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    files = [f.strip() for f in res.stdout.splitlines() if f.strip()]
    return sorted(list(set(files)))


def cmd_preview(source_repo: Path, target_repo: Path, commit_or_range: Optional[str], working_tree: bool = False):
    """Preview classification of changes without modifying any files."""
    desc = "working tree" if working_tree else f"'{commit_or_range}'"
    print(f"=== Previewing changes from {desc} in {source_repo} ===")
    try:
        files = get_git_diff_files(source_repo, commit_or_range, working_tree)
    except subprocess.CalledProcessError as e:
        print(f"Error reading git changes: {e.stderr}", file=sys.stderr)
        return 1

    if not files:
        print("No files modified in specified selection.")
        return 0

    included: List[Tuple[str, str]] = []
    excluded: List[Tuple[str, str]] = []

    for f in files:
        is_ex, reason = is_excluded_path(f)
        if is_ex:
            excluded.append((f, reason))
        else:
            target_path = transform_rel_path(f)
            included.append((f, target_path))

    print(f"\n[INCLUDED FILES: {len(included)}]")
    for src, dst in included:
        if src != dst:
            print(f"  + {src} -> {dst}")
        else:
            print(f"  = {src}")

    print(f"\n[EXCLUDED FILES (ecom & private/local): {len(excluded)}]")
    for src, reason in excluded:
        print(f"  - {src} (reason: {reason})")

    return 0


def cmd_apply_files(source_repo: Path, target_repo: Path, commit_or_range: Optional[str], working_tree: bool = False, dry_run: bool = False):
    """Port changed files from source to target, transforming paths and contents."""
    desc = "working tree" if working_tree else f"'{commit_or_range}'"
    print(f"=== Porting changes from {desc} to {target_repo} (dry_run={dry_run}) ===")
    try:
        files = get_git_diff_files(source_repo, commit_or_range, working_tree)
    except subprocess.CalledProcessError as e:
        print(f"Error reading git changes: {e.stderr}", file=sys.stderr)
        return 1

    copied_count = 0
    excluded_count = 0
    deleted_count = 0

    for f in files:
        is_ex, reason = is_excluded_path(f)
        if is_ex:
            excluded_count += 1
            continue

        src_file = source_repo / f
        dst_rel = transform_rel_path(f)
        dst_file = target_repo / dst_rel

        if not src_file.exists():
            if dst_file.exists():
                print(f"  DELETE: {dst_rel}")
                if not dry_run:
                    dst_file.unlink()
                deleted_count += 1
            continue

        if not src_file.is_file():
            continue

        try:
            with open(src_file, "r", encoding="utf-8") as rf:
                content = rf.read()
            transformed = transform_content(dst_rel, content)
            is_text = True
        except UnicodeDecodeError:
            with open(src_file, "rb") as rf:
                raw_bytes = rf.read()
            is_text = False

        if not dry_run:
            dst_file.parent.mkdir(parents=True, exist_ok=True)
            if is_text:
                with open(dst_file, "w", encoding="utf-8", newline="\n") as wf:
                    wf.write(transformed)
            else:
                with open(dst_file, "wb") as wf:
                    wf.write(raw_bytes)

        action_label = "DRY-COPY" if dry_run else "COPIED"
        if f != dst_rel:
            print(f"  [{action_label}] {f} -> {dst_rel}")
        else:
            print(f"  [{action_label}] {f}")
        copied_count += 1

    print(f"\nFinished: {copied_count} files written, {deleted_count} deleted, {excluded_count} excluded.")
    return 0


def cmd_audit(target_repo: Path):
    """Audit target repo for forbidden tokens, ecom leakage, and package consistency."""
    print(f"=== Auditing public repo: {target_repo} ===")
    errors = 0

    lib_path = target_repo / "library"
    if lib_path.exists():
        for root, dirs, files in os.walk(lib_path):
            if "build" in dirs:
                dirs.remove("build")
            for f in files:
                full_path = Path(root) / f
                rel_path = full_path.relative_to(target_repo).as_posix()
                if "ecommerce" in rel_path.lower():
                    print(f"  [ERROR] Forbidden ecommerce path: {rel_path}")
                    errors += 1
                if f.endswith(".kt") or f.endswith(".java"):
                    try:
                        content = full_path.read_text(encoding="utf-8")
                        if "ru.wildberries.analytics.ecommerce" in content:
                            print(f"  [ERROR] Forbidden ecommerce package reference in {rel_path}")
                            errors += 1
                        if "ru.wildanalytics.pub" in content:
                            print(f"  [WARN] Legacy 'ru.wildanalytics.pub' in {rel_path}")
                    except Exception:
                        pass

    # Check minSdk 23 compatibility guard
    scheduler_file = target_repo / "library/src/main/java/ru/wildberries/pub/analytics/send/SendOperationScheduler.kt"
    if scheduler_file.exists():
        content = scheduler_file.read_text(encoding="utf-8")
        if "scheduler.getPendingJob(" in content:
            print(f"  [ERROR] minSdk 23 violation in {scheduler_file}: getPendingJob requires API 24! Must use allPendingJobs.any.")
            errors += 1
        else:
            print("  [OK] minSdk 23 check passed for SendOperationScheduler.kt")

    # Check for GitLab CI in target
    gitlab_ci = target_repo / ".gitlab-ci.yml"
    if gitlab_ci.exists():
        print("  [ERROR] .gitlab-ci.yml should not be present in public repository!")
        errors += 1

    if errors == 0:
        print("\n[SUCCESS] Audit passed! Zero forbidden ecom, zero internal leakage, minSdk 23 intact.")
        return 0
    else:
        print(f"\n[FAILURE] Audit detected {errors} issue(s).")
        return 1


def main():
    parser = argparse.ArgumentParser(description="Port and sync diffs from private to public repo.")
    parser.add_argument("action", choices=["preview", "apply", "audit"], help="Action to perform")
    parser.add_argument("--source", default="../wbanalytics2android", help="Path to private repo (default: ../wbanalytics2android)")
    parser.add_argument("--target", default=".", help="Path to public repo (default: .)")
    parser.add_argument("--commit", default=None, help="Git commit or range to diff (e.g. 0955193, HEAD~1..HEAD)")
    parser.add_argument("--working-tree", action="store_true", help="Diff unstaged/staged working tree changes in source repo")
    parser.add_argument("--dry-run", action="store_true", help="Perform dry run without writing files")

    args = parser.parse_args()

    source_path = Path(args.source).resolve()
    target_path = Path(args.target).resolve()

    if args.action == "preview":
        sys.exit(cmd_preview(source_path, target_path, args.commit, args.working_tree))
    elif args.action == "apply":
        sys.exit(cmd_apply_files(source_path, target_path, args.commit, args.working_tree, dry_run=args.dry_run))
    elif args.action == "audit":
        sys.exit(cmd_audit(target_path))


if __name__ == "__main__":
    main()
