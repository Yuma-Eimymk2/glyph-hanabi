package com.eimymk2.glyphhanabi.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * パーティクル物理エンジン (Phase 1)。純 Kotlin、Android 依存なし。
 *
 * シミュレータ v6 (Simulator_v6_reference.html) の step() と同一の演算順序・
 * パラメータで実装している。描画 (deposit / 残像バッファ) は Phase 2 の
 * FrameCompositor の責務なので、このクラスは粒の状態と明度を更新するだけ。
 *
 * [rng] をコンストラクタ注入にしているのは、テストで Random(シード) を渡せば
 * 毎回同じ結果になる(決定論的にテストできる)ようにするため。
 */
class HanabiEngine(private val rng: Random = Random.Default) {

    companion object {
        /** 更新間隔 80ms = 12.5 FPS 固定 */
        const val DT = 0.08f

        const val GRID = 13
        const val CENTER = 6f

        /** 表示マスク: 中心からの距離² がこれ以下のセルが実LED (137個の近似) */
        const val MASK_R2 = 42

        /** 消滅判定: 距離² がこれを超えた粒は消す (MASK_R2 + 10 = SPEC の 52) */
        const val KILL_R2 = 52f

        /** シミュレータと同じ丸め値を使う (Math.PI * 2 ではなく 6.283) */
        const val TWO_PI = 6.283f

        /** ロケットの上昇速度 (cells/s) と「ため」時間 (s) */
        const val ROCKET_SPEED = 8f
        const val TAME_DURATION = 0.2f
    }

    /** 打ち上げロケット。上昇 → 頂点で「ため」→ burst の3段階 */
    class Rocket(
        var x: Float,
        var y: Float,
        val apex: Float,      // 開花高度 (これより上=小さい y に達したら「ため」へ)
        val type: ShellType,
    ) {
        var wait: Float = 0f  // 「ため」の経過時間
        val isAscending: Boolean get() = y > apex
    }

    /** 千輪用: シミュレーション時刻 at に達したら action を実行する予約 */
    private class Pending(val at: Float, val action: () -> Unit)

    /** ナイアガラ用: 時刻 until まで毎フレーム emit を呼ぶ放出器 */
    private class Emitter(val until: Float, val emit: () -> Unit)

    // 外には読み取り専用の List として見せ、書き換えは内部の Mutable 参照からだけ行う
    // (バッキングプロパティというイディオム)
    private val _particles = mutableListOf<Particle>()
    val particles: List<Particle> get() = _particles

    private val _rockets = mutableListOf<Rocket>()
    val rockets: List<Rocket> get() = _rockets

    private val pending = mutableListOf<Pending>()
    private val emitters = mutableListOf<Emitter>()

    /** シミュレーション累積時刻 (s)。壁時計ではなく dt の積算なので決定論的 */
    var time: Float = 0f
        private set

    val pendingCount: Int get() = pending.size
    val emitterCount: Int get() = emitters.size

    /** ショー終了判定用: 動いているものが何もないか */
    val isIdle: Boolean
        get() = _particles.isEmpty() && _rockets.isEmpty() &&
                pending.isEmpty() && emitters.isEmpty()

    /** 各列でマスク内になる最上段の行番号 (-1 = その列にマスク内セルなし) */
    private val topRowForColumn = IntArray(GRID) { c ->
        (0 until GRID).firstOrNull { r ->
            val dr = r - CENTER.toInt()
            val dc = c - CENTER.toInt()
            dr * dr + dc * dc <= MASK_R2
        } ?: -1
    }

    /** rnd(a, b): a..b の一様乱数。シミュレータの rnd() と同じ */
    private fun rnd(a: Float, b: Float): Float = a + rng.nextFloat() * (b - a)

    // ===== 発火 (打ち上げ or 直接発生) =====

