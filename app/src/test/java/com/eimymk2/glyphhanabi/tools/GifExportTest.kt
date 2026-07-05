package com.eimymk2.glyphhanabi.tools

import com.eimymk2.glyphhanabi.engine.FrameCompositor
import com.eimymk2.glyphhanabi.engine.HanabiEngine
import com.eimymk2.glyphhanabi.engine.ShowDirector
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream
import kotlin.random.Random

/**
 * README 用デモ GIF の書き出しツール (テストの形をした開発ツール)。
 *
 * 花火エンジンは純 Kotlin なので、実機なしで JVM 上でショーを丸ごと走らせて
 * 1 フレームずつ画像化できる。実機と同じパイプライン
 * (ShowDirector → HanabiEngine → FrameCompositor(gamma=0.6, max=255)) を通し、
 * 13×13 の円形マスク (実 LED 137 個) を「黒地に丸ドット」で描く。
 *
 * 普段の `./gradlew test` では動かない (環境変数ガードで skipped 扱い)。
 * 使い方 (Git Bash):
 *   HANABI_GIF=/c/path/to/demo.gif HANABI_GIF_SEED=7 ./gradlew :app:testDebugUnitTest --tests "*GifExport*"
 */
class GifExportTest {

    companion object {
        private const val GRID = 13
        private const val CELL = 26           // 1 セルのピクセルサイズ
        private const val GAP = 4             // セル間のすき間 (実機同様せまく)
        private const val PAD = 18            // 外周の余白
        private const val IMG = GRID * CELL + PAD * 2
        private const val DELAY_CS = 8        // フレーム間隔 8/100 秒 = 実機と同じ 80ms
        private const val TAIL_BLACK_FRAMES = 12  // ループ前の暗転 (約 1 秒の「間」)
        private const val MAX_FRAMES = 1000   // 暴走ガード (通常 ~790 フレームで終わる)
    }

    /** seed 選び用: HANABI_GIF_SCAN=1 で seed 1..40 の打ち上げ履歴だけを一覧表示 */
    @Test
    fun `seed scan (only when HANABI_GIF_SCAN env is set)`() {
        assumeTrue("HANABI_GIF_SCAN 未設定のためスキップ",
            !System.getenv("HANABI_GIF_SCAN").isNullOrBlank())
        for (seed in 1..40) {
            val engine = HanabiEngine(Random(seed))
            val director = ShowDirector(engine, Random(seed + 1000))
            repeat(810) { director.update(); engine.update() }
            val types = director.history.map { it.type }
            val missing = com.eimymk2.glyphhanabi.engine.ShellType.entries - types.toSet()
            println("seed=%2d shots=%2d first=%-6s missing=%s".format(
                seed, types.size, types.first(), missing.joinToString(",")))
        }
    }

    /**
     * Playground プレビュー用: 盤面中心 (6,6) で直接開花させ、全フレームを 512×512 PNG に。
     * ショー本編と違い打ち上げ位置の乱数ずれがないので、看板向きの対称な開花が撮れる
     */
    @Test
    fun `posed preview export (only when HANABI_POSE_DIR env is set)`() {
        val dir = System.getenv("HANABI_POSE_DIR")
        assumeTrue("HANABI_POSE_DIR 未設定のためスキップ", !dir.isNullOrBlank())
        val seed = System.getenv("HANABI_GIF_SEED")?.toIntOrNull() ?: 1

        for (type in listOf(com.eimymk2.glyphhanabi.engine.ShellType.KIKU,
                            com.eimymk2.glyphhanabi.engine.ShellType.BOTAN,
                            com.eimymk2.glyphhanabi.engine.ShellType.KAMURO)) {
            val engine = HanabiEngine(Random(seed))
            val compositor = FrameCompositor(gamma = 0.6f, maxLevel = 255)
            engine.burst(HanabiEngine.CENTER, HanabiEngine.CENTER, type)
            var frame = 0
            while (frame < 60 && !engine.isIdle) {
                engine.update()
                compositor.compose(engine)
                ImageIO.write(
                    renderFrame(compositor.snapshot(), imgPx = 512, cellPx = 36, gapPx = 6, padPx = 22),
                    "png", File(File(dir!!).apply { mkdirs() }, "%s%02d.png".format(type, frame)))
                frame++
            }
        }
    }

