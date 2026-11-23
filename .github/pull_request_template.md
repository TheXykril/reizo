## Summary

<!-- What changed and why. Link the issue: Closes #N -->

## Testing

<!-- Commands run and results. Separate automated tests from manual or untested visual behavior. -->

- [ ] `./gradlew build`
- [ ] `xvfb-run -a ./gradlew :fabric:1.21.11:runClientGameTest` (if `fabric/` changed)
- Manual checks:

## Checklist

- [ ] docs/FEATURES.md updated (status + evidence)
- [ ] docs/HANDOFF.md updated
- [ ] CHANGELOG.md "Unreleased" updated (user-visible changes)
- [ ] ADR added for architectural changes
- [ ] No code from Flashback, Hindsight, Lunar Client or Replay Mod (docs/DECISIONS/0005)
