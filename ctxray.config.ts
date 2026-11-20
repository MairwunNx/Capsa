import { defineConfig } from "@mairwunnx/ctxray";

// ctxray tests the agent skills of plugins/capsa on every harness capsa-setup installs them for.
// The tests live in ctxray/, the projects they run on in ctxray/fixtures/, and ctxray.lock pins the harness builds.
export default defineConfig({
  harnesses: {
    "claude-code": {},
    codex: {},
    "gemini-cli": {},
    opencode: {},
    // Cursor CLI launches only as its undocumented local build, so ctxray asks for the switch.
    "cursor-cli": { experimental: true },
  },
  backends: { ci: "replay", live: "real" },
  policies: [
    {
      id: "plugin-files",
      requireFiles: [
        ".claude-plugin/marketplace.json",
        "plugins/capsa/.claude-plugin/plugin.json",
        "plugins/capsa/skills/capsa/SKILL.md",
        "plugins/capsa/skills/capsa-setup/SKILL.md",
        "plugins/capsa/skills/capsa-migrate/SKILL.md",
        "plugins/capsa/skills/capsa-new/SKILL.md",
        "plugins/capsa/skills/capsa-review/SKILL.md",
      ],
    },
  ],
  lint: {
    // L1 reads the skills through the links in ctxray/layouts/, so a warning about a skill fails the run.
    errorOnWarnings: true,
    overrides: [
      // The instructions and skills for work on this repository serve Claude Code alone, and the fixtures are projects of their own.
      { include: ["CLAUDE.md", "AGENTS.md", ".claude/**", "ctxray/fixtures/**"], rules: { "harness-differences": "off" } },
    ],
  },
});
