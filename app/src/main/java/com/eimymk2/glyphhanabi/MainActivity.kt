package com.eimymk2.glyphhanabi

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 使い方を表示するだけの最小限の Activity。
 *
 * 存在理由は表示内容より「インストール直後の stopped 状態の解除」:
 * Activity を持たないアプリは install 直後 stopped 状態のままで、
 * Glyph Toy に選んでも Service が bind されない (2026-07-03 実機確認)。
 * ユーザーがランチャーからこの画面を一度開けば stopped が解除され、
 * 以後 Toy として普通に動く。
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // レイアウト XML (res/layout/activity_main.xml) を画面として表示する
        setContentView(R.layout.activity_main)
        // 夜空コンセプトに合わせてアクションバーは隠す (テーマは DarkActionBar のため)
        supportActionBar?.hide()
    }
}
