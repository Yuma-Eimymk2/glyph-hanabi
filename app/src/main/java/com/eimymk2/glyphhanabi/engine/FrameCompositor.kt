package com.eimymk2.glyphhanabi.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 13×13 明度バッファへの描画 (Phase 2)。純 Kotlin、Android 依存なし。
 * シミュレータ v6 の buf / put() / deposit() / draw() に対応する。
 *
 * 毎フレーム compose(engine) を呼ぶと:
 *   1. 残像減衰 (バッファ全体 × 0.58)   … SPEC 演算順序のステップ 1
 *   2. ロケット (上昇の2点光条 / ための点) と全粒 + 尾を deposit … ステップ 4-5 の描画分
 * snapshot() で 0-255 量子化した IntArray を取り出す。
 * 25×25 中央配置は Phase 3 の GlyphRenderer (Glyph Life 流用) の仕事。
 *
 * [gamma] と [maxLevel] は実機 LED に合わせて Phase 4 で調整する暫定値。
 */
class FrameCompositor(
    private val gamma: Float = 0.8f,  // 表示ガンマ: 表示明度 = v^0.8
    private val maxLevel: Int = 153,  // 全体明度上限 60% (= 255 × 0.6)
) {
    companion object {
        const val SIZE = HanabiEngine.GRID  // 13
        const val DECAY = 0.58f             // 全体残像: 毎フレーム buffer *= 0.58
        const val TAIL_MIN = 0.03f          // これ未満の尾は描かない

        // ロケットの描画明度 (シミュレータ v6 と同値)
        const val ROCKET_HEAD = 0.9f    // 上昇中の先頭
        const val ROCKET_TRAIL = 0.4f   // 先頭の 0.7 セル下に置く残り火
        const val ROCKET_TRAIL_OFFSET = 0.7f
        const val ROCKET_TAME = 0.3f    // 「ため」の静止点

        const val THICK_LEVEL = 0.4f    // 椰子: 隣接4セルに撒く明度の比率
    }

    /** 13×13 の明度バッファ (0..1)。1 次元配列で r*13+c に格納 */
    private val buffer = FloatArray(SIZE * SIZE)

    /** 円形マスク: 中心 (6,6) から距離² ≤ 42 のセルだけが実LED */
    private val mask = BooleanArray(SIZE * SIZE) { i ->
        val dr = i / SIZE - HanabiEngine.CENTER.toInt()
        val dc = i % SIZE - HanabiEngine.CENTER.toInt()
        dr * dr + dc * dc <= HanabiEngine.MASK_R2
    }

    fun brightnessAt(r: Int, c: Int): Float = buffer[r * SIZE + c]

    /** 消灯 (ショー終了時など) */
    fun clear() = buffer.fill(0f)

    /** engine.update() 後に毎フレーム呼ぶ。減衰 → ロケット → 粒 + 尾の順で描く */
    fun compose(engine: HanabiEngine) {
        // 1. 残像減衰
        for (i in buffer.indices) buffer[i] *= DECAY

        // 4. ロケット (wait==0 はまだ上昇中の描画。burst したフレームの
        //    「ため」の点 0.3 は開花粒の満輝度に max 合成で埋もれるため描かない)
        for (rk in engine.rockets) {
            if (rk.wait == 0f) {
                deposit(rk.x, rk.y, ROCKET_HEAD)
                deposit(rk.x, rk.y + ROCKET_TRAIL_OFFSET, ROCKET_TRAIL)
            } else {
                deposit(rk.x, rk.y, ROCKET_TAME)
            }
        }

        // 5. 粒: 本体 + 履歴に沿った尾
        for (p in engine.particles) {
            deposit(p.x, p.y, p.brightness, p.hasFlag(Particle.THICK))
            val n = p.history.size
            p.history.forEachIndexed { i, (hx, hy) ->
                // 尾の明度グラデーション: 明度 × (1 - (i+1)/(n+1.5))
                val w = p.brightness * (1f - (i + 1) / (n + 1.5f))
                if (w > TAIL_MIN) deposit(hx, hy, w)
            }
        }
    }

    /**
     * バイリニア deposit: Float 座標を周囲 4 セルに小数比率で配分 (max 合成)。
     * 円の輪郭を滑らかにする要。thick (椰子) は最寄りセルの上下左右にも 40% を撒く。
     */
    fun deposit(x: Float, y: Float, b: Float, thick: Boolean = false) {
        val c0 = floor(x).toInt()
        val r0 = floor(y).toInt()
        val fx = x - c0
        val fy = y - r0
        put(r0, c0, b * (1 - fx) * (1 - fy))
        put(r0, c0 + 1, b * fx * (1 - fy))
        put(r0 + 1, c0, b * (1 - fx) * fy)
        put(r0 + 1, c0 + 1, b * fx * fy)
        if (thick) {
            val rc = y.roundToInt()
            val cc = x.roundToInt()
            put(rc, cc - 1, b * THICK_LEVEL)
            put(rc, cc + 1, b * THICK_LEVEL)
            put(rc - 1, cc, b * THICK_LEVEL)
            put(rc + 1, cc, b * THICK_LEVEL)
        }
    }

    /** 盤面内かつマスク内のセルにだけ、max 合成で書き込む */
    private fun put(r: Int, c: Int, v: Float) {
        if (r in 0 until SIZE && c in 0 until SIZE) {
            val i = r * SIZE + c
            if (mask[i]) buffer[i] = max(buffer[i], v)
        }
    }

    /**
     * 0-255 量子化した 13×13 フレーム (169 要素、行優先)。
     * ガンマ v^0.8 と明度上限 (maxLevel) を適用済み。
     * Phase 3 の GlyphRenderer がこれを 25×25 中央配置に変換する。
     */
    fun snapshot(): IntArray = IntArray(SIZE * SIZE) { i ->
        (min(1f, buffer[i]).pow(gamma) * maxLevel).toInt()
    }
}
