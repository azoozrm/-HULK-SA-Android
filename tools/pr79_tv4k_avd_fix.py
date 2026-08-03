#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
from textwrap import dedent


HELPER = dedent(r'''\
#!/usr/bin/env bash
set -Eeuo pipefail

width="${1:?width px is required}"
height="${2:?height px is required}"
density="${3:?density dpi is required}"
avd_name="${4:-test}"
avd_home="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
config="${avd_home}/${avd_name}.avd/config.ini"

if [[ ! -f "$config" ]]; then
  echo "AVD config is missing: $config" >&2
  exit 2
fi

python3 - "$config" "$width" "$height" "$density" <<'PY_CONFIG'
from pathlib import Path
import sys

path = Path(sys.argv[1])
width, height, density = sys.argv[2:]
replacements = {
    'skin.name': f'{width}x{height}',
    'skin.path': f'{width}x{height}',
    'hw.lcd.width': width,
    'hw.lcd.height': height,
    'hw.lcd.density': density,
}
lines = path.read_text(encoding='utf-8').splitlines()
output = []
seen = set()
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line else ''
    if key in replacements:
        output.append(f'{key}={replacements[key]}')
        seen.add(key)
    else:
        output.append(line)
for key, value in replacements.items():
    if key not in seen:
        output.append(f'{key}={value}')
path.write_text('\n'.join(output) + '\n', encoding='utf-8')
PY_CONFIG

for expected in \
  "skin.name=${width}x${height}" \
  "skin.path=${width}x${height}" \
  "hw.lcd.width=${width}" \
  "hw.lcd.height=${height}" \
  "hw.lcd.density=${density}"; do
  grep -Fxq "$expected" "$config" || {
    echo "AVD config verification failed: $expected" >&2
    exit 3
  }
done

echo "Prepared ${avd_name}: ${width}x${height} @ ${density}dpi"
''')


TEST = dedent(r'''\
from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


class AvdProfilePreparationTest(unittest.TestCase):
    def test_helper_rewrites_stale_tv_profile_geometry(self) -> None:
        helper = Path('quality/compatibility-v2/prepare_avd_profile.sh').resolve()
        with tempfile.TemporaryDirectory() as temp:
            avd_home = Path(temp) / 'avd'
            config = avd_home / 'test.avd' / 'config.ini'
            config.parent.mkdir(parents=True)
            config.write_text(
                'skin.name=1920x1080\n'
                'skin.path=1920x1080\n'
                'hw.lcd.width=1920\n'
                'hw.lcd.height=1080\n'
                'hw.lcd.density=320\n',
                encoding='utf-8',
            )
            env = dict(os.environ, ANDROID_AVD_HOME=str(avd_home))
            subprocess.run(
                ['bash', str(helper), '3840', '2160', '640', 'test'],
                check=True,
                env=env,
                capture_output=True,
                text=True,
            )
            content = config.read_text(encoding='utf-8')
            for expected in (
                'skin.name=3840x2160',
                'skin.path=3840x2160',
                'hw.lcd.width=3840',
                'hw.lcd.height=2160',
                'hw.lcd.density=640',
            ):
                self.assertIn(expected, content)
            self.assertNotIn('skin.name=1920x1080', content)

    def test_workflows_prepare_exact_avd_before_launch(self) -> None:
        full = Path('.github/workflows/compatibility-v2-full.yml').read_text(encoding='utf-8')
        targeted = Path('.github/workflows/compatibility-v2-targeted.yml').read_text(encoding='utf-8')
        marker = 'pre-emulator-launch-script: bash quality/compatibility-v2/prepare_avd_profile.sh'
        self.assertIn(marker, full)
        self.assertIn(marker, targeted)
        self.assertIn("'${{ matrix.width }}' '${{ matrix.height }}' '${{ matrix.density }}'", full)
        self.assertIn(
            "'${{ needs.resolve-profile.outputs.width }}' '${{ needs.resolve-profile.outputs.height }}' '${{ needs.resolve-profile.outputs.density }}'",
            targeted,
        )
        self.assertIn('bash -n quality/compatibility-v2/prepare_avd_profile.sh', full)
        self.assertIn('bash -n quality/compatibility-v2/prepare_avd_profile.sh', targeted)


if __name__ == '__main__':
    unittest.main()
''')


def insert_once(text: str, needle: str, addition: str, label: str) -> str:
    if addition in text:
        return text
    if needle not in text:
        raise SystemExit(f'{label} marker is missing')
    return text.replace(needle, needle + addition, 1)


def main() -> None:
    helper = Path('quality/compatibility-v2/prepare_avd_profile.sh')
    helper.write_text(HELPER, encoding='utf-8')
    helper.chmod(0o755)

    full = Path('.github/workflows/compatibility-v2-full.yml')
    full_text = full.read_text(encoding='utf-8')
    full_emulator = (
        '          emulator-options: -no-window -gpu swiftshader_indirect -noaudio '
        '-no-boot-anim -no-metrics -camera-back none -no-snapshot-save -skin '
        '${{ matrix.width }}x${{ matrix.height }}\n'
    )
    full_prelaunch = (
        "          pre-emulator-launch-script: bash quality/compatibility-v2/prepare_avd_profile.sh "
        "'${{ matrix.width }}' '${{ matrix.height }}' '${{ matrix.density }}' 'test'\n"
    )
    full_text = insert_once(full_text, full_emulator, full_prelaunch, 'Full Matrix emulator-options')
    full_text = insert_once(
        full_text,
        '          bash -n quality/compatibility-v2/configure_emulator_profile.sh\n',
        '          bash -n quality/compatibility-v2/prepare_avd_profile.sh\n',
        'Full Matrix shell validation',
    )
    full.write_text(full_text, encoding='utf-8')

    targeted = Path('.github/workflows/compatibility-v2-targeted.yml')
    targeted_text = targeted.read_text(encoding='utf-8')
    targeted_emulator = (
        '          emulator-options: -no-window -gpu swiftshader_indirect -noaudio '
        '-no-boot-anim -no-metrics -camera-back none -skin '
        '${{ needs.resolve-profile.outputs.width }}x${{ needs.resolve-profile.outputs.height }}\n'
    )
    targeted_prelaunch = (
        "          pre-emulator-launch-script: bash quality/compatibility-v2/prepare_avd_profile.sh "
        "'${{ needs.resolve-profile.outputs.width }}' '${{ needs.resolve-profile.outputs.height }}' "
        "'${{ needs.resolve-profile.outputs.density }}' 'test'\n"
    )
    targeted_text = insert_once(
        targeted_text,
        targeted_emulator,
        targeted_prelaunch,
        'Targeted emulator-options',
    )
    targeted_text = insert_once(
        targeted_text,
        '          bash -n quality/compatibility-v2/configure_emulator_profile.sh\n',
        '          bash -n quality/compatibility-v2/prepare_avd_profile.sh\n',
        'Targeted shell validation',
    )
    targeted.write_text(targeted_text, encoding='utf-8')

    Path('quality/compatibility-v2/tests/test_avd_profile_preparation.py').write_text(TEST, encoding='utf-8')
    Path('quality/compatibility-v2/run-full.request').write_text(
        'requested_for_parent=1041b4f8b054d27eee80f60560847bf8b010d0cf\n'
        'reason=verify-tv-4k-avd-config-before-emulator-launch\n'
        'request_sequence=13\n'
        'requested_at=2026-08-03T20:10:00Z\n',
        encoding='utf-8',
    )


if __name__ == '__main__':
    main()
