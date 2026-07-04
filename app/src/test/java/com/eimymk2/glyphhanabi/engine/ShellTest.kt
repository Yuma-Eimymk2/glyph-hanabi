package com.eimymk2.glyphhanabi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * 玉種ごとの生成則と、ロケット・千輪 pending・ナイアガラ emitter・蜂の特殊挙動。
 * パラメータの数値は SPEC「玉種パラメータ (確定値)」= シミュレータ v6 と同一。
 */
class ShellTest {

    @Test
    fun `玉種ごとの生成粒数が SPEC どおり`() {
        val expected = mapOf(
            ShellType.KIKU to 48,   // ring28 + ring14 + 散り星6
            ShellType.BOTAN to 24,
            ShellType.SHIN to 36,   // ring24 + ring12
            ShellType.KAMURO to 22,
            ShellType.YASHI to 7,
            ShellType.YANAGI to 16,
            ShellType.HACHI to 10,
        )
        expected.forEach { (type, count) ->
            val engine = HanabiEngine(Random(1))
            engine.burst(6f, 5f, type)
            assertEquals("$type の粒数", count, engine.particles.size)
        }
    }

    @Test
    fun `ロケットは 8 cells毎秒 で上昇し、頂点で 0,2s ためてから開花する`() {
        val engine = HanabiEngine(Random(1))
        engine.fire(ShellType.BOTAN)
        assertEquals(1, engine.rockets.size)
        val rocket = engine.rockets[0]
        assertEquals(12.5f, rocket.y, 1e-6f)
        assertTrue("開花高度は 4.2〜5.6", rocket.apex in 4.2f..5.6f)
        assertTrue("打ち上げ x は 5〜7.5", rocket.x in 5f..7.5f)

        // 1 フレームで 8 * 0.08 = 0.64 セル上昇 (y は上向きに減る)
        engine.update()
        assertEquals(12.5f - 0.64f, rocket.y, 1e-4f)

        // 頂点到達後、「ため」のフレーム数を数えながら開花まで回す
        var tameFrames = 0
        var frames = 1
        while (engine.rockets.isNotEmpty() && frames < 40) {
            val wasTame = !engine.rockets[0].isAscending
            engine.update()
            if (wasTame) tameFrames++
            frames++
        }
        // ため 0.2s = wait が 0.08, 0.16, 0.24(≥0.2 で開花) の 3 フレーム
        assertEquals("ため区間のフレーム数", 3, tameFrames)

        // 開花: 牡丹 24 粒が頂点位置の近傍に生まれている
        assertEquals(24, engine.particles.size)
        val cx = engine.particles.map { it.x }.average()
        val cy = engine.particles.map { it.y }.average()
        assertEquals("開花中心 x", rocket.x.toDouble(), cx, 0.1)
        assertEquals("開花中心 y", rocket.y.toDouble(), cy, 0.1)
        assertTrue("開花位置は apex 以上の高さ", rocket.y <= rocket.apex)
    }

    @Test
    fun `蜂はロケットで打ち上がる (sim v6 準拠)`() {
        val engine = HanabiEngine(Random(1))
        engine.fire(ShellType.HACHI)
        assertEquals(1, engine.rockets.size)
        assertEquals(0, engine.particles.size)
    }

    @Test
    fun `蜂の速度は上限 2,2 を超えない`() {
        for (seed in 1..10) {
            val engine = HanabiEngine(Random(seed))
            engine.burst(6f, 5f, ShellType.HACHI)
            repeat(8) {
                engine.update()
                engine.particles.forEach { p ->
                    assertTrue("seed=$seed |v|=${hypot(p.vx, p.vy)}",
                        hypot(p.vx, p.vy) <= 2.2001f)
                }
            }
        }
    }

    @Test
    fun `蜂だけは fade と life が粒ごとに異なる (消え口を揃えない)`() {
        val engine = HanabiEngine(Random(1))
        engine.burst(6f, 5f, ShellType.HACHI)
        val lives = engine.particles.map { it.params.lifeAt }.distinct()
        assertTrue("蜂の lifeAt は粒ごとにばらける", lives.size > 1)
        engine.particles.forEach { p ->
            assertTrue(p.params.fadeAt in 0.5f..1.0f)
            assertTrue(p.params.lifeAt in 1.1f..1.8f)
        }

        // 対照: 牡丹は全粒が同じ StarParams を共有する
        val botan = HanabiEngine(Random(1))
        botan.burst(6f, 5f, ShellType.BOTAN)
        assertEquals(1, botan.particles.map { it.params }.distinct().size)
    }

    @Test
    fun `千輪は 6〜8 箇所の予約が 0,25s 以内に全て開花する`() {
        for (seed in 1..10) {
            val engine = HanabiEngine(Random(seed))
            engine.fire(ShellType.SENRIN)
            val k = engine.pendingCount
            assertTrue("seed=$seed: 予約数 6〜8 (実際 $k)", k in 6..8)
            assertEquals("発火直後はまだ粒なし", 0, engine.particles.size)

            repeat(4) { engine.update() }  // 0.32s > 最大遅延 0.25s
            assertEquals("seed=$seed: 全予約が開花済み", 0, engine.pendingCount)
            assertEquals("seed=$seed: 小 ring 8 粒 × $k 箇所", k * 8, engine.particles.size)
        }
    }

    @Test
    fun `ナイアガラは 3 秒で放出が止まり、粒は下向きに落ちる`() {
        val engine = HanabiEngine(Random(1))
        engine.fire(ShellType.NIAGARA)
        assertEquals(1, engine.emitterCount)
        assertEquals("エミッターはロケットを使わない", 0, engine.rockets.size)

        repeat(10) { engine.update() }  // 0.8s: 放出中
        assertTrue("放出中は粒が存在する", engine.particles.isNotEmpty())
        engine.particles.forEach { p ->
            assertTrue("粒は下向き (vy > 0) に落ちる", p.vy > 0f)
            assertTrue("列 1〜11 から放出される (x=${p.x})",
                p.x > 0.5f && p.x < 11.5f)
        }

        repeat(28) { engine.update() }  // 累計 3.04s ≥ 3s
        assertEquals("3 秒経過で放出停止", 0, engine.emitterCount)
    }

    @Test
    fun `錦冠は hold 後にゆっくり垂れ、終端速度に漸近する`() {
        // 開発者お気に入りの玉種なので挙動を固定しておく。
        // 連続極限の終端速度は g/drag = 2.1/1.2 = 1.75 cells/s (SPEC の値) だが、
        // dt=0.08 の離散積分では不動点が v* = g·dt·(1-drag·dt)/(drag·dt) ≈ 1.58 になる
        // (シミュレータ v6 も同じ積分なので同じ値に収束する)
        val engine = HanabiEngine(Random(1))
        engine.burst(6f, 3.5f, ShellType.KAMURO)

        // hold (0.55s) + 収束時間を経た 2.8s 時点で、生き残りの落下速度は
        // 終端速度近傍に収束しているはず (life=3.6s なのでまだ生きている)
        repeat(35) { engine.update() }
        assertTrue("錦冠の粒がまだ残っている", engine.particles.isNotEmpty())
        engine.particles.forEach { p ->
            assertEquals("垂れの落下速度は離散終端速度 1.58 へ", 1.58f, p.vy, 0.25f)
            assertTrue("横方向はほぼ止まる", abs(p.vx) < 0.5f)
        }

        // 尾: tailN=7 の履歴が積まれている (垂れの光条の元データ)
        engine.particles.forEach { p ->
            assertEquals("履歴は tailN=7 個", 7, p.history.size)
        }
    }
}
