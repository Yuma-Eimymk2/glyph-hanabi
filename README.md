# Glyph Hanabi 🎆

> *Japanese fireworks (hanabi), blooming on the back of your Nothing Phone (4a) Pro.*

Sister app of [Glyph Life](https://github.com/Yuma-Eimymk2/glyph-life).

<p align="center">
  <img src="docs/demo.gif" width="360" alt="Glyph Hanabi demo — a full one-minute show on the 13×13 matrix">
</p>

<p align="center"><i>
The full show, rendered frame-by-frame by the same engine that drives the real LEDs.
</i></p>

## Concept

> *"A one-minute hanabi festival in the palm of your hand."*

Press the power button and a fireworks show begins on the Glyph Matrix:
shells rise one by one, bloom, and fall — building up to a rapid finale barrage,
a beat of silence, and a Niagara curtain to close the night.
The structure of a real Japanese fireworks festival, on a 13×13 board.

## Features

- **9 traditional shell types** — kiku (chrysanthemum), botan (peony), shin-iri (double core),
  senrin (thousand blooms), yanagi (willow), yashi (palm), hachi (bees),
  nishiki-kamuro (golden crown), and a Niagara finale.
- **A real show structure** — ~60 seconds: build-up, finale barrage, a pause, then Niagara.
- **Physics-based** — launch, burst, gravity, drag and brightness envelopes,
  tuned shell by shell against a browser simulator and on the actual device.
- **You control the timing** — the show starts the moment the screen goes dark:
  just press the power button. Waking the screen ends it.
- **Rests while charging** — the matrix stays dark on the charger.
  Hanabi belongs to the night, not the charging cable.

## Usage

1. Install from [Releases](https://github.com/Yuma-Eimymk2/glyph-hanabi/releases)
   (or Nothing Playground, once approved).
2. **Launch the Glyph Hanabi app once.** Android keeps freshly installed apps frozen
   until first launch, and the Toy cannot start while frozen.
3. Go to **Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy**
   and select **Glyph Hanabi**.
4. Press the power button — the show begins the moment the screen goes dark.

## A Note on Behavior

- Each show runs for about a minute and ends on its own. Every screen-off starts
  one show (this includes the automatic screen timeout); waking the screen stops
  it immediately.
- While charging, the matrix intentionally stays dark.
- Like real hanabi, it looks best in a dim room. The Glyph LEDs are subtle in daylight.

## Requirements

- Nothing Phone (4a) Pro

## Building from source

This repository contains the full source code, but the Glyph Matrix SDK binary is not
included due to Nothing's redistribution terms. To build Glyph Hanabi yourself:

1. Clone this repository.
2. Download the Glyph Matrix SDK from
   [Nothing GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
3. Place the `.aar` in `app/libs/` (any filename works).
4. Open the project in Android Studio and build.

Note: a Phone (4a) Pro is required to actually see the output.
The firework engine itself is pure Kotlin — `./gradlew test` runs the whole
test suite without a device. The demo GIF above is rendered by the engine too
(see `GifExportTest.kt` — set `HANABI_GIF=<output path>` to regenerate it).

## Security

The release APK has been scanned by multiple security services:

- **VirusTotal** (0 detections): https://www.virustotal.com/gui/file/0c023e9fb42c93881cd85d218bb20d6deba38f9a3d6f8c03992209df8325bc74
- **Koodous**: https://koodous.com/apks/0c023e9fb42c93881cd85d218bb20d6deba38f9a3d6f8c03992209df8325bc74/general-information

## License

MIT — see [LICENSE](LICENSE).

## Acknowledgements

- The shell types and show structure are modeled after traditional Japanese
  fireworks (uchiage hanabi).
- Built with [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
- Developed with the assistance of AI (Anthropic Claude) for design and coding;
  all behavior was reviewed and tuned by hand on an actual device.

---

*Made by [Yuma-Eimymk2](https://github.com/Yuma-Eimymk2).*
