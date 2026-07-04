package com.eimymk2.glyphhanabi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * FrameCompositor: バイリニア deposit / max 合成 / 円形マスク / 残像減衰 /
 * 尾のグラデーション / THICK / ロケット描画 / 0-255 量子化。
 * 数値はすべてシミュレータ v6 の deposit()・draw() と同じ計算則。
 */
class FrameCompositorTest {

    // 物理を邪魔しない素通しパラメータ (重力・抵抗なし、寿命長め)
    private fun inertParams(tailN: Int = 0) = StarParams(
        gravity = 0f, drag = 0f, hold = 0f, tailN = tailN, fadeAt = 10f, lifeAt = 20f)

    @Test
    fun `整数座標への deposit は 1 セルに全量入る`() {
        val fc = FrameCompositor()
        fc.deposit(3f, 4f, 1f)
        assertEquals(1f, fc.brightnessAt(4, 3), 1e-6f)  // 引数は (x=列, y=行) の順
        assertEquals(0f, fc.brightnessAt(4, 4), 1e-6f)
        assertEquals(0f, fc.brightnessAt(5, 3), 1e-6f)
    }

    @Test
    fun `セル中間への deposit は周囲 4 セルに小数比率で配分される`() {
        val fc = FrameCompositor()
        fc.deposit(6.5f, 6.5f, 0.8f)
        // fx=fy=0.5 なので 4 セル均等: 0.8 × 0.25 = 0.2
        assertEquals(0.2f, fc.brightnessAt(6, 6), 1e-6f)
        assertEquals(0.2f, fc.brightnessAt(6, 7), 1e-6f)
        assertEquals(0.2f, fc.brightnessAt(7, 6), 1e-6f)
        assertEquals(0.2f, fc.brightnessAt(7, 7), 1e-6f)
    }

    @Test
    fun `同一セルへの deposit は max 合成 (加算しない)`() {
        val fc = FrameCompositor()
        fc.deposit(6f, 6f, 0.5f)
        fc.deposit(6f, 6f, 0.3f)
        assertEquals(0.5f, fc.brightnessAt(6, 6), 1e-6f)
    }

    @Test
    fun `円形マスク外と盤面外には書き込まれない (クラッシュもしない)`() {
        val fc = FrameCompositor()
        fc.deposit(0f, 0f, 1f)     // 四隅はマスク外 (距離² = 72 > 42)
        assertEquals(0f, fc.brightnessAt(0, 0), 1e-6f)
        fc.deposit(-1f, -1f, 1f)   // 盤面外 → put の境界チェックで無視
        fc.deposit(12.9f, 12.9f, 1f)
    }

    @Test
    fun `THICK は最寄りセルの上下左右に明度 40 パーセントを撒く`() {
        val fc = FrameCompositor()
        fc.deposit(6.2f, 6.2f, 1f, thick = true)
        // バイリニア分: (6,6) = 0.8×0.8 = 0.64
        assertEquals(0.64f, fc.brightnessAt(6, 6), 1e-5f)
        // 上下左右: バイリニアの端数 (≤0.16) より THICK の 0.4 が勝つ (max 合成)
        assertEquals(0.4f, fc.brightnessAt(6, 5), 1e-5f)
        assertEquals(0.4f, fc.brightnessAt(6, 7), 1e-5f)
        assertEquals(0.4f, fc.brightnessAt(5, 6), 1e-5f)
        assertEquals(0.4f, fc.brightnessAt(7, 6), 1e-5f)
    }

    @Test
    fun `残像は毎フレーム 0,58 倍で減衰する`() {
        val fc = FrameCompositor()
        val empty = HanabiEngine(Random(1))  // 粒なし → compose は減衰だけ
        fc.deposit(6f, 6f, 1f)
        fc.compose(empty)
        assertEquals(0.58f, fc.brightnessAt(6, 6), 1e-5f)
        fc.compose(empty)
        assertEquals(0.58f * 0.58f, fc.brightnessAt(6, 6), 1e-5f)
    }

