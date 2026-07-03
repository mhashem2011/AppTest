# FundingLedger

A single-screen native Android app that replaces a hand-maintained "Asset Balancing Ledger":
edit any number and every derived value (gold amounts, the plug, subtotals, funding gap,
transfer total) recalculates instantly.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- MVVM: `LedgerViewModel` exposes `StateFlow<LedgerState>` (raw inputs) and
  `StateFlow<LedgerSnapshot>` (fully derived values), recomputed on every edit.
- Persistence: the ledger is serialized to JSON in app-private storage. No Room, no network.
- Min SDK 26.

## Modules

- **`logic/`** - pure Kotlin, no Android dependency. `Row`, `LedgerState`, and
  `LedgerCalculator` (the recalculation chain). Has real unit tests
  (`LedgerCalculatorTest`) proving the plug/gold/transfer math against the seed data.
- **`app/`** - the Android app: JSON repository, `LedgerViewModel`, and the Compose UI.

## The "plug"

Every row has a mode:

- **FIXED** - amount typed directly.
- **GOLD** - amount = grams × pricePerGram (derived, edited via grams).
- **PLUG** - exactly one row; amount = target − sum(all other row amounts), so the
  grand total always equals target.

`pricePerGram = xauUsd / 31.1 * 3.75` (SAR/gram), with an optional manual override.

## Running it

Open the project root in Android Studio (Koala or newer) and let it sync - `gradlew` is
already configured. To run just the pure-logic unit tests from the command line:

```
./gradlew :logic:test
```

The `:app` module needs the Android SDK (via Android Studio or `sdkmanager`) to build.
