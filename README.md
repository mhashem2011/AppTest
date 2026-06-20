# HiFi Radio — high-quality radio & podcasts for Android Auto

An Android Auto **media app** that streams **high-bitrate / lossless internet
radio** and **podcasts**, with a quality-tuned audio pipeline and EQ. Built for a
BMW X4 over **wireless Android Auto** (Wi-Fi/PCM), which beats the Bluetooth codec
ceiling.

## Why this app (and not "beat Deezer")

Streaming services already hit the quality ceiling over Android Auto, so a custom
app can't out-fidelity them. Radio + podcasts are different: the content is **open
and license-clean**, and some internet-radio stations broadcast in **FLAC or
high-bitrate** — so "hi-fi" is a real, deliverable feature here, not marketing.

## Content sources (both free, no API key)

- **Radio:** [Radio Browser API](https://www.radio-browser.info) — a community
  station database that exposes each stream's **codec and bitrate**, so the app
  can filter to genuinely hi-fi stations (FLAC / ≥256 kbps).
- **Podcasts:** the **iTunes Search API** for discovery (returns each show's public
  RSS feed), then the **RSS feed** itself for episodes — podcasts are open by
  design, each episode carrying a directly playable audio URL.

## Audio quality, where the app actually controls it

| Lever | Where |
|---|---|
| Prefer FLAC / high-bitrate streams | `content/RadioBrowserProvider.kt` + Hi-Fi filter |
| EQ **baked into the PCM** (survives AA projection) | `audio/EqAudioProcessor.kt` |
| Unity digital gain — no bit loss from attenuation | `audio/AudioEngine.kt` (`volume = 1.0`) |
| Generous buffering to absorb Wi-Fi jitter | `audio/AudioEngine.kt` (`DefaultLoadControl`) |
| Runs as a real AA media app | `playback/PlaybackService.kt` + manifest |

Over **wireless Android Auto** the link carries uncompressed **PCM**, so a lossless
FLAC station reaches the car intact — better than any Bluetooth codec.

## Project layout

```
app/src/main/java/com/hifibt/player/
├─ HiFiApp.kt                 # service locator (engine, providers, BT monitor)
├─ MainActivity.kt            # Compose entry + BT permission request
├─ audio/
│  ├─ AudioEngine.kt          # ExoPlayer (Media3): unity gain, big buffers
│  └─ EqAudioProcessor.kt     # in-pipeline biquad EQ, baked into projected PCM
├─ bluetooth/
│  └─ BluetoothAudioMonitor.kt# reads connected A2DP device + codec, honestly
├─ content/
│  ├─ ContentModels.kt        # Station / PodcastShow / Episode
│  ├─ RadioBrowserProvider.kt # Radio Browser API, hi-fi filtering
│  └─ PodcastProvider.kt      # iTunes search + RSS episode parsing
├─ playback/
│  └─ PlaybackService.kt      # MediaLibraryService → Android Auto browse tree
└─ ui/                        # Compose screens + ViewModel (Radio / Podcasts tabs)
```

## Build & run

Standard Android Studio project (Kotlin, Compose, Media3).

1. Open the folder in **Android Studio** (Koala+); it syncs Gradle and generates
   the wrapper jar. CLI users: run `gradle wrapper` once.
2. Run on a **physical device** (Bluetooth/codec + AA projection don't exist on the
   emulator), connect via **wireless Android Auto**, then browse Radio / Podcasts.

## Status

Working: hi-fi radio search/browse + playback, podcast search → episodes →
playback, the quality-tuned pipeline (in-pipeline EQ, unity gain, big buffers), BT
codec readout, and an Android Auto browse tree exposing the top hi-fi stations.

Follow-ups: podcast browsing on the AA screen (currently phone-side; AA needs a
search/voice flow), favorites/recents, and richer station genres. Verify the AA
tree on a physical head unit or Google's Desktop Head Unit.
