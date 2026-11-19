# Better Chat Formatting

A client-side chat restyling mod for Minecraft Java Edition 26.3. It makes chat easier to read on any server, vanilla or modded, with nothing to install server-side.

It is purely visual. It never changes what you send, never touches signed messages, and never runs commands from chat text.

> **Status: in development.** Core logic, tests, config and themes are done. Chat hooks, rendering, the config screen and NeoForge support are still to come.

## Features

- **Themes:** Vanilla, Midnight, Pastel, Terminal and Minimal, plus your own JSON themes.
- **Colored player names:** a stable color per player, with optional per-player overrides.
- **Smart highlighting:** highlight your own name or custom keywords, each with its own color and sound.
- **Message-type styling:** separate looks for system, join/leave, death, advancement and whisper messages.
- **Markdown in chat:** `*italic*`, `**bold**`, `__underline__`, `~~strike~~` and `` `code` ``.
- **Safe clickable links:** http/https only, and always behind a confirmation prompt.
- **Chat extras:** timestamps, repeat collapsing ("x3"), chat search, and chat size and scroll options.
- **Emoji shortcodes:** a `:shortcode:` picker for outgoing messages (BMP Unicode only).
- **In-game config screen** with live preview and a keybind, plus optional Mod Menu integration.

### Roadmap

| Feature | Status |
| --- | --- |
| Themes and config | Done |
| Name colors, highlighting, markdown, message classifier | Core done, rendering pending |
| Link detection | Done, confirmation prompt in stage 3 |
| Timestamps, repeat collapsing, search, layout options | Helpers done, UI in stage 3 |
| Emoji shortcodes | Table and completion logic done |
| Config screen, keybind, Mod Menu | Stage 4 |
| NeoForge build | Stage 5 |

Screenshots will go in `docs/screenshots/`.

## Configuration

The config file is `config/betterchatformatting.json5`. It is created with commented defaults on first run and hot-reloaded: save the file and changes apply within a couple of seconds, no restart needed.

**Format.** Comments (`//`, `#`, `/* */`), unquoted keys and single quotes are all accepted.

**Error handling.**
- Each field is validated on its own. A bad value falls back to its default (or is clamped to its range), a warning naming the key is logged, and the rest of the file still applies.
- If the file isn't valid JSON at all:
  - **At startup:** defaults are used, your file is copied to `betterchatformatting.json5.bak`, and a fresh file is written.
  - **During hot reload:** your previous settings stay active and the file is left untouched until it becomes valid again.

### Example

You only need to include what you want to change. Everything else keeps its default.

```json5
{
  // Set to false to get vanilla chat back.
  "enabled": true,

  // vanilla, midnight, pastel, terminal, minimal, or a file name from config/themes/.
  "theme": "midnight",

  "features": {
    "markdown": true,        // formatting on incoming chat
    "links": true,           // clickable links, always with a confirmation prompt
    "timestamps": true,
    "collapseRepeats": true  // "Hello" x3
  },

  "timestamps": { "format": "HH:mm:ss" },

  "names": {
    "saturation": 0.55,
    "value": 0.95,
    // Fixed colors for specific players. Everyone else gets a stable hashed color.
    "overrides": { "Steve": "#FF8800" }
  },

  "highlights": {
    "ownName": true,
    "ownNameColor": "#FFD700",
    "ownNameSound": "minecraft:block.note_block.pling",  // "" for silence
    "soundVolume": 0.6,
    "keywords": [
      "diamond",
      { "keyword": "creeper", "color": "#55FF55", "sound": "", "caseSensitive": false, "wholeWord": true }
    ]
  },

  "messageTypes": {
    "join":    { "enabled": true, "color": "#55FF55", "icon": "+" },
    "death":   { "color": "#CC6666", "icon": "☠" },
    "whisper": { "enabled": false }
  },

  // Only applies while features.layout is on.
  "layout": {
    "chatWidth": 0,     // 0 = vanilla width; otherwise 80..800 px
    "chatHeight": 0,    // 0 = vanilla height; otherwise 40..600 px
    "scrollLines": 7    // lines per wheel notch, 1..10 (7 = vanilla)
  }
}
```

## Themes

The built-in themes are `vanilla`, `midnight`, `pastel`, `terminal` and `minimal`.

To make your own, drop a JSON file in `config/themes/`. The file name (minus `.json`) becomes the theme id, so `night-owl.json` is used as `"theme": "night-owl"`. A file with the same name as a built-in theme replaces it.

A theme only needs the fields it changes. Everything else is inherited from its `"base"` theme, which defaults to `vanilla`.

```json5
// config/themes/night-owl.json
{
  "name": "Night Owl",
  "base": "midnight",              // inherit everything else from Midnight

  "backgroundColor": "#0B1020",
  "backgroundOpacity": 0.6,        // 0 = none, 1 = solid
  "paddingX": 4,                   // px, 0..16
  "paddingY": 1,                   // px, 0..8
  "lineSpacing": 1,                // extra px between lines, 0..8
  "textShadow": false,
  "textColor": "#D6E4FF",          // "none" keeps the vanilla color
  "timestampColor": "#7A88C2",
  "font": null                     // e.g. "minecraft:default"; null = default font
}
```

**Limits:** up to 64 theme files, each at most 64 KB. A file that is too large, badly named or invalid is skipped with a warning.

## Safety

- Incoming chat is treated as untrusted.
- Links are limited to `http` and `https`. URLs with credentials or non-ASCII (look-alike) hosts are never linked, and opening a link always requires confirmation.
- The mod never creates click events for commands, and never modifies outgoing chat.
- Each message is parsed once on receipt and cached. Nothing is parsed per frame.

## Project layout

```
common/    Minecraft-free logic (parser, URL detection, highlighting, themes...). Plain Java + JUnit.
fabric/    Fabric entrypoint and rendering hooks. Compiles the common sources into the mod jar.
neoforge/  Planned (stage 5).
```

## Building

Requires **JDK 25** (set `JAVA_HOME`).

```
./gradlew :common:test      # run the unit tests
./gradlew build             # build everything; the Fabric jar lands in fabric/build/libs/
./gradlew :fabric:runClient # launch a dev client
```

## License

[MIT](LICENSE) © 2026 Reusqi