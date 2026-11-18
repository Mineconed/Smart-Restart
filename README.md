<p align="center"><img src="SmartRestart_ikon_128.png" width="128"></p>
<h1 align="center">SmartRestart</h1>
<p align="center">Scheduled and smart restarts with a clean countdown. Made by <b>Mineconed</b>.</p>

## Features
- ⏰ Fixed restart times (fx 06:00 and 18:00) with timezone
- 🧠 Smart restart on low TPS, high RAM or max uptime
- 💬 Countdown in chat, title, actionbar and bossbar with sounds
- 📢 Discord webhook before the restart and when back online
- 💾 Saves worlds and players first
- 🌍 English and Danish
- ✅ 1.8 to 1.21, Spigot and Paper

## Commands
| Command | What it does |
|---|---|
| `/sr status` | Next restart, uptime, TPS and RAM |
| `/sr now [seconds] [reason]` | Start a countdown now |
| `/sr cancel` | Cancel the countdown |
| `/sr reload` | Reload config and messages |

Permission: `smartrestart.admin` (op by default)

## Build
`mvn package` gives `target/SmartRestart.jar`.
