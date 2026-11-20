import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, suite, test } from "@mairwunnx/ctxray";
import { LAYOUTS, type Layout, layoutOf, project, repository, SKILLS } from "./lib/projects";

// L3: which harness lists the skills from which folder, the table of capsa-setup, step 5.
// The Claude Code plugin lives outside the project and ctxray reads no plugins, so .claude/skills stands in for it.
const reads: Record<string, Layout[]> = {
  "claude-code": ["claude-code"],
  codex: ["agents"],
  "gemini-cli": ["agents"],
  opencode: ["claude-code", "agents"],
  "cursor-cli": ["claude-code", "agents"],
};

suite("skills", { fixture: project("kotlin") }, () => {
  test.resolve.each(Object.entries(LAYOUTS).map(([layout, folder]) => ({ layout, folder })))(
    "{folder} lists the five skills to the harnesses that read it",
    async ({ harness, layout, resolve }) => {
      const l = await resolve({ cwd: layout });
      if (reads[harness]?.includes(layout as Layout)) expect(l.skills.listed).toInclude(SKILLS);
      else expect(l.skills.listed).not.toInclude(SKILLS);
    },
  );

  // L1 checks Markdown links, but the skills name their references in backticks: `references/kotlin.md`.
  // Every command reads them from the capsa skill, so each name must lead to a file there.
  test.resolve("every file in references/ that a skill names exists", async ({ resolve }) => {
    await resolve({});
    const folder = join(repository, "plugins/capsa/skills");
    const missing = SKILLS.flatMap((skill) => {
      const dir = join(folder, skill);
      const files = [join(dir, "SKILL.md"), ...(existsSync(join(dir, "references")) ? readdirSync(join(dir, "references")).map((f) => join(dir, "references", f)) : [])];
      return files.flatMap((file) =>
        [...readFileSync(file, "utf8").matchAll(/references\/[\w.-]+\.md/g)]
          .map((match) => match[0])
          .filter((name) => !existsSync(join(folder, "capsa", name)))
          .map((name) => `${skill}: ${name}`),
      );
    });
    expect(missing).toEqual([]);
  });

  test.resolve("each skill is listed once and loads only on demand", async ({ harness, resolve }) => {
    const l = await resolve({ cwd: layoutOf(harness) });
    const capsa = l.skills.listed.filter((skill) => SKILLS.includes(skill.name));
    expect(capsa).toHaveLength(SKILLS.length);
    expect(l.skills.loaded).not.toInclude(SKILLS);
  });
});
