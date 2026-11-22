# CLAUDE.md

All project rules live in [AGENTS.md](AGENTS.md). Read it first; this file only adds
Claude-specific notes and must not duplicate it.

- Follow [docs/AGENT_PROTOCOL.md](docs/AGENT_PROTOCOL.md) at the start and end of every session.
- Verify [docs/HANDOFF.md](docs/HANDOFF.md) against `git log` and the source before trusting it.
- Use the Minecraft sources Loom generates (`./gradlew :fabric:genSourcesWithVineflower`) to check
  class, method and field names before writing mixins. Names are Mojang mappings.
- In cloud sessions without a display, run in-game tests with
  `xvfb-run -a ./gradlew :fabric:runClientGameTest`.
- When unsure whether something is an architectural change, treat it as one: write an ADR and open
  a pull request for the owner.
