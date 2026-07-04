package com.eimymk2.glyphhanabi.engine

/**
 * 星(粒)1個の物理・寿命パラメータ。SPEC.md「粒(星)のデータ構造」と 1:1 対応。
 *
 * data class にしておくと equals/hashCode/copy が自動生成される。
 * burst 内の全星が同じインスタンスを共有する = 消え口(lifeAt)が揃う、という設計。
 * 蜂だけは粒ごとに別インスタンスを作る(唯一、消え口を揃えない玉種)。
 */
data class StarParams(
    val gravity: Float, // 落下加速 (cells/s²)
    val drag: Float,    // 等方空気抵抗 (1/s)
    val hold: Float,    // 真円保持時間 (s)。この間は重力ゼロ
    val tailN: Int,     // 尾の長さ (履歴点数)。0=点、5〜7=光条
    val fadeAt: Float,  // 満輝度の終わり (s)
    val lifeAt: Float,  // 消え口 (s)
)
