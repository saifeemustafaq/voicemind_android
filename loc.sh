#!/usr/bin/env bash
# Count LOC for source files in android/, functions/, and root mem_and/
# Excludes: .md files, build/, node_modules/, .gradle/, generated code
# Sorted in descending order by line count

ROOT="$(cd "$(dirname "$0")" && pwd)"

{
  # Recurse into android/ and functions/, skipping build artifacts and dependencies
  find "$ROOT/android" "$ROOT/functions" -type f \
    ! -name "*.md" \
    ! -path "*/build/*" \
    ! -path "*/node_modules/*" \
    ! -path "*/.gradle/*" \
    ! -path "*/intermediates/*" \
    ! -path "*/generated/*" \
    ! -name "*.apk" \
    ! -name "*.dex" \
    ! -name "*.class" \
    ! -name "*.jar" \
    ! -name "*.aar"

  # Only root-level files in mem_and/ (no subdirectories)
  find "$ROOT" -maxdepth 1 -type f ! -name "*.md"
} | sort -u | while IFS= read -r file; do
    count=$(wc -l < "$file" 2>/dev/null)
    printf "%7d  %s\n" "$count" "${file#$ROOT/}"
  done | sort -rn
