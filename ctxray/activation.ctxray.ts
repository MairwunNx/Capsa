import { expect, suite, test } from "@mairwunnx/ctxray";
import { project, SKILLS } from "./lib/projects";

// L6: does the model pick a Capsa skill by the request alone? It needs a model: --backend live records, --backend ci replays.
// The answers are recorded for Claude Code, in .claude/skills: run L6 and L7 with --harness claude-code.
suite("activation", { fixture: project("kotlin") }, () => {
  test.activate(
    "requests about screens on Capsa pick a Capsa skill, other requests don't",
    {
      cwd: "claude-code",
      samples: 5,
      positive: [
        "Move the search screen in feature/search to Capsa.",
        "Add a like button to the profile screen: a new capability with its slice and tests.",
        "The profile screen on Capsa should count notes again when the user returns to it. Where does that go?",
        "Review feature/profile for violations of Capability Slice Architecture.",
      ],
      negative: ["Bump the Kotlin version in gradle/libs.versions.toml to the newest patch release.", "Add a section on how to build this project to AGENTS.md."],
    },
    ({ runs }) => {
      expect(runs.positive).anyOf(SKILLS).toActivate();
      // 10 runs can't prove 0.2 even when no run loads a skill: 0 of 10 has an upper bound of 0.28.
      expect(runs.negative).anyOf(SKILLS).not.toActivate({ max: 0.3 });
    },
  );
});
