"""Tests for changed_tests.py: python3 -m unittest discover -s .github/ci"""
import unittest

from changed_tests import task_for, test_classes


def reader(files):
    return lambda path: files[path]


class TestClassesTest(unittest.TestCase):

    def test_a_module_test_is_named_by_its_package_line_and_grouped_under_its_task(self):
        path = "lower-third/src/test/kotlin/org/churchpresenter/lowerthird/LowerThirdTabTest.kt"
        groups = test_classes([path], reader({path: "package org.churchpresenter.lowerthird\n\nclass X"}))
        self.assertEqual({":lower-third:test": ["org.churchpresenter.lowerthird.LowerThirdTabTest"]}, groups)

    def test_the_package_line_wins_over_the_directory(self):
        path = "diagnostics/src/test/kotlin/org/elsewhere/HungTestReporterTest.kt"
        groups = test_classes([path], reader({path: "// header\npackage org.churchpresenter.diagnostics\n"}))
        self.assertEqual(["org.churchpresenter.diagnostics.HungTestReporterTest"], groups[":diagnostics:test"])

    def test_compose_app_tests_run_in_jvm_test(self):
        path = "composeApp/src/jvmTest/kotlin/org/churchpresenter/app/churchpresenter/MainLogicTest.kt"
        groups = test_classes([path], reader({path: "package org.churchpresenter.app.churchpresenter"}))
        self.assertEqual([":composeApp:jvmTest"], list(groups))

    def test_screenshot_suites_helpers_and_main_sources_are_left_out(self):
        paths = [
            "composeApp/src/jvmTest/kotlin/a/b/SongsTabScreenshotTest.kt",
            "lower-third/src/testFixtures/kotlin/a/b/LowerThirdTabTestSupport.kt",
            "lower-third/src/test/kotlin/a/b/LowerThirdTabTestSupport.kt",
            "songs/src/main/kotlin/a/b/SongsTest.kt",
            "README.md",
        ]
        self.assertEqual({}, test_classes(paths, reader({p: "package a.b" for p in paths})))

    def test_a_file_without_a_package_is_named_alone_and_classes_are_deduplicated(self):
        a = "live-show/src/test/kotlin/x/CueTest.kt"
        b = "live-show/src/test/kotlin/y/CueTest.kt"
        groups = test_classes([a, b], reader({a: "class CueTest", b: "class CueTest"}))
        self.assertEqual({":live-show:test": ["CueTest"]}, groups)

    def test_a_file_that_cannot_be_read_is_skipped(self):
        def unreadable(_):
            raise OSError("gone")
        self.assertEqual({}, test_classes(["qa/src/test/kotlin/a/QATest.kt"], unreadable))

    def test_only_the_source_set_that_runs_tests_maps_to_a_task(self):
        self.assertIsNone(task_for("composeApp", "test"))
        self.assertIsNone(task_for("songs", "jvmTest"))
        self.assertEqual(":songs:test", task_for("songs", "test"))


if __name__ == "__main__":
    unittest.main()
