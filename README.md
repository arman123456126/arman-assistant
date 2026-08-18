# AI Arman — Personal Voice Assistant (Android)

A real, installable Android app: tap the mic, speak a command in Bangla or
English, and it performs the matching action using standard Android APIs.

## Setup

1. Open this folder in Android Studio (File → Open → select `ArmanAssistant`).
2. Let Gradle sync (it will download dependencies — needs internet once).
3. Connect your phone via USB with Developer Options + USB debugging on,
   or use an emulator.
4. Run ▶️. On first launch, grant the requested permissions (mic, call,
   SMS, contacts) — without them, features fall back to a safer manual mode
   (e.g. opens the dialer instead of calling directly).

## What it can actually do

| Say (Bangla or English)                     | Action |
|-----------------------------------------------|--------|
| "call Rahim" / "কল কর Rahim"                  | Looks up the contact, calls directly (if permission granted) |
| "message Rahim on my way" / "মেসেজ Rahim..."  | Sends SMS directly, or opens Messages pre-filled |
| "open Facebook" / "খোল Facebook"              | Launches the matching installed app |
| "set alarm at 7" / "অ্যালার্ম ৭টায়"           | Opens the alarm screen, pre-filled, for you to confirm |
| "remind me to buy milk" / "রিমাইন্ডার..."     | Opens a calendar reminder pre-filled |
| "flashlight on/off" / "ফ্ল্যাশ চালু/বন্ধ"      | Toggles the torch directly — no confirmation needed |
| "wifi" / "ওয়াইফাই"                            | Opens the Wi-Fi quick panel — you tap to switch |
| "bluetooth on/off" / "ব্লুটুথ চালু/বন্ধ"       | Requests to enable, or opens settings to disable |

## Why some actions need a manual tap

Since Android 10–13, Google removed the ability for third-party apps to
silently toggle Wi-Fi or turn Bluetooth off, for user-privacy reasons —
even with permission granted, the OS itself blocks it. This app opens the
right screen instead of pretending it can bypass that. This is a real OS
limitation, not a limitation of this app's code.

## What this app does NOT do

- It does not run in the background listening for a wake word ("Hey
  Arman") — you tap the mic each time. Always-on wake-word detection
  needs an offline engine (e.g. Picovoice Porcupine) and a foreground
  service; it's a solid next step if you want to extend this.
- It does not control other apps' internals or read/tap arbitrary screens
  — that would require an Accessibility Service, which is a much bigger
  trust and review surface and was left out intentionally to keep this
  app simple and safe to run on your own phone.
- It cannot bypass Android's own security dialogs (call/SMS permission
  prompts, Play Protect warnings on sideloaded apps, etc).

## Extending it

All command logic lives in one file:
`app/src/main/java/com/arman/assistant/CommandProcessor.kt`

Add a new `when` branch there for any new voice command you want to
support — the pattern (match keywords → parse the rest of the sentence →
call an Android API) is the same for every action.
