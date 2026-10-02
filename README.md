# StrataSMP

`StrataCore` is the single Paper plugin backing the SMP. Features run as internal modules (see `StrataCore.java` for startup order).

| Module | Origin | Notes |
|---|---|---|
| StrataEconomy | MillyCrowns | Stratas currency, shop, sell, auction house, Vault provider |
| StrataWeapons | MillyCustomWeapons | custom weapons, skins, kill messages, StrataCharms |
| StrataPerks | MillySovereigns | premium currency + shop |
| StrataMMO | MillyMMO | skills/RPG stats, to be reworked |
| StrataRanks | MillyRanks | rank cosmetics, required by the economy |
| StrataStore | MillyStore | `/store` link (URL in `StrataStore.java` is a placeholder) |
| StrataKeystones | new | overworld keystone drops, 3-wave runs, level 1-10 |

Module defaults live in `src/main/resources/modules/<Module>/`; runtime data in `plugins/<Module>/`.
Rewards for keystone levels are empty command lists in `StrataKeystones/config.yml`.
