#!/usr/bin/env python3
import argparse
import re
import sys
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('xml')
p.add_argument('--class-contains')
p.add_argument('--text-contains')
p.add_argument('--index', type=int, default=0)
a = p.parse_args()
root = ET.parse(a.xml).getroot()
items = []
for node in root.iter('node'):
    cls = node.attrib.get('class', '')
    text = node.attrib.get('text', '') + ' ' + node.attrib.get('content-desc', '')
    if a.class_contains and a.class_contains.lower() not in cls.lower():
        continue
    if a.text_contains and a.text_contains.lower() not in text.lower():
        continue
    m = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.attrib.get('bounds', ''))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1:
        continue
    items.append(((x1 + x2)//2, (y1+y2)//2, node.attrib))
if a.index >= len(items):
    sys.exit(2)
print(items[a.index][0], items[a.index][1])
