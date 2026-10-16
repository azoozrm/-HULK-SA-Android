# R03 integration

Apply the enclosing source.patch to a clean isolated checkout at
4b54dfd91bde501ba545245e4690f08fe0881eff. Verify the enclosing manifest first.
Run git apply --check before git apply. The patch includes binary assets, new files,
resource deletions, Kotlin consumers and scoped validation-pin updates.
Do not copy the old R02 overlay over the new official tree.

android-res is a brand-only mirror using the runtime names:
ic_launcher, ic_launcher_round, ic_launcher_tv, tv_banner, ic_stat_hulk,
hulk_sa_mark_reference, hulk_sa_channel_placeholder and hulk_sa_logo.
Its values/colors.xml is a subset to merge, never a replacement for application colors.xml.
The complete patch performs the required merges and deletes the oversized TV foreground;
copying android-res alone cannot remove stale qualifiers or update Kotlin consumers.

The explicit television v26 adaptive definition is required despite the generic v26 definition,
because UI-mode qualification has precedence over platform-version qualification.
Retain television v21 as the pre-26 fallback and retain application TV aliases.

Frozen source/artifact evidence identifies the dirty candidate. Build/install only through
current engineering contracts. No commit, push, PR, merge, signing change, tag or release
is authorized in the build/install handoff. Do not redesign or recolour assets on the executor.
