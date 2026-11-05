# Minecraft Server Gameplay Plugin: ServerWorld

ServerWorld is a configurable Minecraft Paper plugin that adds random world events, an economy shop, player balances, purchasable ranks, kill tracking, jobs, AFK payouts, spawn and wild teleportation, randomly generated bosses and ruins, minigame areas, and periodic chunk effects to a survival server.

## Install

1. Build the plugin with Java 21 and Maven:
   ```text
   mvn package
   ```
2. Copy the generated `target/plugin.jar` file into your Paper server's `plugins` folder.
3. Start the server once to generate the configuration files.
4. Stop the server and edit `plugins/ServerWorld/config.yml`.
5. Configure the spawn, wild destination, boss area, structure world, minigame regions, shop items, ranks, jobs, events, and chunk effects.
6. Start the server again.
7. Ensure the server runs a compatible Paper version with Java 21. The project is configured against the Paper 1.21.11 API and uses plugin API version 1.21.

Administrators can also set the main locations in game:

```text
/serveradmin set spawn
/serveradmin set wild
```

These commands require the `serverworld.admin` permission, which is granted to server operators by default.

## Use

Player commands:

```text
/spawn
```

Teleports the player to the configured spawn location.

/wild

Teleports the player to the configured wild location.

/shop

Displays configured shop items and their prices.

/buy <item>

Purchases a configured shop item using the player's balance.

/balance

Displays the player's currency balance.

/kills

Displays the player's kill counter.

/rank

Lists configured ranks.

/rank buy <rank>

Purchases and assigns a configured rank.

/job

Displays the current job.

/job assassin

Selects the assassin job. During nighttime, the player deals additional damage.

/job lumberjack

Selects the lumberjack job. Breaking logs grants a short Haste effect.

/job miner

Selects the miner job. The job is available for selection but has no additional gameplay effect in the current implementation.

/job none

Removes the current job.

/afk

Toggles AFK earning. Players must remain still for the configured qualification period before receiving periodic payouts.

/mg spleef
/mg pvp
/mg buildbattle

Teleports the player to the configured minigame area. Spleef creates a snow-block floor when joined. Breaking blocks in the minigame areas contributes to the player's score.

/mg leave

Leaves the current minigame and returns the player to spawn.

Administrator commands:

```text
/serveradmin set spawn
/serveradmin set wild
/serveradmin event <event>
/serveradmin save
```

Available configured event names include `DOUBLE_DROPS`, `SPEED`, `WEAKNESS`, `HASTE`, `HAZARD`, and `GLASS_CANNON`.

The plugin automatically saves balances, kills, jobs, and ranks in its configuration data when players leave or when the server shuts down. Player-versus-player kills award currency and increase the killer's kill counter. Configured bosses appear periodically in the selected world, and generated ruins appear at random locations near players.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/ee98d4d6-4668-4102-b039-f7c58a3ca787?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=ee98d4d6-4668-4102-b039-f7c58a3ca787