    fun fire(type: ShellType) {
        when (type) {
            // 千輪: 打ち上げなし。小 ring × 6-8 箇所を 0〜250ms の時間差で予約
            ShellType.SENRIN -> {
                val k = 6 + rng.nextInt(3)
                repeat(k) {
                    val x = rnd(2.5f, 9.5f)
                    val y = rnd(2.5f, 7f)
                    pending += Pending(time + rnd(0f, 0.25f)) {
                        ring(x, y, 8, 1.7f, StarParams(
                            gravity = 0.6f, drag = 0.8f, hold = 0.4f,
                            tailN = 0, fadeAt = 0.35f, lifeAt = 0.65f))
                    }
                }
            }
            // ナイアガラ: 3秒間、上端各列から確率 0.85/frame で垂直に放出
            ShellType.NIAGARA -> {
                emitters += Emitter(time + 3f) {
                    if (rng.nextFloat() < 0.85f) {
                        val c = rnd(1f, 12f).toInt()  // 列 1..11
                        val r0 = topRowForColumn[c]   // その列でマスク内の最上段
                        if (r0 >= 0) {
                            _particles += Particle(
                                x = c.toFloat(), y = r0.toFloat(),
                                vx = rnd(-0.04f, 0.04f), vy = rnd(2.0f, 2.8f),
                                // 寿命は sim v6 (fadeAt 1.2 / lifeAt 2.0) から意図的に延長。
                                // 実機で「もう少し下まで落ちてほしい」ため (2026-07-04 Phase 4 追加調整)
                                params = StarParams(
                                    gravity = 0.5f, drag = 0f, hold = 0f,
                                    tailN = 3, fadeAt = 1.5f, lifeAt = 2.8f))
                        }
                    }
                }
            }
            // 割物 (蜂含む): 打ち上げロケット。x = 5〜7.5、開花高度 y = 4.2〜5.6
            else -> _rockets += Rocket(
                x = rnd(5f, 7.5f), y = 12.5f, apex = rnd(4.2f, 5.6f), type = type)
        }
    }

    // ===== ring(): 割物の中核 =====
    // 等間隔配置 + 統一速度が真円の必要条件。
    // 乱数は角度オフセット・角度±0.04rad・速度±3% だけ。

    fun ring(x: Float, y: Float, n: Int, speed: Float, params: StarParams, flags: Int = 0) {
        val offset = rng.nextFloat() * TWO_PI
        for (i in 0 until n) {
            val angle = offset + i * TWO_PI / n + rnd(-0.04f, 0.04f)
            spawn(x, y, angle, speed * (1 + rnd(-0.03f, 0.03f)), params, flags)
        }
    }

    /** 角度と速さから 1 粒生成。テストからも直接使う */
    fun spawn(x: Float, y: Float, angle: Float, speed: Float,
              params: StarParams, flags: Int = 0): Particle {
        val p = Particle(x, y, cos(angle) * speed, sin(angle) * speed, params, flags)
        _particles += p
        return p
    }

    // ===== 玉種定義 (SPEC: 玉種パラメータ確定値) =====

    fun burst(x: Float, y: Float, type: ShellType) {
        when (type) {
            // 菊: 二層の光条 + 散り星6粒
            ShellType.KIKU -> {
                ring(x, y, 28, 4.2f, StarParams(0.8f, 1.0f, 0.4f, 5, 0.9f, 1.5f))
                ring(x, y, 14, 2.4f, StarParams(0.6f, 1.1f, 0.4f, 4, 0.8f, 1.4f))
                val scatter = StarParams(0.8f, 0.9f, 0.4f, 0, 1.0f, 1.5f)
                repeat(6) {
                    spawn(x, y, rng.nextFloat() * TWO_PI, rnd(4.6f, 5.1f), scatter)
                }
            }
            // 牡丹: 純粋な点の真円。寿命 0.75s、歪む前に消える
            ShellType.BOTAN ->
                ring(x, y, 24, 5.0f, StarParams(0.5f, 0.7f, 0.5f, 0, 0.4f, 0.75f))
            // 芯入り: 同心二重円。芯 (内側) が後まで残る
            ShellType.SHIN -> {
                ring(x, y, 24, 5.0f, StarParams(0.6f, 0.9f, 0.5f, 3, 0.55f, 1.0f))
                ring(x, y, 12, 2.0f, StarParams(0.4f, 1.1f, 0.6f, 0, 1.2f, 1.7f))
            }
            // 錦冠: 真円 → 終端速度 g/drag = 1.75 cells/s でゆっくり垂れる
            ShellType.KAMURO ->
                ring(x, y, 22, 4.0f, StarParams(2.1f, 1.2f, 0.55f, 7, 2.4f, 3.6f))
            // 椰子: 腕7本 (等間隔・角度±0.08) + THICK
            ShellType.YASHI -> {
                val base = rng.nextFloat() * TWO_PI
                val arm = StarParams(1.4f, 0.8f, 0.3f, 4, 1.0f, 1.7f)
                for (i in 0 until 7) {
                    spawn(x, y,
                        base + i * TWO_PI / 7 + rnd(-0.08f, 0.08f),
                        3.0f * (1 + rnd(-0.03f, 0.03f)),
                        arm, Particle.THICK)
                }
            }
            // 柳: 錦冠の小型・短命版
            ShellType.YANAGI ->
                ring(x, y, 16, 2.8f, StarParams(1.8f, 1.6f, 0.4f, 6, 1.6f, 2.6f))
            // 蜂: 唯一、消え口を揃えない (fade/life が粒ごと)。JITTER + FLICKER
            ShellType.HACHI -> repeat(10) {
                spawn(x, y, rng.nextFloat() * TWO_PI, rnd(1.2f, 1.8f),
                    StarParams(
                        gravity = 0f, drag = 0f, hold = 0f, tailN = 2,
                        fadeAt = rnd(0.5f, 1.0f), lifeAt = rnd(1.1f, 1.8f)),
                    Particle.JITTER or Particle.FLICKER)
            }
            // 千輪・ナイアガラは burst ではなく fire() で直接発生する
            ShellType.SENRIN, ShellType.NIAGARA -> fire(type)
        }
    }

