# R25 closed A/B comparison support

This directory contains only the fixed, observation-only diagnostic patch used by
`.github/workflows/r25-causal-comparison.yml`.

- Arm A: `996ec8a0a1cab774cd524a3e1fcb4a3955c7dd83` plus `a-http-trace.patch`.
- Arm B: `34f455f64d94adf680a84e295b7fadfdc2dd0137`, unmodified.
- Both are built as signed release `sa.hulksa.player` 69 / 1.0.1 with the existing
  `production-signing` environment and unchanged signer.
- The patch only attaches an OkHttp event listener that logs a safe action label and HTTP
  status/cancel event. It never logs a URL, query, header, body, credential or account.
- No input, ref, patch path, source SHA or version is user-supplied. The workflow accepts no
  dispatch inputs; the two sources and the patch are fixed in this commit.
