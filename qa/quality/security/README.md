# Security qualification

PR workflows receive no production or signing secrets. Release workflows use a protected
environment and ephemeral keystore. Runtime host, manifest permissions/exports, dependency tree,
signing identity, and logo integrity are evidence gates. Vulnerability scanning remains
`NOT EXECUTED` until an approved scanner is configured.