    /**
     * Playground プレビュー用 Lottie アニメ (JSON) の書き出し。
     * 仕様: 512×512、30fps、約 5 秒、1MB 未満。
     *
     * ショー冒頭の約 5 秒 (seed は HANABI_GIF_SEED) をエンジンで走らせ、
     * セル (137 個) ごとに「不透明度のホールドキーフレーム」として焼き込む。
     * 実機も 80ms 刻みのステップ更新なので、ホールド (補間なし) が実物に一番忠実。
     * エンジン 1 フレーム (80ms) = Lottie 2.4 フレーム (30fps)。
     */
    @Test
    fun `lottie preview export (only when HANABI_LOTTIE env is set)`() {
        val outPath = System.getenv("HANABI_LOTTIE")
        assumeTrue("HANABI_LOTTIE 未設定のためスキップ", !outPath.isNullOrBlank())
        val seed = System.getenv("HANABI_GIF_SEED")?.toIntOrNull() ?: 1

        val engine = HanabiEngine(Random(seed))
        val director = ShowDirector(engine, Random(seed + 1000))
        val compositor = FrameCompositor(gamma = 0.6f, maxLevel = 255)

        // エンジン 63 フレーム = 5.04 秒分の明度をセルごとに記録
        val engineFrames = 63
        val series = Array(GRID * GRID) { IntArray(engineFrames) }
        repeat(engineFrames) { f ->
            director.update()
            engine.update()
            compositor.compose(engine)
            val levels = compositor.snapshot()
            for (i in levels.indices) series[i][f] = levels[i]
        }

        val totalFrames = 150  // 5 秒 × 30fps
        val dimPct = 6         // 消灯セルの明度 (GIF の #0f0f0f 相当)

        // セルごとの Lottie レイヤーを組み立てる (円形マスク内のみ = 実 LED 137 個)
        val center = HanabiEngine.CENTER.toInt()
        val layers = StringBuilder()
        var ind = 1
        for (r in 0 until GRID) {
            for (c in 0 until GRID) {
                val dr = r - center
                val dc = c - center
                if (dr * dr + dc * dc > HanabiEngine.MASK_R2) continue
                val cx = 22 + c * 36 + 18   // プレビュー PNG と同じ配置 (pad22, cell36)
                val cy = 22 + r * 36 + 18

                // 明度 (0-255) → 不透明度 (%)。0 のときは消灯セルの dim 表示
                val pct = series[r * GRID + c].map { v ->
                    maxOf(dimPct, Math.round(v * 100f / 255f)) }
                val opacity = if (pct.all { it == pct[0] }) {
                    """{"a":0,"k":${pct[0]}}"""
                } else {
                    // 値が変わった瞬間だけホールドキーフレームを打つ
                    val kf = StringBuilder("""{"t":0,"s":[${pct[0]}],"h":1}""")
                    for (f in 1 until engineFrames) {
                        if (pct[f] != pct[f - 1]) {
                            kf.append(""",{"t":${Math.round(f * 2.4f)},"s":[${pct[f]}],"h":1}""")
                        }
                    }
                    """{"a":1,"k":[$kf]}"""
                }

                if (ind > 1) layers.append(",")
                layers.append("""{"ddd":0,"ty":4,"ind":$ind,"ip":0,"op":$totalFrames,"st":0,""" +
                    """"ks":{"o":$opacity,"r":{"a":0,"k":0},"p":{"a":0,"k":[$cx,$cy,0]},""" +
                    """"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}},""" +
                    """"shapes":[{"ty":"gr","it":[""" +
                    """{"ty":"rc","d":1,"s":{"a":0,"k":[30,30]},"p":{"a":0,"k":[0,0]},"r":{"a":0,"k":2}},""" +
                    """{"ty":"fl","c":{"a":0,"k":[1,1,1,1]},"o":{"a":0,"k":100},"r":1},""" +
                    """{"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},""" +
                    """"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}}]}]}""")
                ind++
            }
        }
        // 黒背景 (レイヤー配列の末尾 = 最背面)
        layers.append(""",{"ddd":0,"ty":1,"ind":$ind,"ip":0,"op":$totalFrames,"st":0,""" +
            """"ks":{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[256,256,0]},""" +
            """"a":{"a":0,"k":[256,256,0]},"s":{"a":0,"k":[100,100,100]}},""" +
            """"sw":512,"sh":512,"sc":"#000000"}""")

        val json = """{"v":"5.7.4","fr":30,"ip":0,"op":$totalFrames,"w":512,"h":512,""" +
            """"nm":"Glyph Hanabi","ddd":0,"assets":[],"layers":[$layers]}"""
        File(outPath!!).apply { parentFile?.mkdirs() }.writeText(json)

        println("=== Lottie export done ===")
        println("seed=$seed engineFrames=$engineFrames -> ${totalFrames / 30f}s @30fps")
        println("size=${File(outPath).length()} bytes -> $outPath")
    }

