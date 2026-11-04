# Development

Use JDK 25 and the checked-in Gradle wrapper. `./gradlew build` runs compilation, tests and SpotBugs.
The mod is the `lumine-feed` subproject. Main source is under `lumine-feed/src/main/java/ru/lumine/feed`.

Tests cover command-root replacement, console permissions, absence of the optional D2Gate integration
and settings persistence/error handling. Use a disposable Fabric 26.3 server for runtime evaluation.

Before claiming a production-ready release, test two players: ordinary food with/without hunger,
effect food denied/allowed, creative and survival feeders, bowls/bottles, full inventory, death/spectator
guards, consent after restart, ordinary OP access and a locked/unlocked D2Gate operator if installed.
No production server or user preference file is needed to run the automated suite.
