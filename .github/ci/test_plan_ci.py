"""Tests for plan_ci.py: how the affected modules are grouped, and what the report step is given."""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import plan_ci  # noqa: E402


class ShardsTest(unittest.TestCase):

    def test_nothing_affected_means_no_groups(self):
        self.assertEqual([], plan_ci.shards(set()))

    def test_one_small_module_is_one_group_with_its_suite_and_floor(self):
        groups = plan_ci.shards({"theme"})
        self.assertEqual(1, len(groups))
        self.assertEqual("theme", groups[0]["modules"])
        self.assertEqual(":theme:test :theme:jacocoTestCoverageVerification", groups[0]["tasks"])

    def test_every_module_spreads_over_the_most_groups_and_runs_each_once(self):
        everything = {plan_ci.key(d) for d, _, _ in plan_ci.MODULES}
        groups = plan_ci.shards(everything)
        self.assertEqual(plan_ci.MAX_SHARDS, len(groups))
        ran = [m for g in groups for m in g["modules"].split()]
        self.assertEqual(sorted(d for d, _, _ in plan_ci.MODULES), sorted(ran))

    def test_the_heaviest_module_is_not_stacked_on_other_heavy_ones(self):
        groups = plan_ci.shards({"profiles", "slides", "calendar", "canvas", "server"})
        with_profiles = next(g for g in groups if "profiles" in g["modules"].split())
        self.assertEqual("profiles", with_profiles["modules"], "Profiles alone outweighs a group")

    def test_the_helper_group_also_runs_the_understanding_eval(self):
        groups = plan_ci.shards({"helper"})
        self.assertIn(":helper:wickEval", groups[0]["tasks"])

    def test_a_module_with_no_row_is_named(self):
        known = {plan_ci.key(d) for d, _, _ in plan_ci.MODULES} | {"composeapp", "strings", "newthing"}
        self.assertEqual(["newthing"], plan_ci.unlisted(known))


class ReadAffectedTest(unittest.TestCase):

    def test_true_and_false_lines_are_told_apart(self):
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
            f.write("theme=true\nsettings=false\n\n")
        try:
            affected, known = plan_ci.read_affected(f.name)
        finally:
            os.unlink(f.name)
        self.assertEqual({"theme"}, affected)
        self.assertEqual({"theme", "settings"}, known)


class ReportInputsTest(unittest.TestCase):

    def test_only_modules_with_results_are_reported(self):
        with tempfile.TemporaryDirectory() as root:
            os.makedirs(f"{root}/theme/build/test-results/test")
            open(f"{root}/theme/build/test-results/test/TEST-x.xml", "w").close()
            os.makedirs(f"{root}/settings/build")
            paths, names = plan_ci.report_inputs(root)
        self.assertEqual("theme/build/test-results/**/TEST-*.xml", paths)
        self.assertEqual("Theme", names)


if __name__ == "__main__":
    unittest.main()
