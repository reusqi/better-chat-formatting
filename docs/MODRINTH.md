# Modrinth listing (draft)

Copy the fields below into the Modrinth project and version forms. Replace anything in `[brackets]`.

## Project

- **Name:** Better Chat Formatting
- **Summary:** Search your chat history live (Ctrl+F), plus themes, colored names and ranks, highlights, markdown and clickable links. Works on any server.
- **Project type:** Mod
- **Icon:** `fabric/src/main/resources/assets/betterchatformatting/icon.png`
- **Environment:** Client-side only (server: unsupported)
- **License:** MIT
- **Links:** source code, issue tracker, wiki (the `docs/` folder)

## Categories

Pick the closest matches from Modrinth's list, for example **Fabric**, **Utility**, and the chat-related tag if it is offered. Do not tick server-side environments.

## Description

Better Chat Formatting makes chat easier to read without changing what you send or what the server sees.

## Chat search

Find anything in the chat history in a second. Press **Ctrl+F** in the chat box to switch into search mode, then type. The chat filters live to only the lines that match, and clearing the box shows everything again. Enter does not send a message while searching, so you can't post by accident. Press Ctrl+F again to leave search mode.

- **Live filtering:** matches update as you type.
- **Safe:** search never sends anything and never saves the search text as your draft.

## Features

- **Themes:** Vanilla, Midnight, Pastel, Terminal, Minimal, plus your own theme files.
- **Colored names and ranks:** Keeps the server's own colors when it sends them. Otherwise uses the theme's accent.
- **Highlights:** Keywords and your own name, with an optional sound.
- **Message types:** Join, leave, death, advancement and system lines get their own color and icon.
- **Markdown and links:** `*italic*`, `**bold**`, `` `code` ``, and clickable links. Opening a link always asks first.
- **Quality of life:** Timestamps, collapsed repeats ("x3"), chat search, emoji shortcodes, chat width and height.
- **Settings screen:** Press **K** (rebindable under Controls) or open it from Mod Menu. Live preview included.

Purely visual: outgoing messages, signatures and reporting are not touched. Features can be turned off one by one, and the config file reloads while you play.

Requires **Fabric API**. Mod Menu is optional.

## Version

- **Version number:** 1.0.0
- **Release channel:** Release
- **Game versions:** 26.3
- **Loaders:** Fabric
- **Files:** `fabric/build/libs/fabric-1.0.0.jar` (**not** the `-sources` jar)
- **Dependencies:**
  - Fabric API: required
  - Mod Menu: optional

## Changelog (1.0.0)

- First release.
- Themes, colored names and ranks, highlights, message types, markdown, links.
- Timestamps, repeat collapsing, chat search, emoji shortcodes, layout options.
- In-game settings screen with live preview, keybind K, and Mod Menu integration.
