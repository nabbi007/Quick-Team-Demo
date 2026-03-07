#!/bin/bash
set -e
# ============================================================
# QuickPoll — Git Hooks Setup
# Purpose: Installs all Git hooks into .git/hooks/ so that
#          branch naming, commit messages, and secret scanning
#          are enforced locally for every team member.
# Usage:   bash devops/scripts/setup-hooks.sh
# ============================================================

HOOKS_DIR="$(git rev-parse --show-toplevel)/.git/hooks"

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║            🔧  QuickPoll — Git Hooks Setup                   ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ──────────────────────────────────────────────────────────────
# HOOK 1 — pre-commit
# ──────────────────────────────────────────────────────────────
cat > "$HOOKS_DIR/pre-commit" << 'HOOK_EOF'
#!/bin/bash
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM)
BLOCKED=0

echo ""
echo "🔍  Checking for .env files..."
for FILE in $STAGED_FILES; do
  if echo "$FILE" | grep -qE "(^|/)\.env(\.|$)"; then
    echo ""
    echo "  ❌  BLOCKED: .env file detected — $FILE"
    echo "  Fix: git reset HEAD \"$FILE\" && add it to .gitignore"
    echo ""
    BLOCKED=1
  fi
done
[ "$BLOCKED" -eq 0 ] && echo "  ✅  No .env files staged."

echo ""
echo "🔍  Scanning for hardcoded secrets..."
SECRET_PATTERNS=(
  "password\s*=\s*[\"'][^\"']{4,}"
  "secret\s*=\s*[\"'][^\"']{4,}"
  "api_key\s*=\s*[\"'][^\"']{4,}"
  "apikey\s*=\s*[\"'][^\"']{4,}"
  "AWS_SECRET_ACCESS_KEY"
  "private_key\s*=\s*[\"'][^\"']{4,}"
  "token\s*=\s*[\"'][^\"']{8,}"
  "AKIA[0-9A-Z]{16}"
  "ghp_[a-zA-Z0-9]{36}"
  "sk-[a-zA-Z0-9]{32,}"
  "BEGIN.*PRIVATE KEY"
)
SECRET_FOUND=0
for FILE in $STAGED_FILES; do
  [ -f "$FILE" ] || continue
  case "$FILE" in
    *.lock|*.png|*.jpg|*.gif|*.ico|*.woff|*.woff2|*.ttf|*.eot|*.jar|*.class) continue ;;
    devops/scripts/setup-hooks.sh) continue ;;
  esac
  for PATTERN in "${SECRET_PATTERNS[@]}"; do
    MATCHES=$(git diff --cached -U0 "$FILE" | grep "^+" | grep -v "^+++" | grep -iE -- "$PATTERN")
    if [ -n "$MATCHES" ]; then
      [ "$SECRET_FOUND" -eq 0 ] && echo "" && echo "  ❌  BLOCKED: Possible hardcoded secret(s) detected." && echo ""
      echo "    File   : $FILE"
      echo "    Pattern: $PATTERN"
      echo "$MATCHES" | sed 's/^/      /'
      echo ""
      SECRET_FOUND=1
      BLOCKED=1
    fi
  done
done
[ "$SECRET_FOUND" -eq 0 ] && echo "  ✅  No hardcoded secrets detected."
if [ "$SECRET_FOUND" -eq 1 ]; then
  echo "  Fix: Remove the secret and use environment variables instead."
  echo ""
fi

echo ""
echo "🔍  Checking for debug statements..."
DEBUG_FOUND=0
for FILE in $STAGED_FILES; do
  [ -f "$FILE" ] || continue
  for PATTERN in "console\.log\(" "System\.out\.println\("; do
    MATCHES=$(git diff --cached -U0 "$FILE" | grep "^+" | grep -v "^+++" | grep -E "$PATTERN")
    if [ -n "$MATCHES" ]; then
      [ "$DEBUG_FOUND" -eq 0 ] && echo "" && echo "  ⚠️   WARNING: Debug statements found (not blocking)." && echo ""
      echo "    File: $FILE"
      echo "$MATCHES" | sed 's/^/      /'
      echo ""
      DEBUG_FOUND=1
    fi
  done
done
[ "$DEBUG_FOUND" -eq 0 ] && echo "  ✅  No debug statements found."

echo ""
echo "🔍  Checking file sizes..."
MAX_SIZE=$((5 * 1024 * 1024))
for FILE in $STAGED_FILES; do
  [ -f "$FILE" ] || continue
  FILE_SIZE=$(wc -c < "$FILE" | tr -d ' ')
  if [ "$FILE_SIZE" -gt "$MAX_SIZE" ]; then
    echo "  ❌  BLOCKED: $FILE exceeds 5 MB limit."
    BLOCKED=1
  fi
done

echo ""
if [ "$BLOCKED" -eq 1 ]; then
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║    ❌  COMMIT BLOCKED — Fix the issues above and try again   ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""
  exit 1
else
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║              ✅  All pre-commit checks passed!               ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""
  exit 0
fi
HOOK_EOF

# ──────────────────────────────────────────────────────────────
# HOOK 2 — commit-msg
# ──────────────────────────────────────────────────────────────
cat > "$HOOKS_DIR/commit-msg" << 'HOOK_EOF'
#!/bin/bash
COMMIT_MSG_FILE="$1"
COMMIT_MSG=$(cat "$COMMIT_MSG_FILE")
ALLOWED_TYPES="feat|fix|docs|ci|chore|refactor|test|style|perf"
PATTERN="^($ALLOWED_TYPES)(\([a-zA-Z0-9_-]+\))?: .{1,60}\s+(\[QP-[0-9]+\]|QP-[0-9]+|#[0-9]+)$"

