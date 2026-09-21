# Android Engineering Lab Operations

This directory contains non-secret, repository-tracked automation for the HULK SA Android
engineering lab.

It does not contain credentials, IP addresses, caches, active task state or product behavior.

## Qualified helpers

- `hulk-task-new`: creates one isolated task worktree after a clean canonical clone and exact
  remote official-HEAD gate.
- `hulk-task-status`: reports canonical and optional task worktree state.
- `hulk-task-close`: removes only a clean registered task worktree and deletes its local branch
  only when it has no unique commit and no remote branch.
- `lab-health.sh`: read-only health summary for toolchain, disk, memory, GitHub and KVM mode.
- `git-hooks/pre-commit`: blocks commits from the canonical clone.
- `git-hooks/pre-push`: blocks direct pushes to the official branch.

Installing or replacing machine files under `/usr/local/bin` or `/etc/hulk-android` is a LAB
MAINTENANCE mutation and requires task authority. Do not execute repository scripts with sudo merely
because they exist here.
