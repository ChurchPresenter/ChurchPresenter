import json
import unittest
from unittest.mock import patch

import build_all


class ApiRetryTest(unittest.TestCase):
    def test_read_recovers_from_temporary_network_failure(self):
        with patch.object(build_all, "command", side_effect=[RuntimeError("EOF"), b'{"status":"completed"}']), \
                patch.object(build_all.time, "sleep"):
            self.assertEqual({"status": "completed"}, build_all.api("o/r", "actions/runs/1"))

    def test_writes_are_not_blindly_replayed_after_network_failure(self):
        with patch.object(build_all, "command", side_effect=RuntimeError("EOF")) as command:
            with self.assertRaises(RuntimeError):
                build_all.api("o/r", "git/commits", {"tree": "source"})
        command.assert_called_once()


class SourcePublishingTest(unittest.TestCase):
    def test_verified_tree_uses_a_new_branch_without_force_push(self):
        writes = []

        def fake_api(repo, path, payload=None):
            if payload is not None:
                writes.append((path, payload))
            if path.startswith("git/ref/"):
                return {"object": {"sha": "base"}}
            if path == "git/commits/base":
                return {"tree": {"sha": "tree"}}
            if path.startswith("git/trees/"):
                return {"tree": [{"path": "app.kt", "mode": "100644", "type": "blob", "sha": "blob"}]}
            return {"sha": "commit"}

        def fake_command(args, **kwargs):
            if args[0:2] == ["git", "ls-tree"]:
                return b"100644 blob blob\tapp.kt\0"
            if args[0:2] == ["git", "rev-parse"]:
                return b"tree\n"
            if args[0] == "gh":
                self.assertFalse(json.loads(kwargs["input"])["force"])
            return b""

        with patch.object(build_all, "api", side_effect=fake_api), patch.object(build_all, "command", side_effect=fake_command):
            branch, commit = build_all.publish_source("owner/repo", "builds/base")
        self.assertTrue(branch.startswith("codex/build-"))
        self.assertEqual("commit", commit)
        self.assertEqual(["git/commits", "git/refs"], [path for path, _ in writes])

    def test_tree_mismatch_stops_before_creating_a_commit_or_branch(self):
        def fake_api(repo, path, payload=None):
            self.assertIsNone(payload)
            if path.startswith("git/ref/"):
                return {"object": {"sha": "base"}}
            if path == "git/commits/base":
                return {"tree": {"sha": "remote-tree"}}
            return {"tree": []}

        def fake_command(args, **kwargs):
            return b"different-tree" if args[1] == "rev-parse" else b""

        with patch.object(build_all, "api", side_effect=fake_api), patch.object(build_all, "command", side_effect=fake_command):
            with self.assertRaisesRegex(RuntimeError, "源码校验失败"):
                build_all.publish_source("owner/repo", "builds/base")


class PackageRetryTest(unittest.TestCase):
    def test_capacity_cancellation_is_retried_only_once(self):
        retries = []

        def fake_api(repo, path, payload=None):
            run = {"id": 1, "head_sha": "commit", "html_url": "https://github.com/o/r/actions/runs/1",
                   "status": "completed", "conclusion": "cancelled"}
            if path.startswith("actions/runs?"):
                return {"workflow_runs": [run]}
            if path == "actions/runs/1":
                return run
            if path.endswith("/jobs"):
                return {"jobs": [{"id": 2, "name": "macos-arm64", "status": "completed", "conclusion": "cancelled"}]}
            if path.endswith("/annotations"):
                return [{"message": "Due to capacity constraints, jobs may experience longer queue times."}]
            if path.endswith("/rerun"):
                retries.append(path)
                return {}
            return {"artifacts": []}

        with patch.object(build_all, "api", side_effect=fake_api), patch.object(build_all.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "部分云端构建失败"):
                build_all.wait_for_packages("o/r", "branch", "commit", False)
        self.assertEqual(["actions/jobs/2/rerun"], retries)


if __name__ == "__main__":
    unittest.main()