echo "$COMMIT_MSG" | grep -qE "^Merge " && exit 0
echo "$COMMIT_MSG" | grep -qE "^Revert " && exit 0

if [ -z "$(echo "$COMMIT_MSG" | tr -d '[:space:]')" ]; then
  echo ""; echo "❌  Commit blocked: empty message."; echo ""; exit 1
fi

if ! echo "$COMMIT_MSG" | grep -qE "$PATTERN"; then
  TYPE_PART=$(echo "$COMMIT_MSG" | cut -d'(' -f1 | cut -d':' -f1)

  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║         ❌  COMMIT BLOCKED — Invalid commit message          ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""
  echo "  Your message: \"$COMMIT_MSG\""
  echo ""

  if ! echo "$TYPE_PART" | grep -qE "^($ALLOWED_TYPES)$"; then
    echo "  Problem: \"$TYPE_PART\" is not a valid type."
    echo "  Allowed: feat fix docs ci chore refactor test style perf"
    echo ""
  fi

  HAS_REF=$(echo "$COMMIT_MSG" | grep -cE "(\[QP-[0-9]+\]|QP-[0-9]+|#[0-9]+)$")
  if [ "$HAS_REF" -eq 0 ]; then
    echo "  Problem: Missing backlog reference at end of message."
    echo "  Accepted: [QP-19] or QP-19 or #19"
    echo ""
  fi

  echo "  Required format:"
  echo "    type(scope): short description [QP-XX]"
  echo ""
  echo "  Examples:"
  echo "    feat(auth): add JWT login [QP-19]"
  echo "    fix(vote): prevent duplicate voting [QP-22]"
  echo "    ci: set up GitHub Actions pipeline [QP-17]"
  echo ""
  echo "  Fix: git commit --amend -m \"feat(scope): description [QP-XX]\""
  echo ""
  exit 1
fi

echo "✅  Commit message format is valid."
exit 0
HOOK_EOF

# ──────────────────────────────────────────────────────────────
# HOOK 3 — pre-push
# ──────────────────────────────────────────────────────────────
cat > "$HOOKS_DIR/pre-push" << 'HOOK_EOF'
#!/bin/bash
BRANCH=$(git symbolic-ref HEAD 2>/dev/null | sed 's|refs/heads/||')
ALLOWED="^(feature|fix|hotfix|release|chore|docs|ci)/"

if [ "$BRANCH" = "main" ] || [ "$BRANCH" = "develop" ]; then
  echo "✅  Branch \"$BRANCH\" — push allowed."
  exit 0
fi

if ! echo "$BRANCH" | grep -qE "$ALLOWED"; then
  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║          ❌  PUSH BLOCKED — Invalid branch name              ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""
  echo "  Your branch: \"$BRANCH\""
  echo ""
  echo "  Must start with: feature/ fix/ hotfix/ release/ chore/ docs/ ci/"
  echo ""
  echo "  Examples:"
  echo "    feature/QP-19-jwt-authentication"
  echo "    fix/QP-15-login-bug"
  echo "    ci/QP-17-github-actions"
  echo ""
  echo "  Rename: git branch -m \"$BRANCH\" \"feature/your-name\""
  echo ""
  exit 1
fi

echo "✅  Branch \"$BRANCH\" — push allowed."
exit 0
HOOK_EOF

# ──────────────────────────────────────────────────────────────
# HOOK 4 — prepare-commit-msg
# ──────────────────────────────────────────────────────────────
cat > "$HOOKS_DIR/prepare-commit-msg" << 'HOOK_EOF'
#!/bin/bash
COMMIT_MSG_FILE="$1"
COMMIT_SOURCE="$2"

[ "$COMMIT_SOURCE" = "merge" ] || [ "$COMMIT_SOURCE" = "squash" ] && exit 0

BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null)
[ -z "$BRANCH" ] && exit 0
[ "$BRANCH" = "main" ] || [ "$BRANCH" = "develop" ] && exit 0

TICKET_ID=$(echo "$BRANCH" | grep -oE 'QP-[0-9]+')
[ -z "$TICKET_ID" ] && echo "ℹ️  No QP ticket in branch name. Add [QP-XX] manually." && exit 0

TICKET_TAG="[$TICKET_ID]"
CURRENT_MSG=$(cat "$COMMIT_MSG_FILE")
echo "$CURRENT_MSG" | grep -qF "$TICKET_TAG" && exit 0

FIRST_LINE=$(head -1 "$COMMIT_MSG_FILE")
REST=$(tail -n +2 "$COMMIT_MSG_FILE")

if [ -n "$REST" ]; then
  printf '%s %s\n%s' "$FIRST_LINE" "$TICKET_TAG" "$REST" > "$COMMIT_MSG_FILE"
else
  printf '%s %s' "$FIRST_LINE" "$TICKET_TAG" > "$COMMIT_MSG_FILE"
fi

echo "ℹ️  Auto-appended $TICKET_TAG from branch: $BRANCH"
exit 0
HOOK_EOF

# ──────────────────────────────────────────────────────────────
# Make all hooks executable
# ──────────────────────────────────────────────────────────────
chmod +x "$HOOKS_DIR/pre-commit" \
         "$HOOKS_DIR/commit-msg" \
         "$HOOKS_DIR/pre-push" \
         "$HOOKS_DIR/prepare-commit-msg"

echo "  ✅  pre-commit         — blocks secrets, .env files, large files"
echo "  ✅  commit-msg         — enforces conventional commits + [QP-XX]"
echo "  ✅  pre-push           — enforces branch naming (feature/, fix/, etc.)"
echo "  ✅  prepare-commit-msg — auto-appends ticket ID from branch name"
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           ✅  All Git hooks installed and active!             ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
