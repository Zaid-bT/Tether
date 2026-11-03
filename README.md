# Tether

Android app that enforces daily per-app time limits. Settings are protected by a
passcode ideally set by someone you trust.

**Status:** early development. Not usable yet.

## Planned features
- Daily per-app time budgets
- Block screen when a limit is reached
- Trusted-person passcode with a 12-hour forgot-code reset
- Uninstall and settings protection
- Focus timers and schedules

## Commit message convention

| Type | Use for | Example |
|---|---|---|
| `feat` | New functionality | `feat: list installed apps with search and selection` |
| `fix` | Bug fix | `fix: restore block screen after process restart` |
| `docs` | README, comments, documentation | `docs: add README and license` |
| `refactor` | Code restructure with no behavior change | `refactor: align source package with applicationId` |
| `chore` | Setup, config, dependencies, tooling | `chore: disable app backup` |
| `test` | Adding or changing tests | `test: add budget reset unit tests` |
| `wip` | Unfinished or untested work, only on feature branches, never on `main` | `wip: app picker (untested)` |


## Privacy
All data stays on your device. No network access, no analytics.

## License
See [LICENSE](LICENSE).
