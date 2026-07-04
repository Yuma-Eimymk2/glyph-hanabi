package com.eimymk2.glyphhanabi.engine

/**
 * 玉種。パラメータの実体は HanabiEngine.burst() にある
 * (シミュレータ v6 の burst() と 1:1 で突き合わせられるようにするため)。
 */
enum class ShellType {
    KIKU,    // 菊: 二層の光条 + 散り星6
    BOTAN,   // 牡丹: 純粋な点の真円
    SHIN,    // 芯入り: 同心二重円
    SENRIN,  // 千輪: 小 ring × 6-8 箇所の同時多発 (打ち上げなし・時間差 pending)
    KAMURO,  // 錦冠: 真円 → 終端速度 1.75 cells/s で長く垂れる
    YASHI,   // 椰子: 腕7本 + THICK
    YANAGI,  // 柳: 錦冠の小型・短命版
    HACHI,   // 蜂: JITTER + FLICKER。唯一、消え口を揃えない
    NIAGARA, // ナイアガラ: 上端エミッター3秒 (打ち上げなし・締め専用)
}
