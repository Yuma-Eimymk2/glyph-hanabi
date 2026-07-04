package com.eimymk2.glyphhanabi

/**
 * 13×13 の明度フレームを、Glyph Matrix SDK が期待する
 * 25×25 = 625 要素の IntArray に変換する。
 *
 * Glyph Life の実機検証 (2026-05-09) より:
 * - SDK は 25×25 配列を物理 137 LED の全体にマッピングする
 * - 13×13 を「中央配置」すると物理 LED の半分くらいにしか表示されない
 * → 13×13 を 25×25 全体に最近傍法で拡大マッピングする (Glyph Life と同方式)
 *
 * Glyph Life との違い: 入力が 0/1 の盤面ではなく、FrameCompositor.snapshot() が
 * 返す 0-255 の明度値 (ガンマ・上限適用済み) なので、値をそのまま流す。
 */
object GlyphRenderer {

    private const val OUTPUT_SIZE = 25
    private const val GAME_SIZE = 13

    const val OUTPUT_FRAME_SIZE = OUTPUT_SIZE * OUTPUT_SIZE  // 625

    /**
     * @param levels 13×13 = 169 要素、行優先、各要素 0-255 (FrameCompositor.snapshot())
     * @return 625 要素の IntArray、setMatrixFrame() に渡せる形
     */
    fun render(levels: IntArray): IntArray {
        require(levels.size == GAME_SIZE * GAME_SIZE) {
            "levels must be ${GAME_SIZE}x$GAME_SIZE"
        }

        val output = IntArray(OUTPUT_FRAME_SIZE)
        for (outRow in 0 until OUTPUT_SIZE) {
            for (outCol in 0 until OUTPUT_SIZE) {
                // 最近傍法: 整数除算で対応する 13×13 セルを決める
                val gameRow = outRow * GAME_SIZE / OUTPUT_SIZE
                val gameCol = outCol * GAME_SIZE / OUTPUT_SIZE
                output[outRow * OUTPUT_SIZE + outCol] = levels[gameRow * GAME_SIZE + gameCol]
            }
        }
        return output
    }
}
