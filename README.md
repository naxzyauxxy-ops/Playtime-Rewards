# PlaytimeRewards

Playtime tracking and milestone rewards for **Purpur 1.21.x** (Java 21), styled after the
FruitVanilla "✹ Playtime | Rewards" menu.

## Build

```bash
./gradlew build          # -> build/libs/PlaytimeRewards-1.0.0.jar
```

To target a different Purpur version, change `purpurVersion` in `build.gradle`.

## Install

1. Drop the jar in `plugins/` (optional: PlaceholderAPI, LuckPerms, EssentialsX).
2. Start the server once, then edit `plugins/PlaytimeRewards/config.yml`:
   * swap `gems give %player% <n>` for your gems plugin's real command
   * check that `eco give` matches your economy plugin
3. `/playtimerewards admin reload`

In `purpur.yml`, set `settings.idle-timeout.kick-if-idle: false` so Purpur marks idle players
AFK instead of kicking them. PlaytimeRewards pauses their playtime while they are AFK.

## PlaceholderAPI

| Placeholder | Example |
|---|---|
| `%playtimerewards_time%` | `1d 20h 49m 29s` (scoreboard format) |
| `%playtimerewards_time_short%` | `1d 20h` |
| `%playtimerewards_time_compact%` | `1d 20h 49m 29s` (only the units that aren't zero) |
| `%playtimerewards_days%` `_hours%` `_minutes%` `_seconds%` | `1` `20` `49` `29` |
| `%playtimerewards_total_seconds%` `_total_minutes%` `_total_hours%` | totals |
| `%playtimerewards_claimed%` / `_total%` / `_ready%` | `10` / `28` / `0` |
| `%playtimerewards_next%` | `3h 10m` |
| `%playtimerewards_next_name%` | `2d` |
| `%playtimerewards_afk%` | `true` / `false` |

Scoreboard line from the screenshot:
`<yellow>⏳ Playtime: %playtimerewards_time%`

## Commands & permissions

| Command | Permission | Default |
|---|---|---|
| `/playtimerewards` (aliases `/playtime`, `/rewards`, `/pt`) | `playtimerewards.use` | everyone |
| `/playtimerewards check` | `playtimerewards.check` | everyone |
| `/playtimerewards check <player>` | `playtimerewards.check.others` | op |
| `/playtimerewards claim` (claim all ready) | `playtimerewards.claim` | everyone |
| `/playtimerewards admin reload` | `playtimerewards.admin.reload` | op |
| `/playtimerewards admin set\|add\|remove <player> <time>` | `playtimerewards.admin.modify` | op |
| `/playtimerewards admin reset <player> [tier\|all]` | `playtimerewards.admin.reset` | op |
| `/playtimerewards admin info <player>` | `playtimerewards.admin.info` | op |
| — keeps counting while AFK | `playtimerewards.afk.exempt` | nobody |
| — gated tiers (`require-permission: true`) | `playtimerewards.tier.<id>` | nobody |

Bundles: `playtimerewards.player`, `playtimerewards.admin`, `playtimerewards.*`.

LuckPerms examples:
```
/lp group default permission set playtimerewards.player true
/lp group mod permission set playtimerewards.admin.info true
/lp group mod permission set playtimerewards.check.others true
/lp group admin permission set playtimerewards.admin true
```

## Storage

Each player gets their own file, `plugins/PlaytimeRewards/data/<uuid>.json`. All reads and writes
run on one background IO thread, so the main thread never waits on disk:

* Data loads during the async pre-login.
* It saves on quit, on every claim and on autosave.
* Each save goes to a temp file first and is then moved into place, so a crash can't leave a
  half-written file.

On first join, existing players are seeded from the vanilla play-time statistic
(`import-vanilla-playtime`).
