# False-positive and retry policy

## Classification

Every finding has a stable fingerprint, P0–P3 severity, type, device/API/orientation/density/font
scale, journey, build SHA, expected/actual result, reproduction, evidence, owner, and regression ID.

- Launcher, System UI, permission controller, or Android error dialog is not a product layout bug.
- The foreground package must be the HULK application before screenshot analysis.
- Ancestor/child semantics overlap is ignored; sibling overlap remains eligible.
- A partial LazyRow/LazyColumn teaser is allowed only inside a declared viewport and with the
  configured minimum visible ratio.
- Emulator jank is advisory and cannot become a physical performance blocker.
- Missing screenshot, XML, summary, or expected device artifact is Infrastructure `BLOCKED`.
- Missing markers are a product finding only after foreground/package and build-harness checks pass.

## Retry

Retry is allowed only for findings classified `Infrastructure` or `Flaky`. The first attempt is
retained unchanged. Product failure, assertion failure, crash, or ANR cannot be retried into a
passing result. The aggregate report ignores preserved attempts for final counts but packages them
for review.

## Visual baselines

A baseline needs `approved: true`, a 40-character build SHA, and a recorded reason. A failed capture
cannot become a baseline. PR workflows cannot update baselines. Dynamic masking must use reviewed
rectangles and is recorded in the result. Before, After, and Diff are all retained.

