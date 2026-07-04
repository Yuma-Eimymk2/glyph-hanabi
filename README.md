# Glyph Hanabi 🎆

> *Japanese fireworks (hanabi), blooming on the back of your Nothing Phone (4a) Pro.*
>
> *Nothing Phone (4a) Pro の Glyph Matrix で打ち上げる、日本の花火大会。*

Sister app of [Glyph Life](https://github.com/Yuma-Eimymk2/glyph-life).

## Concept / コンセプト

> *"A one-minute hanabi festival in the palm of your hand."*
>
> **「掌の上の、一分間の花火大会」**

Place your phone face down and a fireworks show begins on the Glyph Matrix:
shells rise one by one, bloom, and fall — building up to a rapid finale barrage,
a beat of silence, and a Niagara curtain to close the night.

スマホを伏せると、Glyph Matrix の夜空で花火大会が始まります。
一発ずつ上がる尺玉、連発のフィナーレ、余韻の間、そして締めのナイアガラまで、
本物の花火大会の構成をそのまま 13×13 の盤面に載せました。

## Features / 特徴

- **9 traditional shell types** — kiku (chrysanthemum), botan (peony), shin-iri (double core),
  senrin (thousand blooms), yanagi (willow), yashi (palm), hachi (bees),
  nishiki-kamuro (golden crown), and a Niagara finale.
  **9 玉種** — 菊・牡丹・芯入り・千輪・柳・椰子・蜂・錦冠、そして締めのナイアガラ
- **A real show structure** — ~60 seconds: build-up, finale barrage, a pause, then Niagara.
  **花火大会の構成** — 約 60 秒。通常部 → フィナーレ連発 → 間 → ナイアガラ
- **Physics-based** — launch, burst, gravity, drag and brightness envelopes,
  tuned shell by shell against a browser simulator and on the actual device.
  **物理ベース** — 打ち上げ・開花・重力・抵抗・明度エンベロープを玉種ごとに調整
- **You control the timing** — The show starts the moment the screen goes dark:
  press the power button or flip the phone face down. Waking the screen ends it.
  **タイミングは自分で** — 画面が消えた瞬間に開演。電源ボタンでも伏せるでも。
  画面を点けると終演
- **Rests while charging** — The matrix stays dark on the charger.
  Hanabi belongs to the night, not the charging cable.
  **充電中は休演** — 花火は夜のもの。充電ケーブルの上では上がりません

## Usage / 使い方

1. Install from [Releases](https://github.com/Yuma-Eimymk2/glyph-hanabi/releases)
   (or Nothing Playground, once approved).
   [Releases](https://github.com/Yuma-Eimymk2/glyph-hanabi/releases) からインストール
   (Playground 承認後はそちらからも)
2. **Launch the Glyph Hanabi app once.** Android keeps freshly installed apps frozen
   until first launch, and the Toy cannot start while frozen.
   **インストール後に一度アプリを開いてください。** 初回起動するまで Android は
   アプリを凍結状態にしており、そのままでは Toy が動きません
3. Go to **Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy**
   and select **Glyph Hanabi**.
   **設定 → Glyph Interface → Flip to Glyph → Always-on Glyph Toy** で
   **Glyph Hanabi** を選択
4. Press the power button, or place your phone face down — the show begins
   the moment the screen goes dark.
   電源ボタンを押すか、スマホを伏せると開演です (画面が消えた瞬間に始まります)

## A Note on Behavior / 動作について

- Each show runs for about a minute and ends on its own. Every screen-off starts
  one show; waking the screen stops it immediately.
  1 回のショーは約 1 分で自然に終わります。画面が消えるたびに 1 回上がり、
  画面を点けるとすぐ終演します
- While charging, the matrix intentionally stays dark.
  充電中は意図的に点灯しません
- Like real hanabi, it looks best in a dim room. The Glyph LEDs are subtle in daylight.
  本物の花火と同じで、暗い場所での観覧がいちばんきれいです

## Requirements / 動作環境

- Nothing Phone (4a) Pro

## Building from source / ソースからのビルド

This repository contains the full source code, but the Glyph Matrix SDK binary is not
included due to Nothing's redistribution terms. To build Glyph Hanabi yourself:

1. Clone this repository.
2. Download the Glyph Matrix SDK from
   [Nothing GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
3. Place the `.aar` in `app/libs/` (any filename works).
4. Open the project in Android Studio and build.

Note: a Phone (4a) Pro is required to actually see the output.
The firework engine itself is pure Kotlin — `./gradlew test` runs the whole
36-test suite without a device.

このリポジトリにはソースコード一式が含まれていますが、Glyph Matrix SDK のバイナリは
Nothing の再配布条件により含まれていません。自分でビルドする場合は:

1. このリポジトリを clone する
2. [Nothing GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) から SDK をダウンロードする
3. `.aar` を `app/libs/` に配置 (ファイル名は自由)
4. Android Studio でプロジェクトを開いてビルド

注: 実際の動作確認には Phone (4a) Pro が必要です。
花火エンジン自体は純 Kotlin なので、`./gradlew test` で全 36 テストが端末なしで走ります。

## Security / セキュリティ

The release APK has been scanned by multiple security services:

<!-- TODO(release): リリース APK 生成後にスキャン URL を差し替えること -->
- **VirusTotal**: (link will be added with the v0.1.0 release)
- **Koodous**: (link will be added with the v0.1.0 release)

リリース版 APK は複数のセキュリティサービスでスキャン済みです (v0.1.0 リリース時にリンク追加)。

## License

MIT — see [LICENSE](LICENSE).

## Acknowledgements / 謝辞

- The shell types and show structure are modeled after traditional Japanese
  fireworks (uchiage hanabi).
  玉種と進行は日本の打ち上げ花火の伝統的な構成に倣っています
- Built with [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
- Developed with the assistance of AI (Anthropic Claude) for design and coding;
  all behavior was reviewed and tuned by hand on an actual device.
  設計・実装に AI (Anthropic Claude) の支援を受けています。挙動はすべて実機で
  人間がレビュー・調整しています

---

*Made by [Yuma-Eimymk2](https://github.com/Yuma-Eimymk2).*
