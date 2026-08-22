package com.core.ons

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.media.MediaPlayer
import android.widget.FrameLayout
import android.widget.VideoView

class OnsVideoActivity : Activity() {
    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        enterImmersive()
        val view = VideoView(this)
        view.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(view)
        val text = intent.getStringExtra(EXTRA_VIDEO_URI)
        val uri = if (text == null) intent.getParcelableExtra<Uri>(EXTRA_VIDEO_URI) else Uri.parse(text)
        if (uri == null) {
            finish()
            return
        }
        view.setVideoURI(uri)
        view.setOnCompletionListener { finish() }
        view.setOnErrorListener { _: MediaPlayer?, _: Int, _: Int -> finish(); true }
        view.start()
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
    }

    private fun enterImmersive() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.decorView.windowInsetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
}
