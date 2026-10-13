# EcoSTG

Lightweight [Paper](https://papermc.io/) / [Purpur](https://purpurmc.org/) plugin: economy, auction house, jobs, and an ESC hub menu (Dialog API).

**Current version:** `1.5.22`  

Built for **Paper 1.21.11**, **26.1**, **26.1.2**, **26.2**, and **26.3** — pick the jar that matches your server.

| Server | Folder / jar | Java |
|--------|----------------|------|
| Paper/Purpur **1.21.11** | [`builds/1.21.11/`](builds/1.21.11/) | 21+ |
| Paper **26.1** | [`builds/26.1/`](builds/26.1/) | 25+ |
| Paper **26.1.2** | [`builds/26.1.2/`](builds/26.1.2/) | 25+ |
| Paper **26.2** | [`builds/26.2/`](builds/26.2/) | 25+ |
| Paper **26.3** | [`builds/26.3/`](builds/26.3/) | 25+ |

Release notes: [`releases/`](releases/).

## Requirements

- Matching **Paper** (or Purpur) line from the table above — do not mix jars across lines
- No Vault or other economy plugins — EcoSTG uses its own SQLite economy

## Features

### Economy
- Starting balance: **500 Dollars** (configurable)
- `/shop` `/ah` `/auction` — auction house; `/ah <search>` filters listings (e.g. `/ah dirt`)
- `/sell` — instant pay at `worth.yml` prices (shulkers include contents); items also list on AH
- `/bal` `/balance` `/money` — show your balance (or another player's, if they allow it)
- `/moneytop` — top 10 richest players
- `/ecoset <player> <amount>` / `/ecogive <player> <amount>` — OP
- `/ecostg toggle <player> <on|off>` — disable economy for a player (OP)

### Jobs
- `/job` — choose one job
- `/jobsell` — sell required job items **on the AH** with badge **By a worker** + discount `%` (visible to everyone)
- `/jobinfo` — job status above the hotbar (days until delivery due)
- Miss the in-game-day deadline → fired + real-day cooldown
- `/jobcancel` `/activejobs` `/job-timer-reset` — OP

### Homes
Named homes via the hub or `/home` / `/sethome` / `/delhome`. Default max: **3** (`homes.max`). Operators have **no limit**.

- List opens as a **dialog** (not a chest). Each home button uses that home's color.
- Per home: **Teleport**, **Change Color**, **Rename**, **Delete**
- **Change Color** presets: Aqua, White, Red, Orange, Yellow, Green, Blue, Purple, Pink, Gray
- Or enter a custom **HEX** color (`#RRGGBB`, e.g. `#55FFFF`)
- Default color: **Aqua** (`#55FFFF`)
- Names can include spaces (1–32 characters: letters, numbers, spaces, `_`, `-`)

Existing homes are kept across updates. Old item-icon values fall back to the default color.

### ESC / hub menu
Tapping the pause-screen button (default label **EcoSTG**) opens the hub directly. Same menu via `/menu` (`/ecostgmenu`).

Hub: Homes, Auction, Sell, Teleport, Leaderboards, RTP, Friends, Pay, Stats, Ender Chest, Sit/lay (if GSit), Gamemode (OP), **Settings** (last).

Every hub feature also has a chat command.

| Button | What it does |
|--------|----------------|
| Homes | Colored named homes; teleport, color, rename, delete (`/home`, `/sethome`, `/delhome`) |
| Auction / Sell | Auction house (`/shop`); instant sell (`/sell`) |
| Teleport | TPA / TPAHere (`/tpa`, `/tpahere`). Target must accept unless privacy allows instant |
| Leaderboards | Money, playtime, kills, deaths, blocks, mobs (`/leaderboard`) |
| RTP | Random safe spot in the **current world**, near you, inside the world border (`/rtp`, cooldown) |
| Friends | Follow someone; friends when both follow (`/friend`). Filter, search, and open a player profile |
| Pay | Pay online players (`/pay`) |
| Stats | Player stats preview; respects privacy (`/stats`) |
| Ender Chest | Open your ender chest from anywhere (`/enderchest`, `/ec`) |
| Sit/lay | Shown only if [GSit](https://modrinth.com/plugin/gsit) is installed |
| Gamemode | OP only: Survival / Creative / Adventure / Spectator |
| Settings | Tap-cycle privacy/chat/notifications, bio, visuals (`/settings`) — always last |

`/ecostg mainletters change <name>` — OP. Live menus update immediately; the ESC button label needs a **server restart**.

### Combat
Hitting another player, or taking any damage, starts a combat timer above the hotbar: `Combat: <secs>s` (seconds in blue). While it is running, **home / TPA / TPAHere / RTP** are blocked. Taking or dealing damage again resets the timer.

Operators are **never** tagged. If an OP fights a non-OP, only the non-OP gets the timer.

### Friends
Following is one-way. You become **friends** only when both players follow each other.

- `/friend add <player>` — follow
- `/friend remove <player>` — unfollow (ends friendship if you were friends)
- `/friend list` — friends, following, and followers
- Friends menu: **Filter**, **Search**, player buttons, **+ Follow**, and a profile (View Stats, Pay, Teleport, Unfollow)

Chat filter, kill/death notifications, and instant TPA set to **Friends** use mutual follows.

### Logging
Important actions are logged as `[ACTION]` in the server console.

## Commands

| Command | Who | Description |
|---------|-----|-------------|
| `/menu` (`/ecostgmenu`) | Everyone | Open hub |
| `/ecostg` `/ecostg help` | Everyone | In-game help |
| `/tpa [player]` `/tpahere [player]` (`/tphere`) | Everyone | Teleport requests |
| `/tpaccept` (`/tpyes`) `/tpdeny` (`/tpno`) | Everyone | Accept / deny TPA |
| `/rtp` | Everyone | Random teleport (cooldown) |
| `/home [name]` (`/homes`) `/sethome [name]` `/delhome <name>` | Everyone | Homes |
| `/stats [player]` | Everyone | Stats (respects privacy) |
| `/friend add\|remove\|list [player]` (`/friends`) | Everyone | Follow; friends when both follow |
| `/leaderboard [money\|playtime\|kills\|deaths\|placed\|broken\|mobs]` (`/lb`) | Everyone | Leaderboards |
| `/settings` | Everyone | Settings menu |
| `/enderchest` (`/ec`, `/ender`) | Everyone | Open your ender chest |
| `/pay [player] [amount]` | Everyone | Pay (GUI if you omit args) |
| `/bal [player]` (`/balance`, `/money`) | Everyone | Balance |
| `/shop` (`/ah`, `/auction`) [search] `/moneytop` | Everyone | Auction house (optional search) |
| `/sell` | Everyone | List items on AH at worth.yml prices |
| `/job` `/jobsell` `/jobinfo` | Everyone | Jobs |
| `/ecoset` `/ecogive` `/ecostg toggle` | OP | Economy admin |
| `/jobcancel` `/activejobs` `/job-timer-reset` | OP | Jobs admin |
| `/ecostg mainletters change <name>` | OP | ESC / hub title |

## Build

Build every Paper target into `builds/<platform>/`:

```bash
./gradlew buildAll
```

Or one platform, e.g. `./gradlew :paper-26.3:build`.

Use a JDK that can run Gradle (21+). Platform modules use toolchains (JDK 21 for 1.21.11, JDK 26 for Paper 26.x). On Windows:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\jdk-21\jdk-21.0.12.1+1"
.\gradlew.bat buildAll
```

## Install

1. Copy `builds/<your-paper-version>/EcoSTG-1.5.22.jar` into `plugins/`
2. Start once to generate `config.yml`, `worth.yml`, `jobs.yml`, and `ecostg.db`
3. Edit configs, then **restart** (prefer restart over `/reload` for Paper/Purpur plugins)

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

combat:
  duration-seconds: 15

auction:
  sale-tax-percent: 5.0

jobs:
  deadline-ingame-days: 4
  fire-cooldown-real-days: 30
  ah-buyer-discount-percent: 8.0
  worker-listing-discount-percent: 5.0
```

- `worth.yml` — instant-sell prices for every item (auto-filled on startup; your edits are kept)
- `jobs.yml` — job definitions and delivery items

Existing `config.yml` files keep their current values; new keys use the defaults above.

## Notes

- Built as a **Paper plugin** (`paper-plugin.yml` + bootstrap); runs on **Paper and Purpur**. Commands are registered in code, not via YAML.
- Server Links entries are vanity URLs only; the real menu is the **ESC button** or `/menu`.
- Changing `main-letters` updates live menus immediately; the ESC pause button label updates after restart.
- RTP loads chunks asynchronously and searches around the player (capped radius), not the entire default world border.
- Money nametag (Settings → Visuals) shows **only** under the player's head in the world. It does not appear in chat, tab, or the console.
- Homes use dialog menus. Colors are stored in SQLite; upgrading does not delete homes.

## License

MIT — see [LICENSE](LICENSE).
