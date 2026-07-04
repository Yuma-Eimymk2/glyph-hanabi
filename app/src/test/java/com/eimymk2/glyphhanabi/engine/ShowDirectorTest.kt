package com.eimymk2.glyphhanabi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * SPEC「単体テストの観点」:
 * - ShowDirector: 62 秒で必ず終了、同一玉種が連続しないこと
 * (+ 打ち上げ間隔・ナイアガラ締め・全体シーケンスの検証)
 */
class ShowDirectorTest {

    /** director + engine を 1 フレーム分回す小さいヘルパー */
    private fun step(director: ShowDirector, engine: HanabiEngine) {
        director.update()
        engine.update()
    }

    @Test
    fun `ショーは 62 秒で必ず終了し、その後まもなく完全アイドルになる`() {
        for (seed in 1..5) {
            val engine = HanabiEngine(Random(seed))
            val director = ShowDirector(engine, Random(seed + 1000))

            // 62.4s (780 フレーム) 回せば終演しているはず
            repeat(780) { step(director, engine) }
            assertTrue("seed=$seed: 62 秒で終演", director.isFinished)

            // 最後の粒 (54s のナイアガラ: 放出 3s + 寿命 2.8s ≈ 59.8s) も消えて
            // 完全アイドルへ。余裕をみて 64.8s まで待つ
            repeat(30) { step(director, engine) }
            assertTrue("seed=$seed: 終演後は完全アイドル", engine.isIdle)

            // 終演後は何も打たれていない
            assertTrue("seed=$seed: 62s 以降の発火なし",
                director.history.all { it.at < 62f })
        }
    }

    @Test
    fun `同一玉種が連続しない`() {
        for (seed in 1..20) {
            val engine = HanabiEngine(Random(seed))
            val director = ShowDirector(engine, Random(seed + 2000))
            repeat(780) { step(director, engine) }

            // 抽選対象の並び (締めのナイアガラは抽選外なので除く)
            val picks = director.history.map { it.type }.filter { it != ShellType.NIAGARA }
            assertTrue("seed=$seed: 打ち上げが複数回ある", picks.size > 10)
            picks.zipWithNext().forEach { (a, b) ->
                assertTrue("seed=$seed: $a が連続した", a != b)
            }
        }
    }

    @Test
    fun `打ち上げ間隔は通常部 1,8〜3,5s、フィナーレ 0,6〜1,1s`() {
        for (seed in 1..10) {
            val engine = HanabiEngine(Random(seed))
            val director = ShowDirector(engine, Random(seed + 3000))
            repeat(780) { step(director, engine) }

            val launches = director.history.filter { it.type != ShellType.NIAGARA }
            launches.zipWithNext().forEach { (a, b) ->
                val gap = b.at - a.at
                // 発火はフレーム境界に量子化されるので +0.08s (1 フレーム) まで許容
                if (a.at <= ShowDirector.FINALE_START) {
                    assertTrue("seed=$seed: 通常部の間隔 $gap (at=${a.at})",
                        gap in 1.79f..3.60f)
                } else {
                    assertTrue("seed=$seed: フィナーレの間隔 $gap (at=${a.at})",
                        gap in 0.59f..1.20f)
                }
            }
        }
    }

    @Test
    fun `ナイアガラは 54 秒に 1 回だけ、52〜54 秒は間 (発火なし)`() {
        for (seed in 1..10) {
            val engine = HanabiEngine(Random(seed))
            val director = ShowDirector(engine, Random(seed + 4000))
            repeat(780) { step(director, engine) }

            val niagara = director.history.filter { it.type == ShellType.NIAGARA }
            assertEquals("seed=$seed: ナイアガラは 1 回だけ", 1, niagara.size)
            assertEquals("seed=$seed: 54s ちょうどの次フレームで発火",
                54f, niagara[0].at, 0.1f)

            // 52-54s の余韻区間には何も打たれない
            val inRest = director.history.filter { it.at > 52.1f && it.at < 53.99f }
            assertTrue("seed=$seed: 余韻区間は発火なし", inRest.isEmpty())
        }
    }

    @Test
    fun `十分な試行で全玉種が登場する (重み付き抽選の生存確認)`() {
        // 固定シード 5 本分のショーを合算すれば、重み 1 の蜂・錦冠も必ず出る
        val seen = mutableSetOf<ShellType>()
        for (seed in 1..5) {
            val engine = HanabiEngine(Random(seed))
            val director = ShowDirector(engine, Random(seed + 5000))
            repeat(780) { step(director, engine) }
            seen += director.history.map { it.type }
        }
        assertEquals("全 9 玉種 (ナイアガラ含む) が登場",
            ShellType.entries.toSet(), seen)
    }

    @Test
    fun `フルショー統合で 62 秒回してもバッファは常に 0〜1、量子化は 0〜153 に収まる`() {
        for (seed in 1..3) {
            val engine = HanabiEngine(Random(seed))
            val director = ShowDirector(engine, Random(seed + 6000))
            val compositor = FrameCompositor()

            repeat(810) { frame ->
                step(director, engine)
                compositor.compose(engine)

                // deposit は max 合成 (加算ではない) なのでバッファは 1 を超えない
                for (r in 0 until FrameCompositor.SIZE) {
                    for (c in 0 until FrameCompositor.SIZE) {
                        val v = compositor.brightnessAt(r, c)
                        assertTrue("seed=$seed frame=$frame ($r,$c)=$v",
                            v in 0f..1f)
                    }
                }
                if (frame % 100 == 0) {
                    compositor.snapshot().forEach { level ->
                        assertTrue("量子化値 $level", level in 0..153)
                    }
                }
            }

            // 終演 + アイドル後、残像も 3 秒 (0.58^37) で事実上消灯している
            assertTrue(engine.isIdle)
            repeat(38) { compositor.compose(engine) }
            for (r in 0 until FrameCompositor.SIZE) {
                for (c in 0 until FrameCompositor.SIZE) {
                    assertTrue("残像が消えている",
                        compositor.brightnessAt(r, c) < 0.01f)
                }
            }
        }
    }
}
