package com.eimymk2.glyphhanabi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * SPEC「単体テストの観点」:
 * - lifeAt 経過後、burst の全粒が消滅していること (消え口の決定論性が要)
 * (+ 明度エンベロープの形の検証)
 */
class EnvelopeTest {

    @Test
    fun `明度は fadeAt まで満輝度、その後 lifeAt に向けて線形減衰`() {
        val engine = HanabiEngine(Random(1))
        val p = engine.spawn(
            6f, 6f, angle = 0f, speed = 0.1f,
            params = StarParams(gravity = 0f, drag = 0f, hold = 0f,
                                tailN = 0, fadeAt = 0.4f, lifeAt = 0.8f))

        // age 0.08〜0.32: 満輝度
        repeat(4) {
            engine.update()
            assertEquals("age=${p.age} は満輝度", 1.0f, p.brightness, 1e-6f)
        }
        // age 0.56: 1 - (0.56-0.4)/(0.8-0.4) = 0.6
        repeat(3) { engine.update() }
        assertEquals(0.6f, p.brightness, 0.01f)
    }

    @Test
    fun `牡丹は lifeAt (0,75s) で全粒が一斉に消える`() {
        for (seed in 1..10) {
            val engine = HanabiEngine(Random(seed))
            engine.burst(6f, 5f, ShellType.BOTAN)
            assertEquals(24, engine.particles.size)

            // 9 フレーム目 (age=0.72 < 0.75): まだ 1 粒も欠けていない
            repeat(9) { engine.update() }
            assertEquals("seed=$seed: 消え口直前は全粒生存", 24, engine.particles.size)

            // 10 フレーム目 (age=0.80 ≥ 0.75): 全粒同時に消灯 = 消え口が揃う
            engine.update()
            assertEquals("seed=$seed: 消え口で一斉消灯", 0, engine.particles.size)
        }
    }

    @Test
    fun `全玉種が lifeAt 経過後に全粒消滅している`() {
        // burst 系の最長寿命は錦冠の 3.6s。50 フレーム (4.0s) 回せば全て消えているはず
        val burstTypes = listOf(
            ShellType.KIKU, ShellType.BOTAN, ShellType.SHIN, ShellType.KAMURO,
            ShellType.YASHI, ShellType.YANAGI, ShellType.HACHI)
        for (type in burstTypes) {
            for (seed in 1..5) {
                val engine = HanabiEngine(Random(seed))
                engine.burst(6f, 5f, type)
                repeat(50) { engine.update() }
                assertEquals("$type seed=$seed", 0, engine.particles.size)
                assertTrue("$type seed=$seed は完全アイドル", engine.isIdle)
            }
        }
    }

    @Test
    fun `千輪とナイアガラも寿命経過後に完全アイドルになる`() {
        // 千輪: 遅延 max0.25s + 寿命 0.65s → 1.2s で十分
        val senrin = HanabiEngine(Random(1))
        senrin.fire(ShellType.SENRIN)
        repeat(15) { senrin.update() }
        assertTrue("千輪は 1.2s で完全アイドル", senrin.isIdle)

        // ナイアガラ: 放出 3s + 寿命 2.8s → 6.0s で十分
        val niagara = HanabiEngine(Random(1))
        niagara.fire(ShellType.NIAGARA)
        repeat(75) { niagara.update() }
        assertTrue("ナイアガラは 6.0s で完全アイドル", niagara.isIdle)
    }
}
