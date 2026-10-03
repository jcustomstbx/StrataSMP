# Server setup (Paper 1.21.11)

Drop-in plugins, not built here.

| Plugin | Status | Purpose |
|---|---|---|
| Vault | **required** | economy provider for other plugins |
| Citizens | **required** | hub entry NPCs |
| Votifier (NuVotifier) | **required** | vote rewards |
| ViaVersion | recommended | newer clients join 1.21.11 |
| ViaBackwards | recommended | older clients |
| ViaRewind | optional | pre-1.9 clients |
| PlaceholderAPI, LuckPerms | optional | placeholders, skin unlocks |
| ModelEngine | optional | boss models (blueprints go in `plugins/ModelEngine/blueprints`) |
| Multiverse-Core, AntiFloodGuard, RankEssentials, CrazyCrates, ChatColor | optional | integrations |
| Geyser + Floodgate | optional | Bedrock |

You also need a MariaDB/MySQL database for the economy (see the root README) and the resource pack from `resourcepack/`
(set `resource-pack` and `resource-pack-sha1` in `server.properties`).

Build StrataCore: `mvn -f StrataCore/pom.xml clean package` (Java 21) -> `StrataCore/target/StrataCore-1.0.0.jar`.