    @Test
    fun `demo GIF export (only when HANABI_GIF env is set)`() {
        val outPath = System.getenv("HANABI_GIF")
        assumeTrue("HANABI_GIF 未設定のためスキップ", !outPath.isNullOrBlank())

        val seed = System.getenv("HANABI_GIF_SEED")?.toIntOrNull() ?: 1

        // 実機の Service と同じ構成・同じ表示パラメータ
        val engine = HanabiEngine(Random(seed))
        val director = ShowDirector(engine, Random(seed + 1000))
        val compositor = FrameCompositor(gamma = 0.6f, maxLevel = 255)

        // フレームを溜め込むとヒープが溢れる (790 枚 × 374² RGB ≈ 440MB) ので、
        // 生成しながら 1 枚ずつ GIF に逐次書き込むストリーミング方式にする
        var frameCount = 0
        writeGif(File(outPath!!)) { emit ->
            // 目視チェック用: HANABI_GIF_PNG_DIR を設定すると 25 フレーム (2 秒) ごとに PNG も保存
            val pngDir = System.getenv("HANABI_GIF_PNG_DIR")?.takeIf { it.isNotBlank() }
                ?.let { File(it).apply { mkdirs() } }
            // Playground プレビュー選び用: 全フレームを 512×512 PNG で保存
            val previewDir = System.getenv("HANABI_PREVIEW_DIR")?.takeIf { it.isNotBlank() }
                ?.let { File(it).apply { mkdirs() } }

            var steps = 0
            // 終演 + 全粒消滅 + 残像が完全に消えるまで回す
            while (steps < MAX_FRAMES) {
                director.update()
                engine.update()
                compositor.compose(engine)
                val levels = compositor.snapshot()
                val frame = renderFrame(levels)
                emit(frame)
                if (pngDir != null && steps % 25 == 0) {
                    ImageIO.write(frame, "png", File(pngDir, "frame%04d.png".format(steps)))
                }
                if (previewDir != null) {
                    // 512 = 余白 22 + セル 36×13 + 余白 22。すき間は GIF と同比率の 6px
                    ImageIO.write(
                        renderFrame(levels, imgPx = 512, cellPx = 36, gapPx = 6, padPx = 22),
                        "png", File(previewDir, "preview%04d.png".format(steps)))
                }
                steps++
                if (director.isFinished && engine.isIdle && levels.all { it == 0 }) break
            }
            // ループの継ぎ目に「間」を入れる (本物の花火大会の余韻)
            repeat(TAIL_BLACK_FRAMES) { emit(renderFrame(IntArray(GRID * GRID))) }
            frameCount = steps + TAIL_BLACK_FRAMES
        }

        println("=== GIF export done ===")
        println("seed=$seed frames=$frameCount (${"%.1f".format(frameCount * 0.08)}s)")
        println("size=${File(outPath).length()} bytes -> $outPath")
        println("--- fired history ---")
        director.history.forEach { println("%5.1fs  %s".format(it.at, it.type)) }
    }

