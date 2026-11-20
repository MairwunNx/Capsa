// Builds the projects the tests of L3–L7 run on, in a temporary folder outside the repository.
// ctxray reads a fixture as a project of its own and skips symlinks that leave it, so each project gets real copies of the skills.
import { cpSync, mkdtempSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

export const repository = resolve(import.meta.dir, "../..");
const skills = join(repository, "plugins/capsa/skills");
const base = mkdtempSync(join(tmpdir(), "capsa-ctxray-"));
const built = new Set<string>();
process.on("exit", () => rmSync(base, { recursive: true, force: true }));

/** The five skills of the plugin, in the order its manifest lists them. */
export const SKILLS = ["capsa", "capsa-setup", "capsa-migrate", "capsa-new", "capsa-review"];

/** Where each layout of T27 puts the skills: the Claude Code plugin is read like .claude/skills, the other harnesses take .agents/skills. */
export const LAYOUTS = { "claude-code": ".claude/skills", agents: ".agents/skills" } as const;
export type Layout = keyof typeof LAYOUTS;

/** The layout a harness gets when a project follows capsa-setup. */
export const layoutOf = (harness: string): Layout => (harness === "claude-code" ? "claude-code" : "agents");

/**
 * Copies ctxray/fixtures/<fixture> twice, into claude-code/ and agents/ of one folder, with the skills in the folder of each layout.
 * The two copies sit side by side, so no harness sees both folders of skills at once. A test picks its copy with cwd.
 */
export function project(fixture: string): string {
  const root = join(base, fixture);
  if (built.has(fixture)) return root;
  built.add(fixture);
  for (const [layout, folder] of Object.entries(LAYOUTS)) {
    const copy = join(root, layout);
    cpSync(join(repository, "ctxray/fixtures", fixture), copy, { recursive: true });
    for (const skill of readdirSync(skills)) cpSync(join(skills, skill), join(copy, folder, skill), { recursive: true });
  }
  return root;
}
