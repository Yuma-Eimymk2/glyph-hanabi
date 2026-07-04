package com.eimymk2.glyphhanabi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.eimymk2.glyphhanabi.engine.FrameCompositor
import com.eimymk2.glyphhanabi.engine.HanabiEngine
import com.eimymk2.glyphhanabi.engine.ShellType
import com.eimymk2.glyphhanabi.engine.ShowDirector
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glyph Hanabi の Toy Service。
 *
 * Flip to Glyph で onBind → SDK 接続完了 → ショー開始。
 *  - 充電中は完全停止 (何も表示しない)
 *  - ショーは 62 秒 + 全粒消滅 + 残像フェードアウトで静かに終わる
 *  - **画面が消えた瞬間 (ACTION_SCREEN_OFF) に上演開始** (2026-07-05 決定)。
 *    電源ボタン・伏せる・無操作タイムアウトのどれでも画面が消えれば 1 回上がる。
 *    「いつ上がるか分からない」問題の解決策: 電源ボタンを押せば必ずその瞬間に始まる
 *  - **画面を点けたら即終演** (ACTION_SCREEN_ON で消灯)。手に取ったら止まる
 *  - EVENT_AOD は再演トリガーとしては使わない (クールダウン方式は 2026-07-05 廃止。
 *    1 画面OFF = 1 ショーなので連続暴発の心配がなく、クールダウンも不要)
 *
 * メインループ (80ms 固定、SPEC の演算順序):
 *   director.update() → engine.update() → compositor.compose() → 13×13 → 25×25 → setMatrixFrame()
 * delay() のドリフトを避けるため、次フレーム時刻は開始時刻からの絶対時刻で計算する
 * (Doze の影響は Phase 4 で実機確認)。
 */
class GlyphHanabiService : GlyphMatrixService("Glyph-Hanabi") {

    private var showScope: CoroutineScope? = null
    private var showJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiverRegistered = false

    /**
     * 画面 ON/OFF を拾うレシーバー。SCREEN_ON/OFF は Manifest 登録では届かない
     * 種類のブロードキャストなので、Service 生存中だけ動的登録する
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff(context.applicationContext)
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        Log.d(TAG, "performOnServiceConnected")

        // bind のたびに新しいスコープを作る (cancel 済みスコープの再利用を避ける)
        showScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // 画面 OFF = 開演 / 画面 ON = 終演 のトリガーを購読する
        context.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            Context.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true

        // bind 直後 (Toy に選んだ直後) は必ず 1 回上演。動作確認を兼ねる
        startShow(context, glyphMatrixManager)
    }

    /**
     * EVENT_AOD (毎分 + 伏せた瞬間に届く) は再演トリガーとしては使わない (2026-07-05)。
     * どんなタイミングで届くかの実態調査のためログだけ残す (基底クラス側でも出るが
     * こちらは Service が生きている証跡として)
     */
    override fun onAodEvent() {
        Log.d(TAG, "EVENT_AOD (info only, not a trigger)")
    }

    /** 画面が消えた瞬間 = 開演。電源ボタン・伏せ・タイムアウトのどれでも来る */
    private fun onScreenOff(context: Context) {
        val gmm = glyphMatrixManager ?: return
        if (showJob?.isActive == true) {
            Log.d(TAG, "screen off: show already running -> ignore")
            return
        }
        Log.d(TAG, "screen off -> starting show")
        startShow(context, gmm)
    }

    /** 画面が点いた瞬間 = 終演。上演中なら止めて消灯する */
    private fun onScreenOn() {
        val job = showJob ?: return
        if (!job.isActive) return
        val gmm = glyphMatrixManager ?: return
        val scope = showScope ?: return
        Log.d(TAG, "screen on -> stopping show")
        scope.launch {
            // ショー本体の終了を待ってから消灯フレームを送る
            // (キャンセルと最終フレーム描画のレースを避ける)
            job.cancelAndJoin()
            pushFrame(gmm, IntArray(GlyphRenderer.OUTPUT_FRAME_SIZE))
        }
    }

