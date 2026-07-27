# Canonical source evidence

- `v0.9.3.17-baseline.sha256` records the reconstructed production project before canonical additions.
- `canonical-source.sha256` records the direct Gradle source, wrapper, and build configuration committed by the parity PR.
- The canonical CI builds directly from checkout and does not reconstruct `app/src/main`.
