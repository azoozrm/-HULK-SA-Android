#!/usr/bin/env bash
set -euo pipefail
rm -rf project source-artifact.zip source-artifact output
mkdir -p project source-artifact output
curl -fL --retry 5 --retry-delay 2 -H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/artifacts/8581493204/zip" -o source-artifact.zip
unzip -q source-artifact.zip -d source-artifact
tar -xzf source-artifact/HULK-SA-v0.9.1-current-source.tar.gz -C project
grep -q 'versionName = "0.9.1"' project/app/build.gradle.kts
cat .payload/v0911-patch/part-* | base64 --decode > /tmp/v0911-direct.patch.gz
echo "566ddebe34fc136ae859db5b98032588d835946d1bc8fbbee6fedbf5000a2c9f  /tmp/v0911-direct.patch.gz" | sha256sum --check
gzip -dc /tmp/v0911-direct.patch.gz > /tmp/v0911-direct.patch
(cd project && patch -p1 --dry-run < /tmp/v0911-direct.patch && patch -p1 < /tmp/v0911-direct.patch)
grep -q 'versionName = "0.9.1.1"' project/app/build.gradle.kts
python3 tools/apply-v0912.py project
grep -q 'versionName = "0.9.1.3"' project/app/build.gradle.kts
mkdir -p project/app/src/main/res/font project/app/src/main/res/drawable-nodpi
FONT_BASE="https://raw.githubusercontent.com/google/fonts/main/ofl/ibmplexsansarabic"
curl -fL --retry 5 --retry-delay 2 "$FONT_BASE/IBMPlexSansArabic-Regular.ttf" -o project/app/src/main/res/font/ibm_plex_sans_arabic_regular.ttf
curl -fL --retry 5 --retry-delay 2 "$FONT_BASE/IBMPlexSansArabic-Medium.ttf" -o project/app/src/main/res/font/ibm_plex_sans_arabic_medium.ttf
curl -fL --retry 5 --retry-delay 2 "$FONT_BASE/IBMPlexSansArabic-SemiBold.ttf" -o project/app/src/main/res/font/ibm_plex_sans_arabic_semibold.ttf
curl -fL --retry 5 --retry-delay 2 "$FONT_BASE/IBMPlexSansArabic-Bold.ttf" -o project/app/src/main/res/font/ibm_plex_sans_arabic_bold.ttf
if ! curl -fL --retry 5 --retry-delay 2 "https://hulksa.com/assets/hulk-official-logo.webp" -o /tmp/hulk-logo.webp; then cat .payload/assets/logo.part-* | base64 --decode > /tmp/hulk-logo.webp; fi
cp /tmp/hulk-logo.webp project/app/src/main/res/drawable-nodpi/hulk_sa_logo.webp
cp /tmp/hulk-logo.webp project/app/src/main/res/drawable-nodpi/ic_banner.webp
python3 - <<'PY'
from pathlib import Path
p=Path('project/app/src/main/AndroidManifest.xml');t=p.read_text().replace('@mipmap/ic_launcher_round','@drawable/hulk_sa_logo').replace('@mipmap/ic_launcher','@drawable/hulk_sa_logo');p.write_text(t)
PY
cd project
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug -PHULK_PORTAL_URL=http://3162356.xyz:8080 --stacktrace 2>&1 | tee ../v0913-build.log
cd ..
cp project/app/build/outputs/apk/debug/app-debug.apk output/HULK-SA-v0.9.1.3-current-beta.apk
sha256sum output/HULK-SA-v0.9.1.3-current-beta.apk | tee output/SHA256.txt
