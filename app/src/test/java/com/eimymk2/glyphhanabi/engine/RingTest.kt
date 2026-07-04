package com.eimymk2.glyphhanabi.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/**
 * SPEC「単体テストの観点」:
 * - ring() 生成直後の粒の速度ノルムが speed ±3% 内であること
 * (+ 等間隔配置の検証。等間隔 + 統一速度が真円の必要条件なので)
 */
class RingTest {

    // hold 長め・寿命長めの無難なパラメータ (このテストでは物理は動かさない)
    private val params = StarParams(
        gravity = 0.5f, drag = 0.7f, hold = 0.5f, tailN = 0, fadeAt = 10f, lifeAt = 20f)

    @Test
    fun `ring は指定した個数の粒を生成する`() {
        val engine = HanabiEngine(Random(1))
        engine.ring(6f, 6f, 24, 5.0f, params)
        assertEquals(24, engine.particles.size)
    }

    @Test
    fun `生成直後の速度ノルムが speed の ±3パーセント内`() {
        // 複数シードで確認 (乱数の揺らぎ幅そのものの検証なので)
        for (seed in 1..20) {
            val engine = HanabiEngine(Random(seed))
            engine.ring(6f, 6f, 24, 5.0f, params)
            engine.particles.forEach { p ->
                val speed = hypot(p.vx, p.vy)
                // ±3% ちょうどが出ても Float 誤差で落ちないよう、わずかに緩める
                assertEquals("seed=$seed の粒の速度ノルム", 5.0f, speed, 5.0f * 0.0305f)
            }
        }
    }

    @Test
    fun `角度が等間隔 (2π を n 等分、揺らぎ ±0,04rad)`() {
        val n = 24
        val expectedGap = 2.0 * PI / n
        for (seed in 1..20) {
            val engine = HanabiEngine(Random(seed))
            engine.ring(6f, 6f, n, 5.0f, params)

            // 速度ベクトルから角度を復元し、昇順に並べて隣同士の間隔を見る
            val angles = engine.particles
                .map { atan2(it.vy.toDouble(), it.vx.toDouble()).let { a -> if (a < 0) a + 2 * PI else a } }
                .sorted()
            val gaps = angles.zipWithNext { a, b -> b - a } +
                    (angles.first() + 2 * PI - angles.last())  // 末尾→先頭のラップ分

            gaps.forEach { gap ->
                // 隣接2粒がそれぞれ ±0.04rad 揺れるので間隔の誤差は最大 0.08rad
                // (+ sim 由来の 2π≈6.283 丸めと Float 誤差の余裕をみて 0.1)
                assertEquals("seed=$seed の角度間隔", expectedGap, gap, 0.1)
            }
        }
    }
}
