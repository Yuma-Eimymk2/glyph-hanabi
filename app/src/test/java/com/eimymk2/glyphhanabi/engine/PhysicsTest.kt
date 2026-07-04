package com.eimymk2.glyphhanabi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/**
 * SPEC「単体テストの観点」:
 * - 等方抵抗: 一定時間後の |vx|/|vy| 比が保存されること (重力ゼロ時)
 * - hold 中の粒の重心 y が変化しないこと (真円保持)
 * (+ マスク外消滅、シード決定論性)
 */
class PhysicsTest {

    @Test
    fun `等方抵抗は vx と vy の比を保存する (重力ゼロ)`() {
        val engine = HanabiEngine(Random(1))
        // vx=1.5, vy=2.0 になる角度と速さで 1 粒だけ生成
        val p = engine.spawn(
            6f, 6f,
            angle = atan2(2.0f, 1.5f), speed = 2.5f,
            params = StarParams(gravity = 0f, drag = 0.8f, hold = 0f,
                                tailN = 0, fadeAt = 10f, lifeAt = 20f))
        val initialRatio = abs(p.vx) / abs(p.vy)

        repeat(20) { engine.update() }  // 1.6 秒分

        assertEquals(1, engine.particles.size)
        val ratio = abs(p.vx) / abs(p.vy)
        // 減衰は掛け算 (v *= 1 - drag*dt) なので比は理論上ぴったり保存される
        assertEquals(initialRatio, ratio, 1e-4f)
        // 減衰自体はちゃんと効いていること
        assertTrue("速度は減衰しているはず", abs(p.vx) < 1.5f)
    }

    @Test
    fun `hold 中は重力が掛からず、hold 経過後に掛かり始める`() {
        val engine = HanabiEngine(Random(1))
        // 真横に飛ぶ粒 (vy=0)。重力 5 は強めにして効果を見やすく。
        // hold はフレーム境界 (0.08 の倍数) に揃えない値にする:
        // 0.48 だと Float の累積誤差で age が 0.48f に届かず判定が不安定になる
        val p = engine.spawn(
            6f, 6f, angle = 0f, speed = 1.0f,
            params = StarParams(gravity = 5f, drag = 0f, hold = 0.44f,
                                tailN = 0, fadeAt = 10f, lifeAt = 20f))

        // age 0.08〜0.40 の 5 フレームは hold 内 → vy はゼロのまま
        repeat(5) {
            engine.update()
            assertEquals("hold 中 (age=${p.age}) の vy", 0f, p.vy, 1e-6f)
        }
        // 6 フレーム目で age=0.48 ≥ hold → 重力が掛かる
        engine.update()
        assertTrue("hold 経過後は vy > 0 (落下開始)", p.vy > 0f)
    }

    @Test
    fun `hold 中は burst の重心 y が変化しない (真円保持)`() {
        // 牡丹 (hold=0.5)。ring の対称性で重心が動かないことを確認する
        for (seed in 1..10) {
            val engine = HanabiEngine(Random(seed))
            engine.burst(6f, 5f, ShellType.BOTAN)
            val initialY = engine.particles.map { it.y }.average()

            // age 0.08〜0.48 の 6 フレームはすべて hold (0.5) 内
            repeat(6) {
                engine.update()
                val centroidY = engine.particles.map { it.y }.average()
                // 速度±3%・角度±0.04rad の揺らぎ分だけわずかに漂うのを許容
                assertEquals("seed=$seed frame=${it + 1} の重心 y",
                    initialY, centroidY, 0.05)
            }
        }
    }

    @Test
    fun `円形マスク外 (中心から距離² が 52 超) に出た粒は消滅する`() {
        val engine = HanabiEngine(Random(1))
        // 真横に猛スピード。1.6 cells/frame で飛んで半径 √52≈7.2 を超えたら消える
        engine.spawn(
            6f, 6f, angle = 0f, speed = 20f,
            params = StarParams(gravity = 0f, drag = 0f, hold = 0f,
                                tailN = 0, fadeAt = 10f, lifeAt = 20f))

        repeat(4) { engine.update() }  // x-6 = 6.4 → まだ内側
        assertEquals(1, engine.particles.size)
        engine.update()                // x-6 = 8.0 → 52 < 64 で消滅
        assertEquals(0, engine.particles.size)
    }

    @Test
    fun `同一シードなら全粒の状態列が完全に一致する (決定論性)`() {
        // ロケット打ち上げ→開花→減衰まで含む 30 フレームを 2 回走らせて突き合わせ
        fun run(seed: Int): String {
            val engine = HanabiEngine(Random(seed))
            engine.fire(ShellType.KIKU)
            val frames = StringBuilder()
            repeat(30) {
                engine.update()
                frames.append(engine.signature()).append('\n')
            }
            return frames.toString()
        }
        // 文字列そのものを比較 (contentDeepHashCode は構造的衝突があるので使わない)
        assertEquals(run(42), run(42))
        assertTrue("シードが違えば結果も違う", run(42) != run(43))
    }

    /** エンジンの全可動状態を文字列化 (Float.toString はロケール非依存) */
    private fun HanabiEngine.signature(): String = buildString {
        append(time).append('|')
        rockets.forEach { append(it.x).append(',').append(it.y).append(',').append(it.wait).append(';') }
        append('|')
        particles.forEach {
            append(it.x).append(',').append(it.y).append(',')
            append(it.vx).append(',').append(it.vy).append(',')
            append(it.age).append(',').append(it.brightness).append(';')
        }
    }
}
