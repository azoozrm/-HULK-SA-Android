#!/usr/bin/env python3
"""Export the flat app-gold icon system. Authoring: Inkscape, Pillow, pypdf."""

from pathlib import Path
import html
import re
import shutil
import subprocess
import tempfile
from uuid import uuid4

from PIL import Image
from pypdf import PdfReader, PdfWriter


PACKAGE = Path(__file__).resolve().parent
REPOSITORY = PACKAGE.parents[1]
GOLD = "#E6C352"
BACKGROUND = "#030402"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def _profile() -> bytes:
    with Image.open(PACKAGE / "adaptive/ic_launcher_background_432.png") as image:
        return image.info["icc_profile"]


def _png(source: Path, target: Path, width: int, height: int, mode: str, profile: bytes) -> None:
    with tempfile.TemporaryDirectory(prefix="hulk-vector-") as temporary:
        raw = Path(temporary) / "render.png"
        subprocess.run([
            "inkscape", str(source), "--batch-process", f"--app-id-tag=hulk{uuid4().hex}", "--export-area-page", "--export-type=png",
            f"--export-filename={raw}", f"--export-width={width}", f"--export-height={height}",
        ], check=True, capture_output=True)
        with Image.open(raw) as image:
            if image.size != (width, height):
                raise ValueError(f"Unexpected dimensions: {source}: {image.size}")
            if mode == "RGB" and image.convert("RGBA").getextrema()[3] != (255, 255):
                raise ValueError(f"Opaque export has transparency: {target}")
            encoded = Path(temporary) / "encoded.png"
            image.convert(mode).save(encoded, icc_profile=profile, compress_level=9)
        with Image.open(encoded) as image:
            image.verify()
        # Keep the previous complete asset if encoding fails.
        encoded.replace(target)


def export_banners() -> None:
    profile = _profile()
    source = PACKAGE / "tv/tv_banner_master.svg"
    for density, factor in DENSITIES.items():
        width, height = int(160 * factor), int(90 * factor)
        target = PACKAGE / f"tv/tv_banner_{density}_{width}x{height}.png"
        _png(source, target, width, height, "RGB", profile)
        for copy in [
            PACKAGE / f"android-res/mipmap-{density}/tv_banner.png",
            REPOSITORY / f"app/src/main/res/mipmap-{density}/tv_banner.png",
        ]:
            shutil.copyfile(target, copy)
    for width, height in [(1920, 1080), (3840, 2160)]:
        _png(source, PACKAGE / f"tv/tv_banner_master_{width}x{height}.png", width, height, "RGB", profile)


def _vector_exports(source: Path) -> None:
    for extension in ["pdf", "eps"]:
        target = source.with_suffix("." + extension)
        subprocess.run([
            "inkscape", str(source), "--batch-process", f"--app-id-tag=hulk{uuid4().hex}", "--export-area-page", f"--export-type={extension}",
            f"--export-filename={target}", "--export-text-to-path",
        ], check=True, capture_output=True)
        if extension == "pdf":
            reader = PdfReader(target)
            writer = PdfWriter()
            writer.append_pages_from_reader(reader)
            writer.add_metadata({
                "/Title": source.stem, "/Author": "HULK SA",
                "/Creator": "HULK SA flat icon exporter", "/Producer": "Inkscape / pypdf",
                "/CreationDate": "D:20261016000000Z", "/ModDate": "D:20261016000000Z",
            })
            writer.write(target)
        else:
            text = target.read_text()
            text = re.sub(r"^%%CreationDate:.*$", "%%CreationDate: 2026-10-16", text, flags=re.MULTILINE)
            target.write_text("\n".join(line.rstrip() for line in text.splitlines()) + "\n")


def _image(path: str, x: float, y: float, width: float, height: float, clip: str = "") -> str:
    clipped = f' clip-path="url(#{clip})"' if clip else ""
    return (f'<image xmlns:xlink="http://www.w3.org/1999/xlink" '
            f'xlink:href="{html.escape((PACKAGE / path).as_uri())}" '
            f'x="{x}" y="{y}" width="{width}" height="{height}"{clipped}/>')


