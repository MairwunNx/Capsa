#!/bin/sh
# Records the demo videos of docs/media. Usage and the tools it needs: tools/media/README.md.
#   tools/media/record.sh like android
#   tools/media/record.sh all ios
set -eu
exec python3 "$(dirname "$0")/record.py" "$@"
