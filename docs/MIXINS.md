# Mixins

Better Chat Formatting uses two mixin classes, both on the chat UI. Everything else goes through Fabric API events or
public Minecraft methods, so the mod stays small and fails less often across game updates.

## `ChatComponentMixin` (client)

| | |
| --- | --- |
| Class | `com.betterchatformatting.fabric.mixin.ChatComponentMixin` |
| Target | `net.minecraft.client.gui.components.ChatComponent` |
| Injection | `@Inject` at `HEAD` of the private `addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)`, cancellable |
| Shadows | `allMessages` and `trimmedMessages` (display lists, for removing a collapsed repeat), and `addMessage` (to re-dispatch) |
| Unique | `betterchatformatting$redispatching` (guard flag) and `betterchatformatting$removeLastMessage()` |
| Handler | `ChatPipeline.process(...)`: returns the original line unless a feature changes it. Changed lines are re-added through the same method with a guard |
| Config | `betterchatformatting.mixins.json`, listed under `"mixins"` in `fabric.mod.json` |

### Why a mixin is needed

Fabric has no event that lets a mod change the text of an incoming chat line before it is stored.
`ChatComponent.addMessage` is private, and the public entry points (`addPlayerMessage`,
`addClientSystemMessage`, `addServerSystemMessage`) store the `Component` as given. Changing the
message content therefore requires a hook inside the chat component.

### Why this injection point

- Every line (player, server system, client system) goes through `addMessage` exactly once, so one
  head hook covers all of them. Restyling touches player and system lines alike (markdown, links, highlights), and type styling only
  touches non-player lines (`ChatPipeline`).
- The hook cancels only when the line changes. Unchanged lines run through vanilla untouched.
- Repeat collapsing has to remove the previous line, and only this class can reach the private
  display lists. The `@Shadow` fields are needed for that and nothing else.
- The guard flag stops the re-dispatched call from being decorated a second time.
- Signatures, the trust tag and the `MessageSignature` are passed through unchanged.
- Checked against the 26.3 sources: `ChatComponent.addMessage` (private, four parameters),
  `addPlayerMessage`, `addServerSystemMessage` and `addClientSystemMessage` all call it.

### What the mixin does **not** touch

- Outgoing messages. Nothing in the send path (`ChatScreen.handleChatInput`,
  `ClientPacketListener.sendChat`) is changed.
- Signature checks, message reporting, and the `MessageSignature` stored with the line.
- Chat rendering code, except the one line-background color described under "Theme background" below.
  Text, positions, scrolling and `GuiMessage.splitLines` are drawn by vanilla.

### Failure behavior

`ChatRestyler.restyle` catches every `RuntimeException`, logs a warning and returns the original
component. A bug in the restyler therefore shows the message the way vanilla would.

### Compatibility notes

- Verified against Minecraft **26.3** (unobfuscated names, Loom with no remapping). If a future
  version renames `addPlayerMessage` or changes the argument list, the mixin fails at startup with
  a clear "target not found" error rather than silently doing nothing (`required: true`).
- Other mods that also change incoming chat (for example, chat-filter mods) may conflict, since
  this mixin cancels and re-adds changed lines. Unchanged lines are not affected. This has **not
  been tested yet**.
- Known limitation: the repeat collapse removes the newest line on screen. If a message was hidden
  by another mod's visibility filter, the removal can hit the wrong line.

### Layout overrides (same class)

| Injection | Why |
| --- | --- |
| `@Inject` at `HEAD` of `getWidth()I` and `getHeight()I` (the private no-argument overloads) | Replaces the vanilla chat width and height with `layout.chatWidth` and `layout.chatHeight` when they are non-zero. The static `getWidth(double)` overload is left alone. |

The width and height are read on every layout call, so a change only needs `ChatComponent.rescaleChat()`,
which the config listener calls.

### Theme background (same class)

| Injection | Why |
| --- | --- |
| `@ModifyArg` on the `ChatGraphicsAccess.fill(IIIII)V` call inside `lambda$extractRenderState$1`, argument index 4 | Vanilla fills each chat line with black at the user's background opacity. This replaces that color with the theme's background color and opacity, keeping vanilla's fade alpha (`ChatTheme.lineBackground`). With the vanilla theme or "Theme colors" off, the input is returned unchanged. |

Checked against the 26.3 bytecode: that lambda contains the only `fill` call that draws a line background.
The lambda name is compiler-generated, so this hook must be rechecked whenever the Minecraft version changes.
If the target is missing, Mixin fails at startup (`required: true`).

## `ChatScreenMixin` (client)

| | |
| --- | --- |
| Class | `com.betterchatformatting.fabric.mixin.ChatScreenMixin` |
| Target | `net.minecraft.client.gui.screens.ChatScreen` |
| Shadows | `input` (the chat `EditBox`), read and written to show the search text and the completed emoji |
| Unique | `betterchatformatting$search`, `betterchatformatting$stashedDraft`, `betterchatformatting$writing` |

| Injection | Feature | Why |
| --- | --- | --- |
| `@Inject` at `HEAD` of `keyPressed(KeyEvent)` | Search toggle (Ctrl+F) and Enter suppression | `keyPressed` is declared on `ChatScreen`. Enter is cancelled only in search mode, so ordinary sending is unchanged. |
| `@Inject` at `HEAD` of `onEdited(String)` | Live search filter, emoji completion | `onEdited` is the input box's responder and is private, so it can be hooked. |
| `@Inject` at `HEAD` of `removed()` | Restore the draft | Runs before vanilla saves the draft, so the search text is never saved as a draft. |
| `@ModifyArg` on the `scrollChat(I)V` call in `mouseScrolled` | Lines per wheel notch | Changes only that call site, which is the wheel path. Other `scrollChat` calls, such as new messages while scrolled, are untouched. |

### Why search is not a separate widget

`ChatScreen` declares no `charTyped`, and `addRenderableWidget` is protected and generic, so a
separate search box could not receive typed characters or be registered from a mixin. Instead, the
existing input box switches into search mode, and its hint says so.

### Search filter

Filtering uses the public `ChatComponent.setVisibleMessageFilter`. It is applied to new messages as they
arrive, and clearing it restores all lines. The filter replaces any other mod's filter; this has not been
tested with other mods.

## Non-mixin hooks

| Purpose | Mechanism |
| --- | --- |
| Config hot reload | `ClientTickEvents.END_CLIENT_TICK` (Fabric API), checks file signatures every 40 ticks |
| Config loading | `ClientModInitializer.onInitializeClient` |
| Mod Menu integration | `modmenu` entrypoint (`ModMenuIntegration`), optional, via `"suggests"`. Compile-only dependency. |
| Settings keybind (default K) | `ChatKeys`: `KeyMappingHelper.registerKeyMapping` and `ClientTickEvents.END_CLIENT_TICK`, opens `ChatConfigScreen` |
| Settings screen | `ChatConfigScreen`: vanilla widgets only, working copy saved through `ConfigManager.save` |
| Accent bar (2px, left edge of each line) | Not a new injection. Changed lines get a `GuiMessageTag` with a category color, which vanilla already draws as a 2px bar (`handleTag`). Lines that already have a tag (for example "not secure") keep it. |

## Planned mixins

None so far. If a later stage needs one (for example, for the chat-width option), it must be
added to this file first, with the same table and justification.