def _label(text: str, x: float, y: float, size: int = 28, color: str = "#FFF9EB") -> str:
    return (f'<text x="{x}" y="{y}" font-family="DejaVu Sans" '
            f'font-size="{size}" fill="{color}">{html.escape(text)}</text>')


def _board(body: str, width: int, height: int, target: str, profile: bytes) -> None:
    with tempfile.TemporaryDirectory(prefix="hulk-preview-") as temporary:
        source = Path(temporary) / "board.svg"
        source.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">'
                          f'<rect width="100%" height="100%" fill="{BACKGROUND}"/>{body}</svg>')
        _png(source, PACKAGE / "previews" / target, width, height, "RGB", profile)


def _previews(profile: bytes) -> None:
    body = _label("HULK SA - Adaptive masks", 90, 110, 60, GOLD)
    body += _label("Flat app gold #E6C352 | 108dp foreground layer | original shield geometry", 90, 175, 29)
    names = ["Circle", "Rounded square", "Squircle", "Teardrop"]
    for index, name in enumerate(names):
        x, y, side = 90 + index * 530, 335, 430
        if index == 0:
            shape = f'<circle cx="{x + side / 2}" cy="{y + side / 2}" r="{side / 2}"/>'
        elif index in [1, 2]:
            shape = f'<rect x="{x}" y="{y}" width="{side}" height="{side}" rx="{95 if index == 1 else 145}"/>'
        else:
            shape = f'<path d="M{x+215},{y} C{x+345},{y} {x+430},{y+85} {x+430},{y+215} V{y+430} H{x+215} C{x+85},{y+430} {x},{y+345} {x},{y+215} C{x},{y+85} {x+85},{y} {x+215},{y}Z"/>'
        body += f'<defs><clipPath id="mask{index}">{shape}</clipPath></defs>'
        body += f'<rect x="{x}" y="{y}" width="{side}" height="{side}" fill="#111108" clip-path="url(#mask{index})"/>'
        body += _image("adaptive/ic_launcher_foreground_432.png", x-side/4, y-side/4, side*1.5, side*1.5, f"mask{index}")
        inset = side * (1 - 66 / 72) / 2
        body += f'<rect x="{x+inset}" y="{y+inset}" width="{side*66/72}" height="{side*66/72}" fill="none" stroke="{GOLD}" stroke-width="2"/>'
        body += _label(name, x + 35, 850, 30)
    body += _label("72dp masked viewport; outline: 66dp safe bounds. System masks retain Android tint.", 90, 1050, 30, "#B8B3A4")
    _board(body, 2200, 1300, "adaptive_mask_qa.png", profile)

    body = _label("HULK SA - Unified Icon System", 96, 110, 64, GOLD)
    body += _label("Flat app gold #E6C352 | Single colour artwork | R03", 96, 175, 30)
    examples = [
        ("play-store/hulk_sa_play_store_512.png", "Store badge"),
        ("legacy/ic_launcher_xxxhdpi_192.png", "Phone launcher"),
        ("round/ic_launcher_round_xxxhdpi_192.png", "Round launcher"),
        ("tv/tv_launcher_xxxhdpi_320.png", "TV launcher"),
        ("adaptive/ic_launcher_foreground_432.png", "Adaptive foreground"),
    ]
    for index, (path, name) in enumerate(examples):
        x = 96 + index * 496
        body += f'<rect x="{x}" y="250" width="424" height="500" rx="26" fill="#111108"/>'
        body += _image(path, x + 52, 292, 320, 320)
        body += _label(name, x + 30, 695, 27)
    body += _label("TV banner - extra shield/name spacing retained", 96, 855, 31)
    body += _image("tv/tv_banner_master_1920x1080.png", 96, 910, 1120, 630)
    body += _image("previews/adaptive_mask_qa.png", 1350, 900, 1150, 680)
    body += _label("All coloured logo artwork: #E6C352. Opaque asset backgrounds: #030402.", 96, 1750, 30, "#B8B3A4")
    _board(body, 2600, 1900, "qa_contact_sheet.png", profile)

    body = _label("HULK SA - Phone and tablet", 80, 100, 54, GOLD)
    for index, (path, name) in enumerate(examples[1:3]):
        x = 170 + index * 820
        body += _image(path, x, 250, 540, 540) + _label(name, x + 100, 900, 34)
    body += _label("Same app gold. Original launcher footprints and transparent corners.", 80, 1100, 28, "#B8B3A4")
    _board(body, 1800, 1200, "phone_tablet_preview.png", profile)
    for width, height in [(1920, 1080), (3840, 2160)]:
        scale = width / 1920
        body = _label("HULK SA - Android TV banner", 96 * scale, 115 * scale, int(50 * scale), GOLD)
        body += _image("tv/tv_banner_master_3840x2160.png", 320 * scale, 235 * scale, 1280 * scale, 720 * scale)
        _board(body, width, height, f"tv_preview_{width}x{height}.png", profile)


