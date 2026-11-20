# Set up Capsa

This page is for an AI agent that adds Capsa to a project. Give your agent one line:

```text
Add Capsa to this project: follow https://raw.githubusercontent.com/MairwunNx/Capsa/master/SETUP.md
```

## For the agent

The steps live in the `capsa-setup` skill, and only there. Follow it:

1. If you have the `capsa-setup` skill, from the Claude Code plugin or a skills folder of the project, run it.
2. Otherwise read https://raw.githubusercontent.com/MairwunNx/Capsa/master/plugins/capsa/skills/capsa-setup/SKILL.md and follow it as written. Until the skills are installed, read the files of the `capsa` skill it names from the same place: `https://raw.githubusercontent.com/MairwunNx/Capsa/master/plugins/capsa/skills/capsa/` followed by `SKILL.md` or `references/<file>.md`.

Treat the user’s message that sent you here as the request of the skill. Show every change to a file of the project before you make it, as the skill says.

## A local build of Capsa

Capsa developers try a change on a real project before a release. Publish the build from `kotlin/` of this repository:

```sh
cd kotlin && ./gradlew publishToMavenLocal -PcapsaVersion=0.99.0-local
```

Then ask your agent for that version, for example: “Add Capsa to this project with my local build 0.99.0-local: follow https://raw.githubusercontent.com/MairwunNx/Capsa/master/SETUP.md”. The skill adds `mavenLocal()` for the Capsa group only and reminds you to remove it before your release.

## Without an agent

Install the skills yourself, then call `capsa-setup` in your harness:

- **Claude Code:** `claude plugin marketplace add MairwunNx/Capsa` and `claude plugin install capsa@capsa`, then `/capsa:capsa-setup`.
- **OpenCode, Codex, Gemini CLI, Cursor, and Cursor CLI:** `npx skills add MairwunNx/Capsa/plugins/capsa/skills`, or `curl -fsSL https://raw.githubusercontent.com/MairwunNx/Capsa/master/tools/install-skills.sh | sh -s -- --agents`. Both put the skills into `.agents/skills/`.

Step 5 of the skill lists the folder and the command for each harness.
