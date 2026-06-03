#!/usr/bin/env bash
#
# Regenerates project_dump.txt: a single text file containing every relevant
# source file in the repo, each prefixed with its path. Used to hand full
# project context to tools in one paste.
#
# Design promises:
#   - Overwrites, never appends.
#   - Reads and writes every matched file, or fails loudly. No silent skips.
#   - Counts files found against files written and halts if they disagree.
#   - Refuses to leave behind an empty or suspiciously tiny dump.

# Stop on any error, on use of an unset variable, and make a failing command
# in a pipeline fail the whole pipeline. This is what turns silent problems
# into loud ones.
set -euo pipefail

out="project_dump.txt"
repo_root="$(pwd)"
separator="$(printf '=%.0s' {1..80})"

# Loud delete. If the old dump exists and cannot be removed, stop now rather
# than risk appending to or half-overwriting a stale file.
if [[ -f "$out" ]]; then
  rm -f "$out"
  if [[ -f "$out" ]]; then
    echo "ERROR: could not delete $out. Close whatever is holding it open and retry." >&2
    exit 1
  fi
fi

# Folders whose contents are build output or dependencies, never dumped.
prune_dirs=(target node_modules dist .angular build .git .idea)

# Build the find prune expression from the list above.
prune_expr=()
for d in "${prune_dirs[@]}"; do
  prune_expr+=(-name "$d" -o)
done
# Drop the trailing -o.
unset 'prune_expr[${#prune_expr[@]}-1]'

# File patterns we consider real project content. Extend this list when the
# project gains a new kind of file. A whitelist keeps binary junk out of a
# text dump while staying under our control.
name_patterns=(
  '*.java' '*.ts' '*.html' '*.scss' '*.css'
  '*.xml' '*.properties' '*.json' '*.md'
  '*.yml' '*.yaml' '*.sql'
  '*.gitignore' '*.gitattributes' 'Dockerfile' 'Caddyfile'
  '.gitignore' '.gitattributes'
)

# Build the find name expression from the patterns above.
name_expr=()
for p in "${name_patterns[@]}"; do
  name_expr+=(-name "$p" -o)
done
unset 'name_expr[${#name_expr[@]}-1]'

# Collect the matching files into an array. We search backend and frontend
# plus a handful of root config files. -print0 / read -d '' handles any path
# with spaces safely.
files=()
while IFS= read -r -d '' f; do
  files+=("$f")
done < <(
  find backend frontend \
    \( "${prune_expr[@]}" \) -prune -o \
    -type f \( "${name_expr[@]}" \) -print0
)

# Also pick up root-level config that lives outside backend/ and frontend/.
for rootfile in docker-compose.yml .gitignore .gitattributes ROADMAP.md README.md; do
  if [[ -f "$rootfile" ]]; then
    files+=("$rootfile")
  fi
done

# Sort and de-duplicate.
IFS=$'\n' files=($(printf '%s\n' "${files[@]}" | sort -u)); unset IFS

found_count=${#files[@]}
if (( found_count == 0 )); then
  echo "ERROR: no files matched. Are you running this from the repo root?" >&2
  exit 1
fi

# Write the dump. Each file either contributes its content or, if it cannot
# be read, a loud in-file marker so a missing file is impossible to overlook.
written_count=0
{
  for f in "${files[@]}"; do
    # Normalize to a repo-relative path for the header.
    rel="${f#./}"
    echo "$separator"
    echo "FILE: $rel"
    echo "$separator"
    if [[ -r "$f" ]]; then
      cat "$f"
    else
      echo ">>> ERROR: FILE EXISTS BUT COULD NOT BE READ. CONTENT MISSING. <<<"
      echo "UNREADABLE FILE: $rel" >&2
    fi
    echo ""
    written_count=$((written_count + 1))
  done
} > "$out"

# Cross-check: every file we found must have been written. If these disagree,
# something was dropped and the dump is not trustworthy.
if (( written_count != found_count )); then
  echo "ERROR: found $found_count files but wrote $written_count. Dump is incomplete." >&2
  exit 1
fi

# Sanity floor: a real FITT dump is well over 50 KB. Anything tiny means the
# read silently produced almost nothing.
size_bytes=$(wc -c < "$out")
if (( size_bytes < 50000 )); then
  echo "ERROR: dump is only $size_bytes bytes, far smaller than expected. Something went wrong." >&2
  exit 1
fi

size_mb=$(awk "BEGIN { printf \"%.2f\", $size_bytes / 1048576 }")
echo "Dump complete. Files: $written_count. Size: ${size_mb} MB."
echo ""
echo "Included files:"
printf '  %s\n' "${files[@]#./}"