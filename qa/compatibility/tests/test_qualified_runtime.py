from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import unittest
from qualified_runtime import build_spatial_graph, extract_layout, shortest_path

XML = '''<?xml version="1.0"?><hierarchy><node>
<node text="كل الشبكات" focusable="true" clickable="true" bounds="[700,50][900,120]" />
<node text="الجدولة الان" focusable="true" clickable="true" bounds="[480,50][680,120]" />
<node text="متزامنة 2" focusable="true" clickable="true" bounds="[260,50][460,120]" />
<node text="ايقاف مؤقت" focusable="true" clickable="true" bounds="[650,200][900,270]" />
<node text="عادية" focusable="true" clickable="true" bounds="[440,200][630,270]" />
<node text="الغاء" focusable="true" clickable="true" bounds="[230,200][420,270]" />
<node text="ايقاف مؤقت" focusable="true" clickable="true" bounds="[650,300][900,370]" />
<node text="عادية" focusable="true" clickable="true" bounds="[440,300][630,370]" />
<node text="الغاء" focusable="true" clickable="true" bounds="[230,300][420,370]" />
</node></hierarchy>'''.encode('utf-8')

class QualifiedRuntimeTests(unittest.TestCase):
    def test_layout_assigns_semantic_rows(self):
        layout = extract_layout(XML)
        self.assertEqual({
            "toolbar-wifi", "toolbar-schedule", "toolbar-concurrent",
            "row-1-primary", "row-1-priority", "row-1-cancel",
            "row-2-primary", "row-2-priority", "row-2-cancel",
        }, set(layout))

    def test_graph_uses_physical_geometry(self):
        graph = build_spatial_graph(extract_layout(XML))
        self.assertEqual("row-1-primary", graph["toolbar-wifi"]["DOWN"])
        self.assertEqual("row-1-priority", graph["toolbar-schedule"]["DOWN"])
        self.assertEqual("row-1-cancel", graph["toolbar-concurrent"]["DOWN"])
        path = shortest_path(graph, "row-1-primary", "row-2-cancel")
        self.assertIsNotNone(path)
        self.assertEqual("row-2-cancel", path[-1][1])

    def test_unreachable_target_returns_none(self):
        graph = build_spatial_graph(extract_layout(XML))
        self.assertIsNone(shortest_path(graph, "row-1-primary", "row-4-primary"))

if __name__ == "__main__":
    unittest.main()
