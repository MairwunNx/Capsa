import { expect, suite, test } from "@mairwunnx/ctxray";
import { project } from "./lib/projects";

// L7: the commands of T26 on a project, end to end. Like L6, they need a model and run on Claude Code in .claude/skills.
// Every prompt agrees to the changes in advance: a headless agent has nobody to ask.
const options = { cwd: "claude-code", turns: 40, timeout: 900_000 };

type Run = { tools: { name: string; input: unknown }[] };

// ctxray 0.1.0 counts a skill only when the Skill tool loads it, and builds `changed` from the paths in tool calls.
// The commands read the capsa skill as files and often write through the shell, so the checks look at the tool calls.
const calls = (run: Run) => JSON.stringify(run.tools.map((tool) => tool.input));
const readsCapsa = (run: Run) => /skills\/capsa\/(SKILL\.md|references)/.test(calls(run));
const edits = (run: Run) => run.tools.filter((tool) => ["Edit", "MultiEdit", "Write", "NotebookEdit"].includes(tool.name));

suite("commands on a Kotlin project", { fixture: project("kotlin") }, () => {
  test.scenario(
    "capsa-setup adds Capsa to a module",
    { ...options, prompt: "Use the capsa-setup skill: add Capsa to :feature:search. I agree to every change in advance, don't ask." },
    ({ run }) => {
      expect(run.skills).toInclude("capsa-setup");
      expect(readsCapsa(run)).toBe(true);
      expect(calls(run)).toMatch("feature/search/build.gradle.kts");
    },
  );

  test.scenario(
    "capsa-migrate moves a view model screen",
    {
      ...options,
      prompt:
        "Use the capsa-migrate skill: move the search screen in feature/search to Capsa. Keep it on CapsaViewModel, no switch. " +
        "Add Capsa to the module if it's missing. I agree to every change in advance, don't ask.",
    },
    ({ run }) => {
      expect(run.skills).toInclude("capsa-migrate");
      expect(readsCapsa(run)).toBe(true);
      expect(calls(run)).toMatch(/business\/capabilities\/\w+\.kt/);
      expect(calls(run)).toMatch("CapsaViewModel");
    },
  );

  test.scenario(
    "capsa-new creates a screen",
    {
      ...options,
      prompt: "Use the capsa-new skill: create a countdown timer screen in :feature:timer, on CapsaViewModel. I agree to every change in advance, don't ask.",
    },
    ({ run }) => {
      expect(run.skills).toInclude("capsa-new");
      expect(readsCapsa(run)).toBe(true);
      expect(calls(run)).toMatch(/business\/capabilities\/[\w/]+\.kt/);
      expect(calls(run)).toMatch("capsaHarness");
    },
  );

  test.scenario(
    "capsa-review reports and changes nothing",
    { ...options, prompt: "Use the capsa-review skill on feature/profile." },
    ({ run }) => {
      expect(run.skills).toInclude("capsa-review");
      expect(calls(run)).toMatch("rules.md");
      expect(calls(run)).toMatch("pitfalls.md");
      expect(edits(run)).toHaveLength(0);
      expect(run.changed).toHaveLength(0);
    },
  );
});

suite("commands on a web project", { fixture: project("web") }, () => {
  test.scenario(
    "capsa-new stops on a platform Capsa doesn't support",
    { ...options, prompt: "Use the capsa-new skill: create a counter screen on Capsa. I agree to every change in advance, don't ask." },
    ({ run }) => {
      expect(run.skills).toInclude("capsa-new");
      expect(edits(run)).toHaveLength(0);
      expect(run.changed).toHaveLength(0);
    },
  );
});
