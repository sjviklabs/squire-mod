# Research: Mod DevOps Pipeline & Publishing

Research conducted 2026-03-18 for Squire Mod Phase 1 planning.

## CI/CD Pipeline

### GitHub Actions — Three Stages

**1. Build & Test (every push/PR):**
- JDK 21 (Temurin)
- `gradle/actions/setup-gradle` with caching
- `./gradlew build`
- NeoForge shared asset cache action (MC assets download once)

**2. Quality Gates (on PR):**
- GameTests (NeoForge test framework)
- Verify `neoforge.mods.toml` validity

**3. Publish (on release/tag):**
- Build JAR
- Publish to CurseForge + Modrinth + GitHub Releases via ModPublisher

### NeoForge CI Resources
- NeoForged org maintains reusable actions at github.com/neoforged/
- Discord webhook action for build notifications

## Publishing Platforms

**Ship to both CurseForge and Modrinth.** Different audiences.

| Factor | CurseForge | Modrinth |
|--------|-----------|----------|
| Library size | Larger, established | Growing fast |
| Upload review | Manual, can take days | Automated, often minutes |
| Open source | Proprietary (Overwolf) | Fully open source |
| API | Requires key | Open REST API |
| Community | Older, larger user base | Developer-preferred |

## Multi-Platform Publishing Tools

| Tool | Type | Publishes To |
|------|------|-------------|
| **ModPublisher** (Recommended) | Gradle plugin | CurseForge + Modrinth + GitHub |
| mod-publish-plugin | Gradle plugin | CurseForge + Modrinth + GitHub |
| MC-Publish | GitHub Action | CurseForge + Modrinth + GitHub |
| Minotaur | Gradle plugin | Modrinth only |
| CurseGradle | Gradle plugin | CurseForge only |

**Our choice:** ModPublisher — one plugin, three platforms, one `./gradlew publishMod` task. Env vars: `MODRINTH_TOKEN`, `CURSE_TOKEN`, `GITHUB_TOKEN`.

## Versioning

NeoForge convention (docs.neoforged.net):
- **Semver** `MAJOR.MINOR.PATCH` baseline
- MC version in filename: `squire-neoforge-1.21.1-0.1.0.jar`
- Our format: `squire-neoforge-{mcVersion}-{modVersion}.jar`

## Distribution Format (JAR)

```
META-INF/
  neoforge.mods.toml    # Required mod metadata
  MANIFEST.MF
com/sjviklabs/squire/   # Compiled classes
assets/squire/           # Client resources (textures, models, lang)
data/squire/             # Server data (recipes, loot tables, tags)
```

`pack.mcmeta` is no longer required — modern NeoForge generates it at runtime.

## Testing

### Three Tiers

1. **JUnit** — pure logic (damage calcs, equipment comparison, config parsing). Fast.
2. **NeoForge Test Framework** — JUnit inside real MC server context. `@ExtendWith(EphemeralTestServerProvider.class)`.
3. **GameTests** — structure-based in-game tests. Place structures, assert entity behavior. `@GameTest` annotation.

### Practical
- mcjunitlib for standard JUnit without class-loading issues
- F3 debug screen + commands for manual testing
- ModDevGradle auto-generates run configs

## Licensing

| License | Notes |
|---------|-------|
| **MIT** (our choice) | Maximum adoption, anyone can fork. Best for portfolio pieces. |
| LGPL-3.0 | Copyleft for mod itself, doesn't infect modpacks |
| Apache 2.0 | Like MIT + patent grant |
| GPL | Avoid for MC mods — technically invalid without classpath exception |

## Monetization

### CurseForge Rewards
- Points from monthly revenue pool based on relative popularity
- Payout via PayPal, Amazon gift cards, bank transfer
- Need significant downloads for real money

### Modrinth Creator Program
- 75% of site ad revenue to creators
- Payout via PayPal, Venmo, gift card
- NET 60 day cycle

### Other
- Patreon/Ko-fi for dedicated communities (cosmetic skins, early access, feature votes)
- None are "quit your job" money for most mods — 100K+ monthly downloads needed for meaningful passive income

## Community Building

1. CurseForge/Modrinth descriptions — screenshots, feature lists, compatibility
2. Discord server — feedback loop, bug reports
3. YouTube showcases — mod spotlights drive downloads hard
4. Reddit (r/feedthebeast, r/Minecraft)
5. Modpack inclusion — biggest organic growth driver
6. Wiki/docs — GitHub wiki or Patchouli in-game guide
