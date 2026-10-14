# Companion plugins: claims, basics, chat

StrataCore deliberately doesn't rebuild these. Install them alongside it and apply the settings below. Config key names
come from the plugins' current defaults, so check them against the version you install.

## 1. Land claims: GriefPrevention

`plugins/GriefPrevention/config.yml`

```yaml
GriefPrevention:
  Claims:
    Worlds: [world]              # survival world(s) only, not the hub
    InitialBlocks: 200           # starting claim blocks
    BlocksAccruedPerHour_2: 100  # check the exact key name in your version's generated config
    AutomaticNewPlayerClaimsRadius: 4
  Economy:
    ClaimBlocksPurchaseCost: 2.0 # Stratas per claim block (0 turns buying off) - this is a land sink
    ClaimBlocksSellValue: 0      # don't let players sell blocks back, or it becomes a money printer
```

Players buy blocks with `/buyclaimblocks <amount>`. GriefPrevention charges through Vault, so it spends Stratas
automatically (StrataEconomy registers as the Vault provider at the highest priority).

Keystones and boss arenas are built in the open world. Boss arenas avoid player structures; keep keystone runs out of
claims you care about by leaving the `run.worlds` list as is and telling players to open them in the wilderness.

## 2. Basics: EssentialsX (+ EssentialsXChat, EssentialsXSpawn)

`plugins/Essentials/config.yml`

**Stop Essentials taking over StrataCore's commands and economy**

```yaml
# StrataCore provides these
disabled-commands:
  - balance
  - bal
  - balancetop
  - baltop
  - pay
  - eco
  - economy
  - sell
  - worth
  - kit
  - kits
  - repair
  - itemname
  - trade
```

Essentials will still read and charge balances through Vault, which is StrataEconomy. Leave its own `starting-balance` at 0.

**Teleport and home fees (a Stratas sink)** - Essentials charges through Vault:

```yaml
command-costs:
  tpa: 50
  tpahere: 50
  home: 25
  warp: 25
  back: 100
# spawn and hub stay free

teleport-cooldown: 30
teleport-delay: 3          # standing-still warmup; moving cancels it
teleport-invulnerability: 4
sethome-multiple:
  default: 2
  ornate: 4
  regal: 6
```

Players tagged by StrataCombat can't use `/tpa`, `/home`, `/spawn` and similar (list is in `StrataCombat/config.yml`).

**AFK:** `afk-timeout: 300` and `afk-kick-time: 900` (or `-1` to never kick). `/afk` toggles.

**Spawn:** EssentialsXSpawn gives `/spawn` and `/setspawn`. `spawn-on-join: true` sends everyone to the hub first.

## 3. Chat format

EssentialsXChat reads prefixes from LuckPerms.

`plugins/Essentials/config.yml` (chat section)

```yaml
chat:
  format: '{PREFIX}{DISPLAYNAME}&7: &f{MESSAGE}'
```

LuckPerms prefixes, for example:

```
/lp group default meta setprefix 100 "&7"
/lp group ornate meta setprefix 100 "&b[Ornate] "
/lp group regal  meta setprefix 100 "&d[Regal] "
```

StrataRanks exposes `%strataranks_name%` and `%strataranks_namecolor%` (the colour a player picked with `/rankstyle name`)
and StrataEconomy exposes `%stratas_balance_short%`. Use them in a TAB or scoreboard plugin. `/rankstyle chat` needs the
separate **ChatColor** plugin; without it the command reports chat colours as unavailable.

## 4. Linking it to StrataCore

| What | Where |
|---|---|
| Spawn / hub world is exempt from combat tags | `StrataCombat/config.yml` -> `exempt-worlds` (default `hub`; change it to your lobby world's name) |
| Spending Stratas: keystones, items, cosmetics | `StrataServices/config.yml` -> `services` (`/services`) |
| Crate keys as a service | add a service whose command is your crate plugin's give command (example is in the file) |
| Cosmetic unlocks | a service whose command is `lp user %player% permission set <node> true` |
| Weekly boss, KOTH, login streak | `StrataEvents/config.yml` (`/events`, `/koth`, `/daily`) |

## 5. Order of operations on a fresh server

1. Paper 1.21.11, then Vault, Citizens, Votifier, LuckPerms, PlaceholderAPI.
2. ViaVersion, ViaBackwards (and ViaRewind) so any client can join.
3. EssentialsX set, GriefPrevention.
4. StrataCore.jar, start once, stop, fill in `StrataEconomy/config.yml` (database) and the other module configs.
5. Create KOTH zones in game: stand in the centre and run `/koth setzone hill 12`.
6. `/events` to confirm the weekly boss and KOTH times.
