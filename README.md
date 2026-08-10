# EcoSTG Paper

Lightweight Paper **1.21.11** economy plugin with an auction house, item sell GUI, and jobs.

## Features

- Own SQLite economy (starting balance **500 Dollars**)
- `/pay` — GUI to pick a player, then type the amount in chat
- `/shop` `/ah` `/auction` — forever listings, unlimited per player, purchase confirmation
- `/sell` — drop items in a GUI; prices from `worth.yml`
- `/moneytop` — top 10 richest player heads
- `/ecostg toggle <player> <on|off>` — OP only
- `/job` `/jobsell` — one job at a time, delivery every N in-game days (configurable)
- `/job-timer-reset <player>` — OP only, clears fired cooldown
- All economy actions logged as `[ACTION]` in the server log

## Build

```bash
./gradlew jar
```

Output: `build/libs/EcoSTG-1.0.0.jar`

## Install

1. Drop the jar into your Paper `plugins/` folder
2. Start the server once to generate `config.yml`, `worth.yml`, and `jobs.yml`
3. Adjust prices/jobs as needed and `/reload` or restart

## Config highlights

```yaml
starting-balance: 500.0
currency-name: Dollars
jobs:
  deadline-ingame-days: 4
  fire-cooldown-real-days: 30
  ah-buyer-discount-percent: 8.0
  worker-listing-discount-percent: 5.0
```
