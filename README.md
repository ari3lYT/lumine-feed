# Lumine Feed

Fabric server mod for Minecraft 26.3. Players can feed one another; the recipient controls whether effect food is allowed with `/feedtoggle`. Operators can use `/feed` and `/heal`, including targeting a named player.

This 1.1.1 port preserves the live 1.1.0 command behavior. The live JAR was inspected because the older source omitted its administrative commands. The port also requires owner-level permission for console commands and saves player consent atomically, reporting I/O failures instead of claiming success.

Build with `./gradlew :lumine-feed:build`. Run the unit tests and SpotBugs with `./gradlew :lumine-feed:test :lumine-feed:spotbugsMain`.

Status: the 26.3 candidate passed these checks and a local Fabric startup/command smoke test. It is staged only; the production server still runs 1.21.11. Player-to-player feeding on the copied world remains to be tested before production use.
