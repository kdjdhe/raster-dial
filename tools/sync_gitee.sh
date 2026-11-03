#!/usr/bin/env bash
# Push main + publish a Gitee Release with the APK attachment.
#
#   export GITEE_TOKEN=...        # or put it in ~/.gitee_token
#   tools/sync_gitee.sh work/raster-dial-0.1.10.apk
#
# The token is only used for this call; it is never written into git config or
# the repository. Gitee mirror is the domestic fallback after the Chengdu
# primary; GitHub stays as the last URL in update.json.
set -euo pipefail

APK="${1:?usage: tools/sync_gitee.sh path/to/raster-dial-X.apk [owner/repo]}"
REPO="${2:-${GITEE_REPO:-kdjdhe/raster-dial}}"
OWNER="${REPO%%/*}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -n "${GITEE_TOKEN:-}" ]]; then
  TOKEN="$GITEE_TOKEN"
elif [[ -f "${GITEE_TOKEN_FILE:-$HOME/.gitee_token}" ]]; then
  TOKEN="$(tr -d ' \r\n' < "${GITEE_TOKEN_FILE:-$HOME/.gitee_token}")"
else
  echo "set GITEE_TOKEN or create ~/.gitee_token" >&2
  exit 1
fi

[[ -f "$APK" ]] || { echo "missing APK: $APK" >&2; exit 1; }
APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"
NAME="$(basename "$APK")"
export NAME
VERSION="${NAME#raster-dial-}"; VERSION="${VERSION%.apk}"
TAG="v$VERSION"
API="https://gitee.com/api/v5/repos/$REPO"

[[ -f "$ROOT/update.json" ]] || { echo "missing $ROOT/update.json" >&2; exit 1; }

echo "==> push main -> $REPO"
git -C "$ROOT" -c http.proxy= -c https.proxy= push \
  "https://$OWNER:$TOKEN@gitee.com/$REPO.git" main 2>&1 | sed -E 's#https://[^@ ]*@#https://***@#g'

echo "==> release $TAG"
RELEASE_ID="$(curl -s --max-time 30 "$API/releases/tags/$TAG?access_token=$TOKEN" \
  | python -X utf8 -I -c "import sys,json;d=json.load(sys.stdin);print(d.get('id') or '')")"

if [[ -z "$RELEASE_ID" ]]; then
  RELEASE_ID="$(curl -s --max-time 60 -X POST "$API/releases" \
    --data-urlencode "access_token=$TOKEN" \
    --data-urlencode "tag_name=$TAG" \
    --data-urlencode "name=Raster Dial $TAG" \
    --data-urlencode "target_commitish=main" \
    --data-urlencode "prerelease=false" \
    --data-urlencode "body=Raster Dial $TAG" \
    | python -X utf8 -I -c "import sys,json;d=json.load(sys.stdin);print(d.get('id') or '')")"
  [[ -n "$RELEASE_ID" ]] || { echo "failed to create release $TAG" >&2; exit 1; }
  echo "    created release id=$RELEASE_ID"
else
  echo "    existing release id=$RELEASE_ID"
fi

if curl -s --max-time 30 "$API/releases/tags/$TAG?access_token=$TOKEN" \
  | python -X utf8 -I -c "import sys,json,os;n=os.environ['NAME'];print('yes' if any(a.get('name')==n for a in json.load(sys.stdin).get('assets',[])) else '')" \
  | grep -q yes; then
  echo "    attachment already present: $NAME"
else
  echo "==> upload $NAME ($(wc -c < "$APK") bytes)"
  curl -s --max-time 600 -X POST "$API/releases/$RELEASE_ID/attach_files?access_token=$TOKEN" \
    -F "file=@$APK" \
    | python -X utf8 -I -c "import sys,json;d=json.load(sys.stdin);print('    ', d.get('browser_download_url') or d)"
fi

URL="https://gitee.com/$REPO/releases/download/$TAG/$NAME"
echo "==> verify anonymous download"
curl -sL --max-time 60 -o /dev/null \
  -w "    http=%{http_code} bytes=%{size_download}\n" "$URL"
echo "    $URL"
