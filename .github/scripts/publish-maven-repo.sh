#!/usr/bin/env bash
#
# Publishes the Maven artifacts of this project into a separate, public Git
# repository that holds a plain Maven repository layout. That repository is
# served read-only (and token-free) through raw.githubusercontent.com and can
# hold the artifacts of several libraries side by side, because Maven's layout
# already namespaces them by groupId and artifactId.
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
#   MAVEN_REPO_REMOTE  git remote of the target repository
#                      (default: git@github.com:Tob1as864/maven-repo.git)
#   MAVEN_REPO_BRANCH  branch to publish to                 (default: main)
#   CHECKOUT_DIR       where to clone it                    (default: .maven-repo)
#   PUSH               set to "false" for a local dry run   (default: true)

set -euo pipefail

REMOTE="${MAVEN_REPO_REMOTE:-git@github.com:Tob1as864/maven-repo.git}"
BRANCH="${MAVEN_REPO_BRANCH:-main}"
CHECKOUT_DIR="${CHECKOUT_DIR:-.maven-repo}"
PUSH="${PUSH:-true}"
RELEASE_VERSION="${1:-}"

MVN="${MVN:-mvn}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

log() { printf '\n==> %s\n' "$*"; }

# --- clone the target repository ---------------------------------------------
log "Cloning $REMOTE"
rm -rf "$CHECKOUT_DIR"
git clone --quiet --depth 1 "$REMOTE" "$CHECKOUT_DIR"

REPO_DIR="$(cd "$CHECKOUT_DIR" && pwd)"

# A freshly created repository has no commits, so HEAD is unborn and no branch
# ref exists yet; -B creates the branch in that case and switches to it in all
# others.
if [ "$(git -C "$REPO_DIR" rev-parse --abbrev-ref HEAD)" != "$BRANCH" ]; then
    git -C "$REPO_DIR" checkout -q -B "$BRANCH"
fi

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
GROUP_ID="$("$MVN" -B -q --no-transfer-progress help:evaluate \
    -Dexpression=project.groupId -DforceStdout)"
ARTIFACT_ID="$("$MVN" -B -q --no-transfer-progress help:evaluate \
    -Dexpression=project.artifactId -DforceStdout)"
ARTIFACT_DIR="$REPO_DIR/$(printf '%s' "$GROUP_ID" | tr '.' '/')/$ARTIFACT_ID/$VERSION"

case "$VERSION" in
    *-SNAPSHOT) ;;
    *)
        # Released versions are immutable: never silently overwrite one.
        if [ -d "$ARTIFACT_DIR" ]; then
            echo "ERROR: $GROUP_ID:$ARTIFACT_ID:$VERSION already exists in $REMOTE." >&2
            echo "       Bump the version or delete it there first." >&2
            exit 1
        fi
        ;;
esac

# --- build and deploy into the checkout --------------------------------------
log "Deploying $GROUP_ID:$ARTIFACT_ID:$VERSION into $REPO_DIR"
"$MVN" -B --no-transfer-progress -Prelease clean deploy -Dmaven.repo.dir="$REPO_DIR"

# --- landing page ------------------------------------------------------------
# The repository is shared by several libraries, so only write a README when it
# has none yet; an existing one is maintained by hand and must not be clobbered.
if [ ! -e "$REPO_DIR/README.md" ]; then
    cat > "$REPO_DIR/README.md" <<'README'
# Maven repository

This repository holds released Java artifacts in Maven repository layout. Its
contents are **generated** by the release workflows of the individual library
repositories - do not commit here by hand.

Consume it without any authentication:

```xml
<repositories>
  <repository>
    <id>tob1as864</id>
    <url>https://raw.githubusercontent.com/Tob1as864/maven-repo/main</url>
  </repository>
</repositories>
```

Then declare the library you need as an ordinary dependency. Browse the
directory tree above for the available groupIds, artifacts and versions.
README
fi

# --- commit and push ----------------------------------------------------------
git -C "$REPO_DIR" add -A
if git -C "$REPO_DIR" diff --cached --quiet; then
    log "No changes to publish"
    exit 0
fi

git -C "$REPO_DIR" commit -q -m "Publish $GROUP_ID:$ARTIFACT_ID $VERSION"
log "Committed $GROUP_ID:$ARTIFACT_ID $VERSION"

if [ "$PUSH" != "true" ]; then
    log "PUSH=$PUSH - skipping push (dry run)"
    exit 0
fi

for delay in 2 4 8 16 0; do
    if git -C "$REPO_DIR" push -u origin "$BRANCH"; then
        log "Pushed to $REMOTE ($BRANCH)"
        exit 0
    fi
    [ "$delay" -eq 0 ] && break
    echo "Push failed, retrying in ${delay}s ..." >&2
    sleep "$delay"
done

echo "ERROR: could not push to $REMOTE" >&2
exit 1
