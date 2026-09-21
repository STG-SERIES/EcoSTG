# EcoSTG Paper - Purpur

Lightweight **Paper - Purpur 1.21.11** plugin: economy, auction house, jobs, and an ESC hub menu (Dialog API).

**Current version:** `1.5.11`   **Output jar:** `build/libs/EcoSTG-1.5.11.jar`

## Requirements

*   Paper - Purpur**1.21.11+** (Java 21)
*   No Vault or other economy plugins required (own SQLite economy)

## Features

### Economy

*   Starting balance: **500 Dollars** (configurable)
*   `/pay` — pick a player and amount (hub + presets / custom), or `/pay <player> <amount>`
*   `/shop` `/ah` `/auction` — auction house (forever listings, unlimited per player, buy confirmation)
*   `/sell` — list any item on the AH with a **custom price**
*   `/moneytop` — top 10 richest players
*   `/ecoset <player> <amount>` / `/ecogive <player> <amount>` — OP
*   `/ecostg toggle <player> <on|off>` — disable economy for a player (OP)

### Jobs

*   `/job` — choose one job
*   `/jobsell` — sell required job items **on the AH** with badge **By a worker** + discount `%` (visible to everyone)
*   `/jobinfo` — chat status (days until delivery due)
*   Miss the in-game-day deadline → fired + real-day cooldown
*   `/jobcancel` `/activejobs` `/job-timer-reset` — OP

### ESC / hub menu

*   Pause screen button labeled with **main letters** (default `EcoSTG`)
*   Or `/menu` (`/ecostgmenu`)
*   Hub: Homes, Auction, Sell, Teleport, Leaderboards, RTP, RTP Queue, Friends, Pay, Stats, Settings
*   Matching chat commands for every hub feature (`/tpa`, `/rtp`, `/stats`, `/home`, …)
*   `/ecostg mainletters change <name>` — OP (ESC button title needs a **server restart**)

| Button         |What it does                                                                                         |
| -------------- |---------------------------------------------------------------------------------------------------- |
| Homes          |Up to 3 homes; set &amp; instant teleport                                                            |
| Auction / Sell |Existing AH GUIs                                                                                     |
| Teleport       |TPA / TPAHere (accept required; no timeout). Privacy can allow <strong>instant</strong> TPA for anyone/friends/nobody |
| Leaderboards   |Money, playtime, kills, deaths, blocks, mobs                                                         |
| RTP            |Random spot in <strong>current world</strong>, inside the world border (cooldown)                    |
| RTP Queue      |Teleport to a random online player (no cooldown)                                                     |
| Friends        |Follow someone; you are friends only when they follow you back                                       |
| Pay            |Pay online players                                                                                   |
| Stats          |View a player's profile (respects privacy settings)                                                  |
| Settings       |Chat, notifications, visuals, privacy, general toggles                                               |

### Logging

Important actions are logged as `[ACTION]` in the server console.

## Commands

| Command                                      |Who      |Description                      |
| -------------------------------------------- |-------- |-------------------------------- |
| <code>/menu</code>                           |Everyone |Open hub                         |
| <code>/ecostg</code> / <code>/ecostg help</code> |Everyone |In-game help                     |
| <code>/tpa [player]</code> <code>/tpahere [player]</code> |Everyone |Teleport requests                |
| <code>/tpaccept</code> <code>/tpdeny</code>  |Everyone |Accept / deny TPA                |
| <code>/rtp</code> <code>/rtpq</code>         |Everyone |Random teleport / random player  |
| <code>/home [name]</code> <code>/sethome [name]</code> <code>/delhome &lt;name&gt;</code> |Everyone |Homes                            |
| <code>/stats [player]</code>                 |Everyone |Stats (respects privacy)         |
| <code>/friend add|remove|list</code>         |Everyone |Follow; friends when both follow |
| <code>/leaderboard [type]</code> <code>/lb</code> |Everyone |Leaderboards                     |
| <code>/settings</code>                       |Everyone |Settings menu                    |
| <code>/pay [player] [amount]</code> <code>/bal [player]</code> |Everyone |Pay / balance                    |
| <code>/shop</code> <code>/sell</code> <code>/moneytop</code> |Everyone |Auction house                    |
| <code>/job</code> <code>/jobsell</code> <code>/jobinfo</code> |Everyone |Jobs                             |
| <code>/ecoset</code> <code>/ecogive</code> <code>/ecostg toggle</code> |OP       |Economy admin                    |
| <code>/jobcancel</code> <code>/activejobs</code> <code>/job-timer-reset</code> |OP       |Jobs admin                       |
| <code>/ecostg mainletters change &lt;name&gt;</code> |OP       |ESC / hub title                  |

## Build

```
./gradlew jar
```

Use JDK 21. On Windows with a custom JDK:

