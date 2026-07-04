package com.eimymk2.glyphhanabi.engine

/**
 * 粒(星)。座標・速度はすべて Float のセル単位(サブピクセル)。
 *
 * data class ではなく通常の class なのは、毎フレーム書き換わるミュータブルな
 * 状態(x, y, vx, vy, age...)の入れ物だから。
 */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val params: StarParams,
    val flags: Int = 0,
) {
    var age: Float = 0f

    /**
     * このフレームの表示明度 (0..1)。HanabiEngine.update() が毎フレーム
     * エンベロープ + FLICKER を計算して書き込む。FrameCompositor はこれを読むだけ。
     */
    var brightness: Float = 1f

    /**
     * 尾(光条)用の位置履歴。移動「前」の位置を先頭に積み、tailN 個まで保持。
     * 垂れ系(錦冠・柳)では放物線に沿った曲線の尾になる。
     */
    val history: ArrayDeque<Pair<Float, Float>> = ArrayDeque()

    /**
     * 明度エンベロープ (burst 内で共有 → 消え口が揃う):
     *   age < fadeAt          → 1.0 (満輝度)
     *   fadeAt ≤ age < lifeAt → 線形に 1.0 → 0.0
     *   age ≥ lifeAt          → 0 以下 (消滅)
     */
    fun envelopeAt(age: Float): Float =
        if (age < params.fadeAt) 1f
        else 1f - (age - params.fadeAt) / (params.lifeAt - params.fadeAt)

    fun hasFlag(flag: Int): Boolean = (flags and flag) != 0

    companion object {
        // Int のビットフラグ。or で組み合わせる (例: JITTER or FLICKER)
        const val JITTER = 1  // 蜂: 毎フレーム速度をランダム回転+ノイズ
        const val FLICKER = 2 // 蜂: 明度を毎フレーム ×rand(0.6, 1)
        const val THICK = 4   // 椰子: 隣接4セルに明度40%を撒く(描画は FrameCompositor)
    }
}
