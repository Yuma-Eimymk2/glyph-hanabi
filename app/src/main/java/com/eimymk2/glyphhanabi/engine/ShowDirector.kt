package com.eimymk2.glyphhanabi.engine

import kotlin.random.Random

/**
 * 花火大会のシーケンス制御 (Phase 2)。純 Kotlin、Android 依存なし。
 *
 * ```
 * 0s      ショー開始
 * 0-44s   通常部: 1.8〜3.5 秒間隔で単発打ち上げ
 * 44-52s  フィナーレ: 0.6〜1.1 秒間隔で連発
 * 52-54s  間 (余韻)
 * 54s     ナイアガラ (3秒間放出)
 * 62s     終演 (全粒消滅は engine.isIdle で Service 側が確認する)
 * ```
 *
 * 使い方 (Service のメインループ、1 フレームごと):
 *   director.update(dt)   // 時間が来ていれば engine.fire() を呼ぶ
 *   engine.update(dt)
 *   compositor.compose(engine)
 *
 * シミュレータ v6 では ShowDirector 処理が pending/emitter 処理の「後」だが、
 * この分割では engine.update() の「前」に来る。発火がシミュレータ比で最大
 * 1 フレーム (80ms) 早く見えることがあるだけで、挙動は同一。
 */
class ShowDirector(
    private val engine: HanabiEngine,
    private val rng: Random = Random.Default,
) {
    companion object {
        const val SHOW_DURATION = 62f   // 終演 (これが主たる時間制御)
        const val LAUNCH_END = 52f      // 打ち上げはここまで。52-54s は余韻の間
        const val FINALE_START = 44f    // ここから連発
        const val NIAGARA_AT = 54f      // 締めのナイアガラ

        /**
         * 玉種抽選テーブル。エントリ数 = 重みそのもの:
         * 菊2 / 牡丹2 / 芯入り2 / 千輪2 / 柳2 / 椰子2 / 蜂1 / 錦冠1
         * (ナイアガラは締め専用なので入れない)
         */
        private val LOTTERY = listOf(
            ShellType.KIKU, ShellType.KIKU,
            ShellType.BOTAN, ShellType.BOTAN,
            ShellType.SHIN, ShellType.SHIN,
            ShellType.SENRIN, ShellType.SENRIN,
            ShellType.YANAGI, ShellType.YANAGI,
            ShellType.YASHI, ShellType.YASHI,
            ShellType.HACHI,
            ShellType.KAMURO,
        )
    }

    /** いつ何を打ったかの記録。テストとデバッグ用 */
    data class FiredEvent(val at: Float, val type: ShellType)

    /** ショー開始からの経過時間 (s)。dt の積算なので決定論的 */
    var elapsed: Float = 0f
        private set

    val isFinished: Boolean get() = elapsed >= SHOW_DURATION

    /** フィナーレ区間かどうか (表示やデバッグ用) */
    val isFinale: Boolean get() = elapsed > FINALE_START && elapsed < LAUNCH_END

    private var nextLaunchAt = 0f          // 次の打ち上げ予定時刻 (0 = 開始直後に1発目)
    private var lastType: ShellType? = null
    private var niagaraFired = false

    private val _history = mutableListOf<FiredEvent>()
    val history: List<FiredEvent> get() = _history

    fun update(dt: Float = HanabiEngine.DT) {
        if (isFinished) return
        elapsed += dt
        when {
            // 終演。以後は何も打たない (消灯待ちは Service の仕事)
            elapsed >= SHOW_DURATION -> Unit
            // 締めのナイアガラは 1 回だけ
            elapsed >= NIAGARA_AT && !niagaraFired -> {
                niagaraFired = true
                fire(ShellType.NIAGARA)
            }
            // 通常部 / フィナーレの打ち上げ
            elapsed < LAUNCH_END && elapsed >= nextLaunchAt -> {
                val finale = elapsed > FINALE_START
                fire(pickType())
                nextLaunchAt = elapsed + if (finale) rnd(0.6f, 1.1f) else rnd(1.8f, 3.5f)
            }
        }
    }

    private fun fire(type: ShellType) {
        _history += FiredEvent(elapsed, type)
        engine.fire(type)
    }

    /** 重み付き抽選。直前と同じ玉種が出たら引き直す */
    private fun pickType(): ShellType {
        var type: ShellType
        do {
            type = LOTTERY[rng.nextInt(LOTTERY.size)]
        } while (type == lastType)
        lastType = type
        return type
    }

    private fun rnd(a: Float, b: Float): Float = a + rng.nextFloat() * (b - a)
}
