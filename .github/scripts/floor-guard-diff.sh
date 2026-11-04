#!/usr/bin/env bash
# Floor guard (CI): fails when a PR weakens the quality floor. Only lines the PR ADDS are
# inspected, so existing code is never flagged. Usage: floor-guard-diff.sh <base-ref>
# Escape hatch: a line carrying the text "floor-guard: allow" is ignored (use sparingly,
# explain in the PR description).
set -euo pipefail

base=${1:?usage: floor-guard-diff.sh <base-ref>}
range="$base...HEAD"
fail=0

code='*.ts *.tsx *.js *.jsx *.java *.kts'
tests=(':(glob)**/*.test.ts' ':(glob)**/*.test.tsx' ':(glob)**/*.spec.ts' ':(glob)**/*Test.java' ':(glob)**/*Tests.java' ':(glob)**/*IT.java')
excl=(':!.github' ':!docs' ':!*.md')

# added_lines <pattern> <pathspec...>: "file:line text" for each matching added line.
added_lines() {
  local pattern=$1; shift
  git diff -U0 --no-color "$range" -- "$@" "${excl[@]}" | PAT="$pattern" awk '
    /^\+\+\+ b\// { file = substr($0, 7); next }
    /^@@/ { match($0, /\+[0-9]+/); line = substr($0, RSTART + 1, RLENGTH - 1) + 0; next }
    /^\+/ { text = substr($0, 2);
            if (text ~ ENVIRON["PAT"] && text !~ /floor-guard: allow/) printf "%s:%d: %s\n", file, line, text;
            line++ }'
}

report() { # report <title> <hits>
  if [ -n "$2" ]; then
    echo "::error title=Floor guard::$1"
    printf '%s\n' "$2" | head -50
    fail=1
  fi
}

# shellcheck disable=SC2086
report "Added suppression of a checker (ts-ignore, eslint-disable, SuppressWarnings, NOPMD, istanbul ignore)" \
  "$(added_lines '@ts-ignore|@ts-nocheck|eslint-disable|@SuppressWarnings|NOPMD|istanbul ignore' $code)"

report "Added skipped or disabled test (.skip, .todo, xit, xdescribe, @Disabled)" \
  "$(added_lines '\.(skip|todo)\(|(^|[^A-Za-z])xit\(|xdescribe\(|@Disabled' "${tests[@]}")"

# shellcheck disable=SC2086
report "Added stub or empty catch (not implemented, catch {})" \
  "$(added_lines 'not implemented|catch[[:space:]]*(\([^)]*\))?[[:space:]]*\{[[:space:]]*\}' $code)"

deleted=$(git diff --diff-filter=D --name-only "$range" -- "${tests[@]}" || true)
report "Deleted test file" "$deleted"

# Removed assertions are only a warning: refactors can move them legitimately.
for f in $(git diff --diff-filter=M --name-only "$range" -- "${tests[@]}"); do
  rem=$(git diff -U0 "$range" -- "$f" | grep -cE '^-.*(expect\(|assert[A-Z(]|assertThat|verify\()' || true)
  add=$(git diff -U0 "$range" -- "$f" | grep -cE '^\+.*(expect\(|assert[A-Z(]|assertThat|verify\()' || true)
  if [ "$rem" -gt "$add" ]; then
    echo "::warning file=$f,title=Floor guard::$((rem - add)) assertion(s) removed net. Confirm tests still prove the same behaviour."
  fi
done

if [ "$fail" -eq 0 ]; then echo "Floor guard: clean."; fi
exit "$fail"
