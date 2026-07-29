# Downloads qualification

`DownloadRepositoryFixtureTest` executes the production repository against MockWebServer and
requires positive transferred bytes, completed state, size, integrity flag, and checksum. The
evidence analyzer additionally validates monotonic progress, part growth, resume offset, final
checksum, and single-writer ownership. Reboot/process-death proof needs managed hardware.

