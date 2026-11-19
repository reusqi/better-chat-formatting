# Better Chat Formatting

A **client-side** chat restyling mod for Minecraft Java Edition 26.3. It works on any server, vanilla or
modded, needs nothing server-side, and is purely visual: it never changes what you send, never touches
signed messages, and never runs commands from chat text.

> **Status: in development.** Stages 1 (core logic and tests) and 2 (config and themes) are done. Chat
> hooks, rendering, the config screen and NeoForge support are still to come.

## Planned features

| Feature | Status |
| --- | --- |
| Themes (Vanilla, Midnight, Pastel, Terminal, Minimal) + custom JSON themes | config + themes done |
| Colored player names (hashed color, per-player overrides) | core done |
| Smart highlighting (own name / keywords, per-keyword color + sound) | core done |
| Message-type styling (system, join/leave, death, advancement, whisper) | classifier done |
| Client-side markdown: `*italic*` `**bold**` `__underline__` `~~strike~~` `` `code` `` | core done |
| Clickable links with a confirmation prompt (http/https only, never auto-open) | detection done, prompt in stage 3 |
| Timestamps, repeat collapsing ("x3"), chat search, chat size/scroll options | helpers done, UI in stage 3 |
| `:shortcode:` emoji picker for outgoing messages (BMP Unicode only) | table + completion logic done |
| In-game config screen with live preview + keybind | stage 4 |
| Mod Menu integration (optional) | stage 4 |
| NeoForge build | stage 5 |

Screenshots: _placeholders — to be added in `docs/screenshots/`._

## Configuration

The config lives at `config/betterchatformatting.json5`. It is created with commented defaults on first
run and **hot-reloaded**: save the file and the changes apply within a couple of seconds, without
restarting the game.

- Comments (`//`, `#`, `/* */`), unquoted keys and single quotes are accepted.
- Each field is checked on its own. A bad value falls back to its default (or is clamped to its
  range) and a warning naming the key is written to the log; the rest of the file still applies.
- A file that is not valid JSON at all is treated like this: at startup the defaults are used, your
  file is copied to `betterchatformatting.json5.bak`, and a fresh file is written. During a hot reload
  the previous settings stay active and your file is left alone until it is valid again.

Example (only the parts you want to change; everything else keeps its default):

```json5
{
  // Turn the whole mod off and get vanilla chat back.
  "enabled": true,

  // vanilla, midnight, pastel, terminal, minimal, or the name of a file in config/themes/.
  "theme": "midnight",

  "features": {
    "markdown": true,        // *italic* **bold** __underline__ ~~strike~~ `code` on incoming chat
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
    "join":  { "enabled": true, "color": "#55FF55", "icon": "+" },
    "death": { "color": "#CC6666", "icon": "☠" },
    "whisper": { "enabled": false }
  },

  "layout": {
    "chatWidth": 0,     // 0 keeps the vanilla width; otherwise 80..800 pixels
    "chatHeight": 0,    // 0 keeps the vanilla height; otherwise 40..600 pixels
    "scrollLines": 7    // lines per mouse-wheel notch, 1..10 (7 = vanilla)
  }
}
```

The `layout` options only apply while `features.layout` is on.

### Themes

Built-in themes are `vanilla`, `midnight`, `pastel`, `terminal` and `minimal`. Your own themes are
JSON files in `config/themes/`. The file name (without `.json`) becomes the
theme id. A theme only needs the fields it changes; the rest come from its `"base"` theme, which
defaults to `vanilla`. A file with the same name as a built-in replaces it.

```json5
// config/betterchatformatting/themes/night-owl.json
{
  "name": "Night Owl",
  "base": "midnight",              // inherit everything else from Midnight

  "backgroundColor": "#0B1020",
  "backgroundOpacity": 0.6,        // 0 = no background, 1 = solid
  "paddingX": 4,                   // pixels, 0..16
  "paddingY": 1,                   // pixels, 0..8
  "lineSpacing": 1,                // extra pixels between lines, 0..8
  "textShadow": false,
  "textColor": "#D6E4FF",          // "none" keeps the vanilla color
  "timestampColor": "#7A88C2",
  "font": null                     // e.g. "minecraft:default"; null = default font
}
```

Then set `"theme": "night-owl"` in the main config. Themes are limited to 64 files of at most 64 KB
each; a file that is too large, has a bad name or is invalid is skipped with a warning.

## Project layout

```
common/    Minecraft-free logic (parser, URL detection, highlighting, themes...). Plain Java + JUnit.
fabric/    Fabric entrypoint and rendering hooks. Compiles the common sources into the mod jar.
neoforge/  (stage 5)
```

## Building

Requires **JDK 25** (set `JAVA_HOME`).

```
./gradlew :common:test     # run the unit tests
./gradlew build            # build everything; the Fabric jar is in fabric/build/libs/
./gradlew :fabric:runClient
```

## Safety design

- Incoming chat is treated as untrusted. Links are limited to `http`/`https`, URLs with credentials
  or non-ASCII (look-alike) hosts are never linked, and opening always requires confirmation.
- Click events are never created for commands. Outgoing chat is never modified.
- Parsing happens once per received message and is cached; nothing is parsed per frame.

## License

[MIT](LICENSE) © 2026 Reusqi
