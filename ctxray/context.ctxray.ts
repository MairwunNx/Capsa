import { expect, suite, test } from "@mairwunnx/ctxray";
import { layoutOf, project, SKILLS } from "./lib/projects";

suite("context", { fixture: project("kotlin") }, () => {
  // L4: the harness on the mock model, in the layout capsa-setup gives it.
  test.assemble("a session starts with the five descriptions only", async ({ harness, assemble }) => {
    const l = await assemble({ cwd: layoutOf(harness) });
    expect(l.skills.listed).toInclude(SKILLS);
    expect(l.skills.loaded).not.toInclude(SKILLS);
    expect(l.duplicates).toHaveLength(0);
    const descriptions = l.skills.listed.filter((skill) => SKILLS.includes(skill.name)).reduce((sum, skill) => sum + skill.tokens, 0);
    expect(descriptions).toBeLessThan(1_000);
  });

  // L5: the test plays the model and loads a skill the way the harness does.
  test.wire("a command loads its own skill and nothing else", async ({ harness, startSession }) => {
    const session = await startSession({ cwd: layoutOf(harness) });
    await session.firstRequest();
    // Codex and Cursor CLI have no skill tool: their model reads SKILL.md itself. Read takes a path from the project root.
    if (harness === "codex" || harness === "cursor-cli") await session.model.calls("Read", { path: "agents/.agents/skills/capsa-migrate/SKILL.md" });
    else await session.model.calls("Skill", { name: "capsa-migrate" });
    const next = await session.nextRequest();
    expect.on(["claude-code", "gemini-cli", "opencode"], () => expect(next.added.skills).toEqual(["capsa-migrate"]));
    // ctxray 0.1.0 counts no skill that Codex or Cursor CLI reads as a file, so the test looks for its text in the request.
    expect.on(["codex", "cursor-cli"], () => expect(JSON.stringify(next.raw)).toMatch("# capsa-migrate"));
  });
});