```
$env:JAVA_HOME = "$env:USERPROFILE\jdk-21\jdk-21.0.12+8"
.\gradlew.bat jar
```

## Install

1.  Put `EcoSTG-1.3.9.jar` in `plugins/`
2.  Start once to generate `config.yml`, `worth.yml`, `jobs.yml`, and `ecostg.db`
3.  Edit configs, then **restart** (prefer restart over `/reload` for Paper plugins)

## Config highlights

```
starting-balance: 500.0
currency-name: Dollars
currency-symbol: "$"


menu:
&nbsp; main-letters: "EcoSTG"


homes:
&nbsp; max: 3


rtp:
&nbsp; cooldown-seconds: 30
&nbsp; max-radius: 1500
&nbsp; min-radius: 32
&nbsp; max-attempts: 80


auction:
&nbsp; sale-tax-percent: 5.0


jobs:
&nbsp; deadline-ingame-days: 4
&nbsp; fire-cooldown-real-days: 30
&nbsp; ah-buyer-discount-percent: 8.0
&nbsp; worker-listing-discount-percent: 5.0
```

*   `worth.yml` — suggested AH prices (and sell hints)
*   `jobs.yml` — job definitions and delivery items

## Notes

*   This is a **Paper and Purpur plugin** (`paper-plugin.yml` + bootstrap). Commands are registered in code, not via YAML.
*   Server Links entries are vanity URLs only; the real menu is the **ESC button** or `/menu`.
*   Changing `main-letters` updates live menus immediately; the ESC pause button label updates after restart.

Enjoy, _STGHECKER_| Leaderboards | Money, playtime, kills, deaths, blocks, mobs |
| RTP | Random spot in **current world**, inside the world border (cooldown) |
| RTP Queue | Teleport to a random online player (no cooldown) |
| Friends | Follow someone; you are friends only when they follow you back |
| Pay | Pay online players |
| Stats | View a player's profile (respects privacy settings) |
| Settings | Chat, notifications, visuals, privacy, general toggles |

### Logging
Important actions are logged as `[ACTION]` in the server console.

## Commands

| Command | Who | Description |
|---------|-----|-------------|
| `/menu` | Everyone | Open hub |
| `/ecostg` / `/ecostg help` | Everyone | In-game help |
| `/tpa [player]` `/tpahere [player]` | Everyone | Teleport requests |
| `/tpaccept` `/tpdeny` | Everyone | Accept / deny TPA |
| `/rtp` `/rtpq` | Everyone | Random teleport / random player |
| `/home [name]` `/sethome [name]` `/delhome <name>` | Everyone | Homes |
| `/stats [player]` | Everyone | Stats (respects privacy) |
| `/friend add\|remove\|list` | Everyone | Follow; friends when both follow |
| `/leaderboard [type]` `/lb` | Everyone | Leaderboards |
| `/settings` | Everyone | Settings menu |
| `/pay [player] [amount]` `/bal [player]` | Everyone | Pay / balance |
| `/shop` `/sell` `/moneytop` | Everyone | Auction house |
| `/job` `/jobsell` `/jobinfo` | Everyone | Jobs |
| `/ecoset` `/ecogive` `/ecostg toggle` | OP | Economy admin |
| `/jobcancel` `/activejobs` `/job-timer-reset` | OP | Jobs admin |
| `/ecostg mainletters change <name>` | OP | ESC / hub title |

## Build

```bash
./gradlew jar
```

Use JDK 21. On Windows with a custom JDK:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\jdk-21\jdk-21.0.12+8"
.\gradlew.bat jar
```

## Install

1. Put `EcoSTG-1.3.9.jar` in `plugins/`
2. Start once to generate `config.yml`, `worth.yml`, `jobs.yml`, and `ecostg.db`
3. Edit configs, then **restart** (prefer restart over `/reload` for Paper plugins)

## Config highlights

```yaml
starting-balance: 500.0
currency-name: Dollars
currency-symbol: "$"

menu:
  main-letters: "EcoSTG"

homes:
  max: 3

rtp:
  cooldown-seconds: 30
  max-radius: 1500
  min-radius: 32
  max-attempts: 80

auction:
  sale-tax-percent: 5.0

jobs:
  deadline-ingame-days: 4
  fire-cooldown-real-days: 30
  ah-buyer-discount-percent: 8.0
  worker-listing-discount-percent: 5.0
```

- `worth.yml` — suggested AH prices (and sell hints)
- `jobs.yml` — job definitions and delivery items

## Notes

- This is a **Paper plugin** (`paper-plugin.yml` + bootstrap). Commands are registered in code, not via YAML.
- Server Links entries are vanity URLs only; the real menu is the **ESC button** or `/menu`.
- Changing `main-letters` updates live menus immediately; the ESC pause button label updates after restart.