    // ===== メインループ (SPEC: 演算順序のステップ 2, 4, 5) =====
    // ステップ 1 (残像減衰) と 6 (deposit → setMatrixFrame) は FrameCompositor / Renderer の仕事

    fun update(dt: Float = DT) {
        time += dt

        // 2. pending / emitter 処理
        pending.removeAll { p ->
            if (time >= p.at) { p.action(); true } else false
        }
        emitters.removeAll { e ->
            if (time < e.until) { e.emit(); false } else true
        }

        // (3. ShowDirector 更新は Phase 2。Service 側が fire() を呼ぶ)

        // 4. Rocket: 8 cells/s 上昇 → 頂点で 0.2s ため → burst
        _rockets.removeAll { rk ->
            if (rk.isAscending) {
                rk.y -= ROCKET_SPEED * dt
                false
            } else {
                rk.wait += dt
                if (rk.wait >= TAME_DURATION) {
                    burst(rk.x, rk.y, rk.type)
                    true
                } else false
            }
        }

        // 5. 粒の更新 (false を返した粒 = 消滅)
        _particles.removeAll { p -> !stepParticle(p, dt) }
    }

    /**
     * 粒 1 個の 1 フレーム分の更新。シミュレータの particles.filter 内と同一順序:
     * age → エンベロープ判定 → FLICKER → 履歴 push → JITTER
     * → hold 重力判定 → 等方抵抗 → 位置更新 → マスク外判定
     */
    private fun stepParticle(p: Particle, dt: Float): Boolean {
        p.age += dt

        // 明度エンベロープ。0 以下なら消え口 (burst 内で lifeAt 共有 → 一斉消灯)
        var b = p.envelopeAt(p.age)
        if (b <= 0f) return false
        if (p.hasFlag(Particle.FLICKER)) b *= rnd(0.6f, 1f)
        p.brightness = b

        // 尾: 移動「前」の位置を履歴の先頭に積む
        if (p.params.tailN > 0) {
            p.history.addFirst(p.x to p.y)
            while (p.history.size > p.params.tailN) p.history.removeLast()
        }

        // JITTER (蜂のみ): 速度を ±80° (±1.4rad) ランダム回転 + ノイズ、速度上限 2.2
        if (p.hasFlag(Particle.JITTER)) {
            val a = rnd(-1.4f, 1.4f)
            val cosA = cos(a)
            val sinA = sin(a)
            val rotatedVx = p.vx * cosA - p.vy * sinA
            p.vy = p.vx * sinA + p.vy * cosA
            p.vx = rotatedVx
            p.vx += rnd(-0.6f, 0.6f)
            p.vy += rnd(-0.6f, 0.6f)
            val sp = sqrt(p.vx * p.vx + p.vy * p.vy)
            if (sp > 2.2f) {
                p.vx *= 2.2f / sp
                p.vy *= 2.2f / sp
            }
        }

        // hold 中は重力ゼロ (真円保持)、以降は g で落下
        val g = if (p.age < p.params.hold) 0f else p.params.gravity
        p.vy += g * dt

        // 等方空気抵抗: vx, vy 両方に適用 (水平のみだと円が縦長に歪む)
        val damping = 1f - p.params.drag * dt
        p.vx *= damping
        p.vy *= damping

        p.x += p.vx * dt
        p.y += p.vy * dt

        // 円形マスク外に出た粒は消滅
        val dr = p.y - CENTER
        val dc = p.x - CENTER
        return dr * dr + dc * dc <= KILL_R2
    }
}
