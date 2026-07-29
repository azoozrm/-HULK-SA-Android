# Journey coverage

The machine-readable source is `qa/quality/journeys/journeys.json`. Generated Mermaid traces are
stored in `qa/quality/journeys/graphs/`.

| Journey | State | Automated evidence | Remaining proof |
|---|---|---|---|
| Login → Home | Protected only | none in public PR | protected production smoke |
| Shell destinations | Automated | Compatibility Lab + Compose semantics | upgraded artifact review |
| Movie → Details → Player → Back | Not covered | topology only | legal playback fixture and focus restoration |
| Series → Season → Episode → Next | Not covered | topology only | Media3 episode fixture |
| Download enqueue/recover/play | Partial | production repository positive-byte/integrity test | pause/resume, process death, reboot, offline play |

Every runtime failure must retain the ordered key input, foreground package, focused node before and
after, and screenshot/XML pair. A focus trace fails for an unreachable expected node, lost focus,
or a repeated two-node loop. Back must close the current layer before finishing the activity.

Coordinates are last-resort selectors and require a preceding geometry assertion. Approved order:
Compose semantics, stable debug-only semantics/test tags, UIAutomator selectors, then coordinates.

