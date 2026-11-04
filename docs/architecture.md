**English** · [Русский](architecture.ru.md)

# Architecture

```mermaid
flowchart LR
  Use[Fabric UseEntityCallback] --> Validate[Player, food and hunger checks]
  Validate --> Consent[Recipient effect-food preference]
  Consent --> Consume[Vanilla finishUsingItem]
  Consume --> Inventory[Consume feeder item / return container]
  Commands[Brigadier commands] --> Admin[OP + optional D2Gate check]
  Consent --> Disk[Atomic properties files]
```

`LumineFeed` owns interaction validation, effect detection, messages and recipient preferences.
`AdminFoodCommands` owns the administrative commands. It replaces same-named Brigadier command
roots deliberately, including startup re-registration; review conflicts with other `/feed` or `/heal` mods.

Effect food is opt-in per recipient UUID. Failure to read consent refuses effect food. Persistence writes
a temporary file and moves it into place, with a non-atomic-move fallback for unsupported filesystems.
Vanilla `finishUsingItem` preserves food-specific consumption behavior and item remainders.
