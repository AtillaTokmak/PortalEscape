# 🌀 PortalEscape

<p align="center">
  <img src="nether_portal_logo_512.png" alt="PortalEscape Logo" width="180" height="180" />
</p>

<p align="center">
  <strong>Minecraft Java 26.3 • Fabric Client-Side Mod • Java 25</strong><br>
  <em>Designed for DonutSMP and hardcore multiplayer environments.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.3-green.svg" alt="Minecraft 26.3" />
  <img src="https://img.shields.io/badge/Loader-Fabric%200.20.1-blue.svg" alt="Fabric Loader" />
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25" />
  <img src="https://img.shields.io/badge/Platform-Client%20Side-purple.svg" alt="Client Side" />
  <img src="https://img.shields.io/badge/Tests-36%20Passed-brightgreen.svg" alt="Unit Tests" />
</p>

---

## 🎯 About PortalEscape

On competitive survival servers like **DonutSMP**, players often encounter malicious **Nether portal traps** (obsidian box cages, cobweb locks, lava pools, or endless loading nausea) where standard keyboard controls and chat are frozen or suppressed by vanilla Minecraft mechanics.

**PortalEscape** is an ultra-lightweight, zero-cheat, client-side Fabric mod designed specifically for **DonutSMP**. It automatically tracks post-portal movement, detects entrapment, provides emergency chat access, and dispatches the `/rtp` (random teleport) recovery command cleanly without risking server anti-cheat kicks or chat bans.

---

## ⚡ Key Features

### 1. 🍩 Designed for DonutSMP: Automated `/rtp` Recovery
* Automatically monitors player coordinates immediately after dimension transitions.
* If near-zero displacement ($\le 0.05$m) persists for **5.0 seconds** (configurable), PortalEscape marks the condition as trapped and dispatches `/rtp`.
* **Zero Packet Flood:** Protected by an atomic monotonic cooldown (default 10s, configurable) that guarantees at most one single recovery command is sent per incident.
* Reset immediately upon moving, dying, disconnecting, or changing dimensions.

### 2. 💬 Dual Portal Chat Bypass
Vanilla Minecraft blocks the default `T` chat key while inside nether portal blocks and during portal distortion effects. PortalEscape registers two independent portal-safe hotkeys:
* **Emergency Recovery Key (`K` by default):** Opens chat pre-populated with `/rtp` (can be toggled with `/pe keycommand`).
* **Clean Portal Chat (`H` by default):** Opens a completely clean, empty chat screen inside portals, allowing you to freely type to teammates without restrictions.
*(Both keys are fully remappable under `Options -> Controls -> Key Binds -> Portal Escape`).*

### 3. 📍 Double-Tap Protected Coordinate Sharing (`P` by default)
Accidentally leaking base coordinates in public chat on DonutSMP can lead to immediate raids. PortalEscape introduces a secure double-press confirmation system:
* **First Press:** Emits a private, client-side yellow warning message:  
  `[PortalEscape] Paylaşmak istediğinize emin misiniz? Onaylamak için tekrar basın.`  
  *(No packet is sent to the server or public chat).*
* **Second Press (within 2 seconds):** Broadcasts your current rounded block coordinates and dimension to chat:  
  `[PortalEscape] Kordinat: X: 1420, Y: 72, Z: -3580 (minecraft:the_nether)`
* **Anti-Chatter & Anti-Spam:** 250ms mechanical debounce protection + 3.0s anti-spam rate limiter.

### 4. 🛠️ In-Game Client Commands (`/pe` or `/portalescape`)
No need to edit JSON files or restart Minecraft; configure everything live through client chat:
* `/pe status` — Displays all current configuration parameters.
* `/pe timeout <seconds>` — Sets portal stuck timeout duration (1.0 to 60.0s).
* `/pe command <cmd>` — Customizes recovery command (defaults to `/rtp`).
* `/pe autocommand <true|false>` — Toggles automated command dispatch on/off.
* `/pe keycommand <true|false>` — Toggles whether the emergency key (K) pre-fills the command.

---

## ⌨️ Default Keybindings

| Key | Action | Description |
|:---:|:---|:---|
| **`K`** | Emergency Portal Chat | Opens chat with `/rtp` pre-filled for immediate escape |
| **`H`** | Open Normal Chat in Portal | Opens a clean, blank chat box inside portal blocks |
| **`P`** | Share Coordinates | Double-press confirmation hotkey to broadcast coordinates |

---

## 🔧 In-Game Command Reference

```text
/pe status                 - View active configuration & help
/pe timeout <1.0-60.0>     - Set stuck detection timeout (default: 5.0)
/pe command <string>       - Set custom recovery command (default: /rtp)
/pe autocommand <on|off>   - Enable/disable automatic command dispatch
/pe keycommand <on|off>    - Enable/disable command pre-filling on emergency key
```

---

## 🛡️ Security & Zero-Trust Architecture

PortalEscape was developed and verified under an aggressive Zero-Trust pipeline:
* **No Server Bypass / No Cheats:** Operates strictly within legal vanilla client limits. Never spoofs packets or circumvents server permissions.
* **Command Injection Guard:** All inputs are stripped of CR/LF newlines (`\r`, `\n`), null bytes (`\0`), and Minecraft format codes (`§`).
* **Recursion Guard:** Prevents recursive loops (e.g. setting `/pe` as recovery command).
* **Safe Diagnostics:** Player world coordinates and server IP addresses are automatically redacted in client log files.
* **Corrupted Config Auto-Recovery:** Automatic fallback to immutable defaults with timestamped `.corrupted` backup if JSON syntax is damaged.

---

## 📦 Building from Source

### Prerequisites
* **Java 25** (JDK 25)
* Git

```bash
# Clone the repository
git clone https://github.com/AtillaTokmak/PortalEscape.git
cd PortalEscape

# Build the mod JAR with Gradle
./gradlew build
```

The compiled mod JAR will be located at:
```text
build/libs/portalescape-1.0.0.jar
```

---

## 📥 Installation

1. Install **Minecraft 26.3** with **Fabric Loader** (version `0.20.1` or higher).
2. Install **Fabric API** (`0.165.0+26.3` or compatible).
3. Place `portalescape-1.0.0.jar` into your `.minecraft/mods` directory.
4. Launch the game and survive DonutSMP!

---

## 📄 License
Licensed under the [MIT License](LICENSE).
