# Server setup (Paper 1.21.11)

Drop-in plugins, not built here:

| Plugin | Purpose |
|---|---|
| ViaVersion | newer clients join 1.21.11 |
| ViaBackwards | older clients (1.21.x down to 1.8 range, depending on Via build) |
| ViaRewind | pre-1.9 clients (optional) |
| Vault | required by StrataCore (economy provider) |
| PlaceholderAPI, LuckPerms | optional integrations |
| Geyser + Floodgate | optional, Bedrock |

Build StrataCore: `mvn -f StrataCore/pom.xml clean package` (Java 21) -> `StrataCore/target/StrataCore-1.0.0.jar`.
