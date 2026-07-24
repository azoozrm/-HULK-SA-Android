#!/usr/bin/env bash
set -euo pipefail
rm -rf project source-artifact.zip source-artifact export
mkdir -p project source-artifact export
curl -fL --retry 5 --retry-delay 2 -H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/artifacts/8581493204/zip" -o source-artifact.zip
unzip -q source-artifact.zip -d source-artifact
tar -xzf source-artifact/HULK-SA-v0.9.1-current-source.tar.gz -C project
cat .payload/v0911-patch/part-* | base64 --decode > /tmp/v0911-direct.patch.gz
echo "566ddebe34fc136ae859db5b98032588d835946d1bc8fbbee6fedbf5000a2c9f  /tmp/v0911-direct.patch.gz" | sha256sum --check
gzip -dc /tmp/v0911-direct.patch.gz > /tmp/v0911-direct.patch
(cd project && patch -p1 < /tmp/v0911-direct.patch)
python3 tools/apply-v0912.py project
python3 tools/apply-v0913.py project
python3 tools/apply-v0914-fixed.py project
python3 tools/apply-v0915.py project
python3 tools/apply-v0916.py project
python3 tools/apply-v0917.py project
grep -q 'versionName = "0.9.1.7"' project/app/build.gradle.kts
tar -czf export/HULK-SA-v0.9.1.7-current-source.tar.gz -C project .
sha256sum export/HULK-SA-v0.9.1.7-current-source.tar.gz > export/SHA256.txt
