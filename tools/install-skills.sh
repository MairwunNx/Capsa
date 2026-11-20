#!/bin/sh
# Installs the agent skills of Capsa into a project.
#
#   curl -fsSL https://raw.githubusercontent.com/MairwunNx/Capsa/master/tools/install-skills.sh | sh
#   curl -fsSL https://raw.githubusercontent.com/MairwunNx/Capsa/master/tools/install-skills.sh | sh -s -- --agents
#
# The script downloads capsa-skills.zip from the newest release of MairwunNx/Capsa and unpacks
# the five skills into .claude/skills/ of the current directory. Run it from the project root.
set -eu

REPO="MairwunNx/Capsa"
SKILLS="capsa capsa-setup capsa-migrate capsa-new capsa-review"
dest=".claude/skills"
version=""
url=""

usage() {
    cat <<'EOF'
Usage: install-skills.sh [--agents | --dir PATH] [--global] [--version X.Y.Z] [--url URL]

  (no flag)        .claude/skills/ of the project: Claude Code without the plugin
  --agents         .agents/skills/ of the project: OpenCode, Codex, Gemini CLI, Cursor, Cursor CLI
  --dir PATH       any other skills folder
  --global         the folder in your home directory instead of the project: ~/.claude/skills/ or ~/.agents/skills/
  --version X.Y.Z  the skills of that release instead of the newest one
  --url URL        take capsa-skills.zip from this URL or file:// path

Keep the skills in one folder only: OpenCode and Cursor read both .claude/skills/ and .agents/skills/,
and a skill in both makes OpenCode warn and Cursor may load it twice.
EOF
}

global=false
while [ $# -gt 0 ]; do
    case "$1" in
        --agents) dest=".agents/skills" ;;
        --dir) [ $# -ge 2 ] || { usage >&2; exit 2; }; dest="$2"; shift ;;
        --global) global=true ;;
        --version) [ $# -ge 2 ] || { usage >&2; exit 2; }; version="${2#v}"; shift ;;
        --url) [ $# -ge 2 ] || { usage >&2; exit 2; }; url="$2"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "install-skills: unknown option $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

if $global; then
    case "$dest" in
        .claude/skills|.agents/skills) dest="$HOME/$dest" ;;
        *) echo "install-skills: --global works with the default folder or --agents" >&2; exit 2 ;;
    esac
fi

for tool in curl unzip; do
    command -v "$tool" >/dev/null 2>&1 || { echo "install-skills: $tool is missing" >&2; exit 1; }
done

if [ -z "$url" ]; then
    if [ -n "$version" ]; then
        url="https://github.com/$REPO/releases/download/v$version/capsa-skills.zip"
    else
        # Releases before 1.0.0 are pre-releases, and /releases/latest skips them, so ask for the newest release of any kind.
        url=$(curl -fsSL "https://api.github.com/repos/$REPO/releases?per_page=1" |
            sed -n 's/.*"browser_download_url": *"\([^"]*\/capsa-skills\.zip\)".*/\1/p' | head -n 1)
        if [ -z "$url" ]; then
            echo "install-skills: no release of $REPO has capsa-skills.zip yet." >&2
            echo "Copy the folders of plugins/capsa/skills/ from https://github.com/$REPO instead." >&2
            exit 1
        fi
    fi
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT INT TERM

echo "Downloading $url"
curl -fsSL "$url" -o "$tmp/capsa-skills.zip"
unzip -q "$tmp/capsa-skills.zip" -d "$tmp/skills"

for skill in $SKILLS; do
    [ -f "$tmp/skills/$skill/SKILL.md" ] || { echo "install-skills: the archive has no $skill skill" >&2; exit 1; }
done

mkdir -p "$dest"
for skill in $SKILLS; do
    rm -rf "${dest:?}/$skill"
    cp -R "$tmp/skills/$skill" "$dest/$skill"
done
echo "Installed $SKILLS into $dest"

case "$dest" in
    *.claude/skills) other=$(echo "$dest" | sed 's/\.claude\/skills$/.agents\/skills/') ;;
    *.agents/skills) other=$(echo "$dest" | sed 's/\.agents\/skills$/.claude\/skills/') ;;
    *) other="" ;;
esac
if [ -n "$other" ] && [ -f "$other/capsa/SKILL.md" ]; then
    echo "Warning: $other holds the Capsa skills too. OpenCode warns about each name and Cursor may load each skill twice: remove one copy." >&2
fi