def sync_android_resources() -> None:
    target = PACKAGE / "android-res"
    # This directory is a generated, scoped brand-only mirror, never a full res overlay.
    for file in target.rglob("*"):
        if file.is_file():
            file.unlink()
    resources = REPOSITORY / "app/src/main/res"
    for source in resources.rglob("*"):
        if source.is_file() and (source.stem.startswith(("ic_launcher", "ic_stat_hulk", "hulk_sa_"))
                                or source.name in {"tv_banner.png", "hulk_tv_launcher_aliases.xml"}):
            dest = target / source.relative_to(resources)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, dest)
    colors = target / "values/colors.xml"
    colors.parent.mkdir(parents=True, exist_ok=True)
    colors.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                      '    <color name="ic_launcher_background">#030402</color>\n'
                      '    <color name="hulk_icon_gold">#E6C352</color>\n</resources>\n')


def main() -> None:
    profile = _profile()
    for name, width, height, mode in [
        ("mark", 4096, 4096, "RGBA"), ("badge", 4096, 4096, "RGB"),
        ("lockup", 4096, 2304, "RGB"),
    ]:
        source = PACKAGE / f"master/hulk_sa_{name}_master.svg"
        suffix = "4096x2304" if name == "lockup" else "4096"
        _png(source, PACKAGE / f"master/hulk_sa_{name}_master_{suffix}.png", width, height, mode, profile)
        _vector_exports(source)
    for name, mode in [("foreground", "RGBA"), ("background", "RGB"), ("monochrome", "RGBA")]:
        _png(PACKAGE / f"adaptive/ic_launcher_{name}.svg",
             PACKAGE / f"adaptive/ic_launcher_{name}_432.png", 432, 432, mode, profile)
    _png(PACKAGE / "master/hulk_sa_badge_master.svg", PACKAGE / "play-store/hulk_sa_play_store_512.png", 512, 512, "RGB", profile)
    for density, factor in DENSITIES.items():
        size, tv_size = int(48 * factor), int(80 * factor)
        for family, stem in [("legacy", "ic_launcher"), ("round", "ic_launcher_round")]:
            target = PACKAGE / f"{family}/{stem}_{density}_{size}.png"
            _png(PACKAGE / family / f"{stem}.svg", target, size, size, "RGBA", profile)
            for copy in [PACKAGE / f"android-res/mipmap-{density}/{stem}.png",
                         REPOSITORY / f"app/src/main/res/mipmap-{density}/{stem}.png"]:
                shutil.copyfile(target, copy)
        target = PACKAGE / f"tv/tv_launcher_{density}_{tv_size}.png"
        _png(PACKAGE / "master/hulk_sa_badge_master.svg", target, tv_size, tv_size, "RGB", profile)
        for copy in [PACKAGE / f"android-res/mipmap-{density}/ic_launcher_tv.png",
                     REPOSITORY / f"app/src/main/res/mipmap-{density}/ic_launcher_tv.png"]:
            shutil.copyfile(target, copy)
    shutil.copyfile(PACKAGE / "play-store/hulk_sa_play_store_512.png", REPOSITORY / "app/src/main/res/mipmap-nodpi/hulk_sa_tv_launcher_v14.png")
    export_banners()
    sync_android_resources()
    _previews(profile)
    print("Flat gold master, launcher, banner, runtime copies and preview exports complete")


if __name__ == "__main__":
    main()
