#!/usr/bin/env bash
#
# Publishes the Maven artifacts of this project into the `maven-repo` branch of
# this same repository. That branch holds a plain Maven repository layout and is
# served read-only (and token-free) through raw.githubusercontent.com.
#
# Usage:
#   .github/scripts/publish-maven-repo.sh [version]
#
# Without an argument the version from pom.xml is published as-is (typically a
# SNAPSHOT). With an argument the pom version is set to it for the build only;
# pom.xml is restored afterwards, so the working tree keeps its development
# version and nothing has to be committed back to the source branch.
#
# Environment:
#   MAVEN_REPO_BRANCH  branch holding the repository       (default: maven-repo)
#   WORKTREE_DIR       checkout location of that branch    (default: .maven-repo-branch)
#   PUSH               set to "false" for a local dry run  (default: true)

set -euo pipefail

BRANCH="${MAVEN_REPO_BRANCH:-maven-repo}"
WORKTREE_DIR="${WORKTREE_DIR:-.maven-repo-branch}"
PUSH="${PUSH:-true}"
RELEASE_VERSION="${1:-}"

MVN="${MVN:-mvn}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

log() { printf '\n==> %s\n' "$*"; }

# --- prepare a worktree holding the maven-repo branch ------------------------
if [ -e "$WORKTREE_DIR" ]; then
    git worktree remove --force "$WORKTREE_DIR" 2>/dev/null || rm -rf "$WORKTREE_DIR"
fi
git worktree prune

if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
    log "Fetching existing branch '$BRANCH'"
    git fetch --no-tags origin "+refs/heads/$BRANCH:refs/remotes/origin/$BRANCH"
    git worktree add --detach "$WORKTREE_DIR" "refs/remotes/origin/$BRANCH"
    git -C "$WORKTREE_DIR" switch -C "$BRANCH" "refs/remotes/origin/$BRANCH"
else
    log "Branch '$BRANCH' does not exist yet - creating it as an orphan branch"
    git worktree add --detach "$WORKTREE_DIR" HEAD
    git -C "$WORKTREE_DIR" checkout --orphan "$BRANCH"
    git -C "$WORKTREE_DIR" rm -rq --cached . 2>/dev/null || true
    find "$WORKTREE_DIR" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
fi

REPO_DIR="$(cd "$WORKTREE_DIR" && pwd)"

# --- determine and validate the version to publish ---------------------------
if [ -n "$RELEASE_VERSION" ]; then
    log "Setting project version to $RELEASE_VERSION"
    POM_BACKUP="$(mktemp)"
    cp pom.xml "$POM_BACKUP"
    # Restore the development version even when the build below fails.
    trap 'cp "$POM_BACKUP" "$REPO_ROOT/pom.xml"; rm -f "$POM_BACKUP"' EXIT
    "$MVN" -B --no-transfer-progress versions:set \
        -DnewVersion="$RELEASE_VERSION" -DgenerateBackupPoms=false
fi

VERSION="$("$MVN" -B -q --no-transfer-progress help:evaluate \
    -Dexpression=project.version -DforceStdout)"
GROUP_PATH="$("$MVN" -B -q --no-transfer-progress help:evaluate \
    -Dexpression=project.groupId -DforceStdout | tr '.' '/')"
ARTIFACT_ID="$("$MVN" -B -q --no-transfer-progress help:evaluate \
    -Dexpression=project.artifactId -DforceStdout)"
ARTIFACT_DIR="$REPO_DIR/$GROUP_PATH/$ARTIFACT_ID/$VERSION"

case "$VERSION" in
    *-SNAPSHOT) ;;
    *)
        # Released versions are immutable: never silently overwrite one.
        if [ -d "$ARTIFACT_DIR" ]; then
            echo "ERROR: version $VERSION already exists in branch '$BRANCH'." >&2
            echo "       Bump the version or delete it from that branch first." >&2
            exit 1
        fi
        ;;
esac

# --- build and deploy into the worktree --------------------------------------
log "Deploying $ARTIFACT_ID:$VERSION into $REPO_DIR"
"$MVN" -B --no-transfer-progress -Prelease clean deploy -Dmaven.repo.dir="$REPO_DIR"

# --- landing page of the branch ----------------------------------------------
cat > "$REPO_DIR/README.md" <<README
# Maven repository

This branch is **generated** - do not commit to it by hand. It contains the
released artifacts of [reqif4j](https://github.com/Tob1as864/ReqIF-Parser-Java)
in Maven repository layout and is published by
\`.github/workflows/release.yml\` on the default branch.

Consume it without any authentication:

\`\`\`xml
<repositories>
  <repository>
    <id>reqif4j</id>
    <url>https://raw.githubusercontent.com/Tob1as864/ReqIF-Parser-Java/$BRANCH</url>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>

<dependency>
  <groupId>de.uni_stuttgart.ils</groupId>
  <artifactId>reqif4j</artifactId>
  <version>$VERSION</version>
</dependency>
\`\`\`
README

# --- commit and push ----------------------------------------------------------
git -C "$REPO_DIR" add -A
if git -C "$REPO_DIR" diff --cached --quiet; then
    log "No changes to publish"
    exit 0
fi

git -C "$REPO_DIR" commit -q -m "Publish $ARTIFACT_ID $VERSION"
log "Committed $ARTIFACT_ID $VERSION to branch '$BRANCH'"

if [ "$PUSH" != "true" ]; then
    log "PUSH=$PUSH - skipping push (dry run)"
    exit 0
fi

for delay in 2 4 8 16 0; do
    if git -C "$REPO_DIR" push -u origin "$BRANCH"; then
        log "Pushed branch '$BRANCH'"
        exit 0
    fi
    [ "$delay" -eq 0 ] && break
    echo "Push failed, retrying in ${delay}s ..." >&2
    sleep "$delay"
done

echo "ERROR: could not push branch '$BRANCH'" >&2
exit 1