    /** ショーを 1 本立ち上げる (bind 直後と画面 OFF の共通入口) */
    private fun startShow(context: Context, glyphMatrixManager: GlyphMatrixManager) {
        val scope = showScope ?: return
        showJob = scope.launch {
            if (isCharging(context)) {
                // SPEC: 充電中は完全停止
                Log.d(TAG, "charging -> stay dark")
                pushFrame(glyphMatrixManager, IntArray(GlyphRenderer.OUTPUT_FRAME_SIZE))
                return@launch
            }
            // ショー中だけ CPU を起こしておく (Doze で delay() が遅れて
            // 花火がかくかくする対策)。タイムアウト付きなので release 漏れでも安全
            acquireWakeLock(context)
            try {
                runShow(context, glyphMatrixManager)
            } finally {
                releaseWakeLock()
            }
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        Log.d(TAG, "performOnServiceDisconnected: cancelling show")
        if (screenReceiverRegistered) {
            context.unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        showScope?.cancel()
        showScope = null
        showJob = null
        releaseWakeLock()
    }

    private fun acquireWakeLock(context: Context) {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlyphHanabi:show").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        Log.d(TAG, "wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private suspend fun runShow(context: Context, gmm: GlyphMatrixManager) {
        val engine = HanabiEngine()
        val director = ShowDirector(engine)
        // 満開時は LED 全開 (Phase 4 実機調整: 暫定 153 では暗かった)。
        // ガンマも 0.8 → 0.6 に下げて中間調 (尾・残像・開花直後) を底上げ
        // (明るい環境で目立たない対策。ピーク 255 はハード上限なのでこれが最後のノブ)
        val compositor = FrameCompositor(gamma = DISPLAY_GAMMA, maxLevel = PEAK_BRIGHTNESS)

        if (TEST_SINGLE_BOTAN) {
            // Phase 3 の実機確認モード: ショーは回さず牡丹を 1 発だけ打つ
            Log.d(TAG, "TEST_SINGLE_BOTAN: firing one BOTAN")
            engine.fire(ShellType.BOTAN)
        }

        var anchorMs = SystemClock.uptimeMillis()
        var frame = 0

        while (currentCoroutineContext().isActive) {
            // 1 フレーム分進める (SPEC の演算順序)
            if (!TEST_SINGLE_BOTAN) director.update()
            engine.update()
            compositor.compose(engine)
            pushFrame(gmm, GlyphRenderer.render(compositor.snapshot()))

            // 終了判定: ショー終了 (62s) + 全パーティクル消滅
            val showDone =
                if (TEST_SINGLE_BOTAN) engine.isIdle
                else director.isFinished && engine.isIdle
            if (showDone) break

            // 充電が始まったら完全停止 (2 秒ごとにチェック)
            if (frame % CHARGE_CHECK_INTERVAL_FRAMES == 0 && isCharging(context)) {
                Log.d(TAG, "charging started -> abort show")
                break
            }

            frame++
            anchorMs = waitForFrame(anchorMs, frame)
        }

        // 全粒消滅後も残像バッファを数フレーム減衰させ、静かに消灯する
        repeat(FADE_OUT_FRAMES) {
            if (!currentCoroutineContext().isActive) return
            compositor.compose(engine)  // 粒ゼロなので減衰だけが走る
            pushFrame(gmm, GlyphRenderer.render(compositor.snapshot()))
            frame++
            anchorMs = waitForFrame(anchorMs, frame)
        }
        pushFrame(gmm, IntArray(GlyphRenderer.OUTPUT_FRAME_SIZE))
        Log.d(TAG, "show finished (${frame} frames, ${frame * FRAME_INTERVAL_MS / 1000}s)")
        // ショー終了後はこのコルーチンが終わるだけ。
        // 次の上演は「次に画面が消えた瞬間」(onScreenOff)
    }

    /**
     * setMatrixFrame は Glyph Life と同様にメインスレッドから呼ぶ。
     * かくかく調査用: 呼び出しに時間がかかったフレームをログに残す
     * (遅いのが delay() 側か setMatrixFrame 側かの切り分け。2026-07-04)
     */
    private suspend fun pushFrame(gmm: GlyphMatrixManager, frame: IntArray) {
        val startMs = SystemClock.uptimeMillis()
        withContext(Dispatchers.Main) {
            gmm.setMatrixFrame(frame)
        }
        val tookMs = SystemClock.uptimeMillis() - startMs
        if (tookMs > SLOW_PUSH_LOG_MS) {
            Log.d(TAG, "slow push: setMatrixFrame took ${tookMs}ms")
        }
    }

    /**
     * 基準時刻からの絶対時刻で次フレームまで待つ (delay の累積ドリフト防止)。
     * 何かの理由で大きく遅れていたら、遅れを早回しで取り戻そうとせず
     * 基準時刻を今に引き直して等速のまま続ける (かくかく対策の保険)。
     *
     * @return 次フレーム以降に使う基準時刻
     */
    private suspend fun waitForFrame(anchorMs: Long, frame: Int): Long {
        val nextMs = anchorMs + frame.toLong() * FRAME_INTERVAL_MS
        val waitMs = nextMs - SystemClock.uptimeMillis()
        if (waitMs > 0) {
            delay(waitMs)
            return anchorMs
        }
        // かくかく調査用: 半フレーム以上の遅れは全部記録する (2026-07-04)
        if (waitMs < -SLOW_FRAME_LOG_MS) {
            Log.d(TAG, "late frame $frame: behind ${-waitMs}ms")
        }
        if (waitMs < -RESYNC_THRESHOLD_MS) {
            Log.d(TAG, "frame pacing resync (behind ${-waitMs}ms)")
            return SystemClock.uptimeMillis() - frame.toLong() * FRAME_INTERVAL_MS
        }
        return anchorMs
    }

    private fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService(BatteryManager::class.java)
        return bm?.isCharging == true
    }

    private companion object {
        private const val TAG = "GlyphHanabiService"

        /** 更新間隔 80ms = 12.5 FPS (エンジンの dt=0.08s と対応) */
        private const val FRAME_INTERVAL_MS = 80L

        /** 充電開始チェックの間隔 (25 フレーム = 2 秒) */
        private const val CHARGE_CHECK_INTERVAL_FRAMES = 25

        /** 終演後のフェードアウト: 0.58^12 ≈ 0.001 で事実上消灯 */
        private const val FADE_OUT_FRAMES = 12

        /** 満開時の LED 明度 (0-255)。Phase 4 実機調整: 153 (60%) → 255 (100%) */
        private const val PEAK_BRIGHTNESS = 255

        /**
         * 表示ガンマ。0.8 (sim v6) → 0.6: 中間明度が持ち上がり体感が明るくなる。
         * 例: 明度 50% の粒の表示が 57% → 66%。トレードオフはコントラスト低下
         * (錦冠の淡い垂れとの相性は実機で要確認。2026-07-04 明所対策)
         */
        private const val DISPLAY_GAMMA = 0.6f

        /** ウェイクロックの保険タイムアウト: ショー 62s + フェード + 余裕 */
        private const val WAKE_LOCK_TIMEOUT_MS = 75_000L

        /** これ以上遅れたら基準時刻を引き直す (3 フレーム分) */
        private const val RESYNC_THRESHOLD_MS = 240L

        /** かくかく調査ログの閾値: 半フレーム (40ms) 以上遅れたら記録 */
        private const val SLOW_FRAME_LOG_MS = 40L

        /** かくかく調査ログの閾値: setMatrixFrame 呼び出しがこれ以上かかったら記録 */
        private const val SLOW_PUSH_LOG_MS = 30L

        /**
         * ★ Phase 3 実機確認モード: true の間は牡丹 1 発だけ打って終わる。
         * 2026-07-03 実機確認済み (見え方良好) → false でフルショー (62 秒)。
         */
        private const val TEST_SINGLE_BOTAN = false
    }
}
