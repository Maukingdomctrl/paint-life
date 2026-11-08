#!/usr/bin/env bash
# Blocks Claude from running git commit / git push. The user does these manually.
input=$(cat)
if echo "$input" | grep -Eq '\bgit\b[^"]*\b(commit|push)\b'; then
  echo "Blocked: the user commits and pushes manually. Do not run git commit or git push; tell the user the work is ready to commit instead." >&2
  exit 2
fi
exit 0
