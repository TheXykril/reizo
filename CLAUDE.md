# CLAUDE.md

All project rules live in [AGENTS.md](AGENTS.md). Read it first; this file only adds
Claude-specific notes and must not duplicate it.

- Follow [docs/AGENT_PROTOCOL.md](docs/AGENT_PROTOCOL.md) at the start and end of every session.
- Verify [docs/HANDOFF.md](docs/HANDOFF.md) against `git log` and the source before trusting it.
- Use the Minecraft sources Loom generates (`./gradlew :fabric:<version>:genSourcesWithVineflower`)
  to check class, method and field names before writing mixins, for every version the code builds
  for. Names are Mojang mappings (26.x is unobfuscated).
- Gradle needs JDK 25. In cloud sessions set `JAVA_HOME` to a JDK 25 first.
- In cloud sessions without a display, run in-game tests per version with
  `xvfb-run -a ./gradlew :fabric:1.21.11:runClientGameTest` (and `:fabric:1.21.10:`, `:fabric:26.1.2:`, `:fabric:26.2:`, `:fabric:26.3:`).
- When unsure whether something is an architectural change, treat it as one: write an ADR and open
  a pull request for the owner.
