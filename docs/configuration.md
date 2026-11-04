**English** · [Русский](configuration.ru.md)

# Commands and files

| Command | Permission / behavior |
| --- | --- |
| `/feedtoggle` | Recipient toggles effect-food consent |
| `/feedtoggle status` | Show current consent |
| `/feedtoggle effects on` | Allow effect food |
| `/feedtoggle effects off` | Refuse effect food |
| `/feedtoggle effects toggle` | Toggle consent |
| `/feed [player]` | OP player, or owner-level console with an explicit target |
| `/heal [player]` | Same permission; restore health/food and clear effects/fire |

Files are created under `config/lumine-feed/`: `player-prefs.properties` contains recipient UUID
preferences, and `messages.properties` contains editable messages. These files are runtime data,
not examples to commit. Default messages are Russian. Stop the server before editing files.

No custom authentication mod is required. If D2Gate is present, an operator must also pass its
unlock check; an unavailable or incompatible installed integration fails closed.
