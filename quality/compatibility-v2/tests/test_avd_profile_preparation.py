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

    def test_full_matrix_prepares_exact_avd_before_launch(self) -> None:
        full = Path('.github/workflows/compatibility-v2-full.yml').read_text(encoding='utf-8')
        marker = 'pre-emulator-launch-script: bash quality/compatibility-v2/prepare_avd_profile.sh'
        self.assertIn(marker, full)
        self.assertIn("'${{ matrix.width }}' '${{ matrix.height }}' '${{ matrix.density }}'", full)
        self.assertIn('bash -n quality/compatibility-v2/prepare_avd_profile.sh', full)
        self.assertEqual(1, full.count(marker))


if __name__ == '__main__':
    unittest.main()