    /**
     * 13×13 明度フレーム (0-255) を画像にする。
     * 実機の Glyph Matrix に合わせて、セルは四角形・セル間のすき間は狭く描く。
     * サイズはパラメータ化してあり、GIF 用 (374px) と Playground プレビュー用 (512px) で共用
     */
    private fun renderFrame(
        levels: IntArray,
        imgPx: Int = IMG, cellPx: Int = CELL, gapPx: Int = GAP,
        padPx: Int = (IMG - GRID * CELL) / 2,
    ): BufferedImage {
        val img = BufferedImage(imgPx, imgPx, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fillRect(0, 0, imgPx, imgPx)

        val half = (cellPx - gapPx) / 2  // 四角セルの半辺 (すき間 gapPx を空ける)
        forEachLed(levels, cellPx, padPx) { cx, cy, v ->
            // 消灯セルもうっすら見せて「盤面」の存在を伝える
            g.color = if (v > 0) Color(v, v, v) else Color(15, 15, 15)
            g.fillRect(cx - half, cy - half, half * 2, half * 2)
        }
        g.dispose()
        return img
    }

    /** 円形マスク内 (実 LED) のセルだけを列挙して中心座標と明度を渡す */
    private inline fun forEachLed(
        levels: IntArray, cellPx: Int, padPx: Int,
        draw: (cx: Int, cy: Int, v: Int) -> Unit,
    ) {
        val center = HanabiEngine.CENTER.toInt()
        for (r in 0 until GRID) {
            for (c in 0 until GRID) {
                val dr = r - center
                val dc = c - center
                if (dr * dr + dc * dc > HanabiEngine.MASK_R2) continue
                draw(padPx + c * cellPx + cellPx / 2, padPx + r * cellPx + cellPx / 2,
                    levels[r * GRID + c].coerceIn(0, 255))
            }
        }
    }

    /**
     * アニメ GIF 書き出し (無限ループ、フレーム間隔 DELAY_CS/100 秒)。
     * [body] に emit 関数を渡し、呼ばれるたびにそのフレームを逐次書き込む
     */
    private fun writeGif(file: File, body: (emit: (BufferedImage) -> Unit) -> Unit) {
        val writer = ImageIO.getImageWritersBySuffix("gif").next()
        val params = writer.defaultWriteParam
        val meta = writer.getDefaultImageMetadata(
            ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB), params)

        val fmt = meta.nativeMetadataFormatName  // "javax_imageio_gif_image_1.0"
        val root = meta.getAsTree(fmt) as IIOMetadataNode

        // フレーム間隔 (GIF の時間単位は 1/100 秒)
        val gce = IIOMetadataNode("GraphicControlExtension").apply {
            setAttribute("disposalMethod", "none")
            setAttribute("userInputFlag", "FALSE")
            setAttribute("transparentColorFlag", "FALSE")
            setAttribute("delayTime", DELAY_CS.toString())
            setAttribute("transparentColorIndex", "0")
        }
        root.appendChild(gce)

        // 無限ループ指定 (NETSCAPE2.0 拡張、loop count 0 = forever)
        val appExts = IIOMetadataNode("ApplicationExtensions")
        val app = IIOMetadataNode("ApplicationExtension").apply {
            setAttribute("applicationID", "NETSCAPE")
            setAttribute("authenticationCode", "2.0")
            userObject = byteArrayOf(1, 0, 0)
        }
        appExts.appendChild(app)
        root.appendChild(appExts)
        meta.setFromTree(fmt, root)

        file.parentFile?.mkdirs()
        FileImageOutputStream(file).use { out ->
            writer.output = out
            writer.prepareWriteSequence(null)
            body { f -> writer.writeToSequence(IIOImage(f, null, meta), params) }
            writer.endWriteSequence()
        }
        writer.dispose()
    }
}
