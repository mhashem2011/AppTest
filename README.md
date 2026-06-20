# HiFi BT — max-quality Bluetooth music for a BMW X4

An Android music app focused on getting the **best audio quality the car's
Bluetooth link can physically accept**, using Deezer as the catalog/source.

## The one thing to understand first

Bluetooth audio quality to a car is **capped by the car**, not the app. Audio sent
over Bluetooth (A2DP) must be encoded with a codec **both** sides support. A BMW
iDrive head unit supports **SBC** and **AAC** — and that's it (no aptX / LDAC /
lossless). So the realistic quality ceiling over Bluetooth is **AAC ~256 kbps**.
No app can beat the car's decoder.

Because of that, this app does the only things that actually help:

1. **Pins the highest codec the car accepts (AAC)** and shows you the live codec
   so you can confirm it (and fix it in Developer Options if SBC sneaks in).
2. **Bit-perfect decode → minimal pipeline → encoder**, so nothing is degraded
   before the Bluetooth stage. FLAC is decoded natively by ExoPlayer.
3. **Parametric EQ** applied in the OS pipeline *before* encoding, tuned to taste
   for the car. Turn it off for a fully transparent path.

> Want quality *above* Bluetooth? That needs a **wired/USB digital connection** —
> that's the only way to actually beat Android Auto in this car. Bluetooth is the
> weak link by design.

## Source: Deezer

Deezer **retired its public third-party streaming SDK**, so:

- The open API (`api.deezer.com`) powers **search and metadata** today with no key.
- Full-length **lossless FLAC** playback through a non-Deezer app needs
  **partner/commercial streaming access** from Deezer. Until that's wired in, the
  app plays Deezer's **30-second preview** so the whole pipeline is exercisable.
- That single integration seam is `DeezerProvider.resolveFullStream()`. Return a
  real FLAC URL there and everything downstream delivers it bit-perfect — no other
  code changes.

## Project layout

```
app/src/main/java/com/hifibt/player/
├─ HiFiApp.kt                 # service locator (engine, provider, BT monitor)
├─ MainActivity.kt            # Compose entry + BT permission request
├─ audio/
│  ├─ AudioEngine.kt          # ExoPlayer (Media3), FLAC, quality audio attrs
│  └─ Equalizer10Band.kt      # system EQ on the player's audio session
├─ bluetooth/
│  └─ BluetoothAudioMonitor.kt# reads connected A2DP device + codec, honestly
├─ streaming/
│  ├─ StreamingProvider.kt    # provider-independent interface
│  └─ DeezerProvider.kt       # Deezer search/metadata + stream-resolution seam
├─ playback/
│  └─ PlaybackService.kt      # Media3 session → AVRCP controls on the head unit
└─ ui/                        # Compose screens + ViewModel
```

## Build & run

This is a standard Android Studio project (Kotlin, Compose, Media3).

1. Open the folder in **Android Studio** (Koala or newer). It will sync Gradle and
   generate the Gradle wrapper jar on first run. (CLI users: run `gradle wrapper`
   once to create `gradlew`.)
2. Optional Deezer app id: add to `local.properties`:
   ```
   DEEZER_APP_ID=your_id_here
   ```
3. Run on a **physical device** (Bluetooth/A2DP and the codec APIs don't exist on
   the emulator), pair with the car, then search and play.

## In-car quality checklist

- Settings → **Developer options → Bluetooth Audio Codec → AAC** while connected
  to the car. The app's status card shows the negotiated codec.
- Set **Bluetooth Audio Sample Rate** as high as the car offers.
- Keep the EQ **off** for a transparent A/B, then dial it in by ear.
- Phone media volume near max, adjust final level on the car — keeps the digital
  signal clean before encoding.

## Status

Scaffold: search, playback pipeline, EQ, BT codec readout, and Media3 session are
in place. Full-length lossless awaits entitled Deezer streaming access at the one
documented seam.
