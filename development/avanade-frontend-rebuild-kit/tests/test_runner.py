import contextlib
import io
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run

class RunnerTests(unittest.TestCase):
    def test_docs_embedded(self):
        with tempfile.TemporaryDirectory() as tmp:
            prompt = run.make_prompt("plan", Path(tmp))
            self.assertIn("SKILL-DISCOVERY-AND-INVOCATION", prompt)
            self.assertIn("APENAS AS FASES 1", prompt)
            self.assertIn("AVANADE-VISUAL-REFERENCE", prompt)

    def test_approval_gate_rejects_yes(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(run.main(["--yes"]), 2)

    def test_missing_agent_blocks(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(run.shutil, "which", return_value=None):
                with contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(run.main(["--project", tmp]), 2)

    def test_commands_have_read_only_plan(self):
        self.assertEqual(run.agent_command("codex", "codex", "plan")[-2:], ["read-only", "-"])
        self.assertIn("plan", run.agent_command("claude", "claude", "plan"))
        self.assertIn("workspace-write", run.agent_command("codex", "codex", "implement"))

if __name__ == "__main__":
    unittest.main()
