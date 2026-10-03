# StrataSMP

`StrataCore` is the single Paper plugin behind the SMP. Every feature is an internal module (startup order is in
`StrataCore.java`). If one module fails to start it is skipped and the rest keep running; only a StrataEconomy failure
(usually bad database settings) stops the whole plugin.

## Modules

| Module | What it does |
|---|---|
| StrataEconomy | Stratas currency, `/shop`, `/sell`, auction house, buy orders, Vault provider, `%stratas_*%` placeholders |
| StrataWeapons | custom weapons, skins, kill messages, StrataCharms |
| StrataPerks | premium currency and shop |
| StrataMMO | skills, daily quests (`/mmo quests`), +10% XP multiplier |
| StrataKeystones | overworld keystone drops, 3-wave runs, levels 1-10, team parties (`/keystone join`) |
| StrataBosses | five world bosses with arenas, damage-share rewards, `/boss list|tp|kill|reload` |
| StrataTeams, StrataHub, StrataKits, StrataCrateVault, StrataTrade, StrataLeaderboards, StrataRanks | teams and homes, hub/NPC entry and lockdown, rank kits, crate vault, safe trading, floating leaderboards, rank cosmetics |
| StrataDuels | duel queue, challenges, ELO, FFA |
| StrataVoteReward | vote rewards paid in Stratas |
| StrataStore | `/store` link |

## Requirements

- Paper 1.21.11, Java 21.
- **Required plugins (hard dependencies):** Vault, Citizens, Votifier (NuVotifier). StrataCore will not load without all three.
- **Database:** a MariaDB/MySQL database for StrataEconomy. Set host, port, name, user and password in
  `plugins/StrataEconomy/config.yml` (the shipped password is `CHANGE_ME`). StrataTrade uses an embedded H2 file and needs nothing.
- **Optional plugins:** PlaceholderAPI, LuckPerms (skin unlocks), ModelEngine (boss models), Multiverse-Core,
  AntiFloodGuard, RankEssentials, CrazyCrates, ChatColor.
- Cross-version joining: ViaVersion, ViaBackwards, ViaRewind (see `server/README.md`).

## First start and old data

- Remove the old Milly plugin jars; they must not run next to StrataCore.
- On first start the data (not the configs) from `plugins/MillyTeams`, `MillyHub`, `MillyKits`, `MillyCrateVault`,
  `MillyCustomWeapons`, `MillySovereigns`, `MillyMMO`, `MillyTrade`, `MillyDuels`, `MillyVoteReward`,
  `MillyLeaderboards`, `MillyBosses` and `MillyRanks` is copied into the matching `plugins/Strata*` folder. Existing files are
  never overwritten and a finished import leaves a `.legacy-imported` marker. Do this before the server has created
  new data, and check the folder names against your live `plugins/` directory first. Old database tables are not migrated.
- Before launch, replace the placeholder `stratasmp.com` links (store, vote, web APIs) in the module configs and in
  `StrataStore`, `StoreLink`, `RankShowcase` and `VoteReminder`.

## Layout

`StrataCore/` Maven project (`mvn -f StrataCore/pom.xml clean package`), `resourcepack/` resource pack and artwork,
`server/` server setup notes. Module defaults live in `src/main/resources/modules/<Module>/`; runtime data in
`plugins/<Module>/`. Keystone level rewards are empty command lists in `StrataKeystones/config.yml`.
