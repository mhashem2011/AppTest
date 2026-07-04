# FundingLedger

A native Android app with two pages behind a bottom nav bar:

1. **Ledger** — automates a hand-maintained **Asset Balancing Ledger** for a
   house purchase: edit any number and every derived value recalculates
   instantly.
2. **Gold** — the GoldMonitor dashboard merged in: live XAU/USD from
   api.gold-api.com, SAR/g conversion, 1h/3h/yesterday history chips,
   200g portfolio P/L, and hourly background alerts on ±0.5% moves.

Every fresh live rate is pushed into the Ledger's `xauUsd`, so SAR/g, GOLD
rows, and the plug recompute automatically from real market data.

## Stack

- Kotlin + Jetpack Compose (Material 3), min SDK 26, target SDK 35
- MVVM: `LedgerViewModel` exposes state via `StateFlow`; all derived values
  recompute automatically from a single `LedgerCalculator.derive()` pass
- Persistence: the ledger is serialized to pretty-printed JSON
  (`ledger.json`) in app-private storage on every edit and reloaded on start

## Core concept — the "plug"

Every row has a mode:

| Mode | Behavior |
|---|---|
| `FIXED` | SAR amount typed directly |
| `GOLD` | stores grams; amount = grams × pricePerGram (read-only, derived) |
| `PLUG` | exactly one row; amount = target − sum(all other rows), never typed |

Gold price: `pricePerGram = xauUsd / 31.1 × 3.75` (SAR/g), with an optional
manual override (editing xauUsd clears the override).

Recalculation chain (re-runs on any edit): price/gram → gold amounts →
plug amount → per-row % of target → green/red subtotals → funding gap →
KSA→SY transfer sum. See
`app/src/main/java/com/fundingledger/domain/LedgerCalculator.kt`.

## Project layout

```
app/src/main/java/com/fundingledger/
├── MainActivity.kt                 # entry point + JSON share intent
├── model/LedgerModels.kt           # Row, Ledger, seed data (persisted state only)
├── domain/LedgerCalculator.kt      # the whole recalculation chain (pure, testable)
├── data/LedgerRepository.kt        # JSON file persistence
└── ui/
    ├── LedgerViewModel.kt          # StateFlow state + edit operations
    ├── LedgerScreen.kt             # Compose table UI, inline editing, dialogs
    └── theme/Theme.kt              # ledger band colors
app/src/test/java/com/fundingledger/
└── LedgerCalculatorTest.kt         # plug / gold / transfer math on seed data
```

## Building

Open in Android Studio (or run `./gradlew assembleDebug` with the Android
SDK installed) and deploy the `app` module. Unit tests: `./gradlew test`.

## Editing UX

- Tap a FIXED amount → inline numeric field, commits on Done/focus loss
- Tap a GOLD row's grams → edit grams, amount and plug recompute
- Target, XAU/USD and SAR/g are editable at the top
- Long-press a row → rename, category/mode, designate as plug, transfer
  flag, move, delete; **+** in the top bar adds a row; share icon exports JSON
- If non-plug rows exceed the target the plug goes negative (shown in red)
  and the footer flags "Over-funded by X"
