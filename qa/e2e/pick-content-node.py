#!/usr/bin/env python3
import argparse
import re
import sys
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument("xml")
p.add_argument("--index", type=int, default=0)
a = p.parse_args()

root = ET.parse(a.xml).getroot()
raw = []
max_x = 1
max_y = 1
for node in root.iter("node"):
    m = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    max_x = max(max_x, x2)
    max_y = max(max_y, y2)
    raw.append((node, x1, y1, x2, y2))

excluded = (
    "الرئيسية", "البث", "مباشر", "القنوات", "الأفلام", "افلام", "المسلسلات", "مسلسلات",
    "المفضلة", "البحث", "التنزيلات", "التحميلات", "الإعدادات", "الاعدادات", "تسجيل الخروج",
    "الكل", "تحديث", "رجوع",
)

candidates = []
for node, x1, y1, x2, y2 in raw:
    width = x2 - x1
    height = y2 - y1
    area = width * height
    if width <= 0 or height <= 0:
        continue
    clickable = node.attrib.get("clickable") == "true"
    focusable = node.attrib.get("focusable") == "true"
    if not (clickable or focusable):
        continue
    text = ((node.attrib.get("text", "") or "") + " " + (node.attrib.get("content-desc", "") or "")).strip()
    if any(word in text for word in excluded):
        continue
    if area < max(1800, int(max_x * max_y * 0.002)):
        continue
    if area > int(max_x * max_y * 0.45):
        continue
    if y2 < int(max_y * 0.15):
        continue
    if y1 > int(max_y * 0.94):
        continue
    center_x = (x1 + x2) // 2
    center_y = (y1 + y2) // 2
    centrality = abs(center_x - max_x / 2) / max_x
    score = (center_y, centrality, -area)
    candidates.append((score, center_x, center_y, text, node.attrib.get("class", "")))

candidates.sort(key=lambda item: item[0])
if a.index >= len(candidates):
    sys.exit(2)
print(candidates[a.index][1], candidates[a.index][2])
