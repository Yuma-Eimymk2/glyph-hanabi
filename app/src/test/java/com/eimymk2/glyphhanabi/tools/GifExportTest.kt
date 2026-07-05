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

    /** 13×13 明度フレーム (0-255) を「黒地 + 丸 LED」の画像にする */
    private fun renderFrame(levels: IntArray): BufferedImage {
        val img = BufferedImage(IMG, IMG, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fillRect(0, 0, IMG, IMG)

        // 2 パス描画: 先に全 LED のハロー (ぼんやりした光) → 後から芯。
        // 1 パスだと明るい LED のハローが隣の暗い LED の芯を上書きしてしまう
        forEachLed(levels) { cx, cy, v ->
            if (v > 0) {
                val r = 8 + 5 * v / 255  // 明るいほどハローが広がる
                g.color = Color(v * 30 / 100, v * 30 / 100, v * 30 / 100)
                g.fillOval(cx - r, cy - r, r * 2, r * 2)
            }
        }
        forEachLed(levels) { cx, cy, v ->
            if (v > 0) {
                g.color = Color(v, v, v)
                g.fillOval(cx - 6, cy - 6, 12, 12)
            } else {
                // 消灯 LED もうっすら見せて「盤面」の存在を伝える
                g.color = Color(15, 15, 15)
                g.fillOval(cx - 5, cy - 5, 10, 10)
            }
        }
        g.dispose()
        return img
    }

    /** 円形マスク内 (実 LED) のセルだけを列挙して中心座標と明度を渡す */
    private inline fun forEachLed(levels: IntArray, draw: (cx: Int, cy: Int, v: Int) -> Unit) {
        val center = HanabiEngine.CENTER.toInt()
        for (r in 0 until GRID) {
            for (c in 0 until GRID) {
                val dr = r - center
                val dc = c - center
                if (dr * dr + dc * dc > HanabiEngine.MASK_R2) continue
                draw(PAD + c * CELL + CELL / 2, PAD + r * CELL + CELL / 2,
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