    @Test
    fun `尾は履歴に沿って本体より暗いグラデーションで描かれる`() {
        val engine = HanabiEngine(Random(1))
        // 真横 (角度0) に等速 2.5 cells/s で進む粒。3 フレームで x: 4.0 → 4.6
        engine.spawn(4f, 6f, angle = 0f, speed = 2.5f, params = inertParams(tailN = 3))
        repeat(3) { engine.update() }

        val fc = FrameCompositor()
        fc.compose(engine)

        // 本体 x=4.6: セル(6,5) に 0.6、履歴 [(4.4),(4.2),(4.0)] の尾が後方に伸びる
        // (6,5) は本体の 0.6 が最大、(6,4) は尾 i=0 の 0.778×0.6 ≈ 0.467 が最大
        assertEquals(0.6f, fc.brightnessAt(6, 5), 0.01f)
        assertEquals(1f * (1f - 1f / 4.5f) * 0.6f, fc.brightnessAt(6, 4), 0.01f)
        assertTrue("頭のほうが尾より明るい",
            fc.brightnessAt(6, 5) > fc.brightnessAt(6, 4))
        assertTrue("尾の後方にも光が残る", fc.brightnessAt(6, 4) > 0.1f)
    }

    @Test
    fun `上昇中のロケットは光条が描かれ、ため中は暗い点になる`() {
        val engine = HanabiEngine(Random(1))
        engine.fire(ShellType.BOTAN)
        engine.update()  // 1 フレーム上昇 (y = 11.86)

        val fc = FrameCompositor()
        fc.compose(engine)
        val ascentPeak = maxOf(fc)
        assertTrue("上昇中は明るい点が見える (peak=$ascentPeak)", ascentPeak > 0.2f)

        // 頂点到達 → 「ため」1 フレーム目まで進める
        while (engine.rockets.isNotEmpty() && engine.rockets[0].wait == 0f) {
            engine.update()
        }
        assertTrue("まだ開花していない", engine.rockets.isNotEmpty())
        val fc2 = FrameCompositor()
        fc2.compose(engine)
        val tamePeak = maxOf(fc2)
        assertTrue("ため中は明度 0.3 の静止点 (peak=$tamePeak)",
            tamePeak in 0.05f..0.3f)
    }

    @Test
    fun `snapshot は ガンマ 0,8 と上限 153 を適用した 0-255 値を返す`() {
        val fc = FrameCompositor()
        fc.deposit(6f, 6f, 1f)     // 満輝度 → 上限 153
        fc.deposit(5f, 6f, 0.5f)   // セル (r=6, c=5) へ。0.5^0.8 × 153 = 87
        val frame = fc.snapshot()
        assertEquals(169, frame.size)
        assertEquals(153, frame[6 * 13 + 6])
        assertEquals(87, frame[6 * 13 + 5])
        assertEquals(0, frame[0])  // マスク外は常に 0

        // 1 超えの入力もクランプされる
        val fc2 = FrameCompositor()
        fc2.deposit(6f, 6f, 1.5f)
        assertEquals(153, fc2.snapshot()[6 * 13 + 6])
    }

    @Test
    fun `clear で全消灯する`() {
        val fc = FrameCompositor()
        fc.deposit(6f, 6f, 1f)
        fc.clear()
        assertEquals(0f, fc.brightnessAt(6, 6), 1e-6f)
        assertTrue(fc.snapshot().all { it == 0 })
    }

    /** バッファ全セルの最大値 */
    private fun maxOf(fc: FrameCompositor): Float {
        var m = 0f
        for (r in 0 until FrameCompositor.SIZE) {
            for (c in 0 until FrameCompositor.SIZE) {
                if (fc.brightnessAt(r, c) > m) m = fc.brightnessAt(r, c)
            }
        }
        return m
    }
}
