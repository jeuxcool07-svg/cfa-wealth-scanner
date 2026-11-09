package com.adtech.cfawealth

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var titleText: TextView
    private lateinit var styleText: TextView
    private lateinit var cleanText: TextView
    private lateinit var amountText: TextView
    private var jackpotPlayed = false

    private val askCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else titleText.text = "CAMÉRA REFUSÉE"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hudText(size: Float, color: Int): TextView = TextView(this).apply {
        textSize = size
        setTextColor(color)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
    }

    private fun buildScreen() {
        val root = FrameLayout(this)
        previewView = PreviewView(this)
        root.addView(previewView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#B3000000"))
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        titleText = hudText(16f, Color.parseColor("#00E5FF")).apply { text = "CFA WEALTH SCANNER" }
        styleText = hudText(14f, Color.parseColor("#00FF88")).apply { text = "STYLE : --" }
        cleanText = hudText(14f, Color.parseColor("#00FF88")).apply { text = "PROPRETÉ : --" }
        amountText = hudText(30f, Color.parseColor("#FFD700")).apply {
            text = "0 FCFA"
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        panel.addView(titleText)
        panel.addView(styleText)
        panel.addView(cleanText)
        panel.addView(amountText)

        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))
        setContentView(root)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(
                ContextCompat.getMainExecutor(this),
                CameraAnalyzer { style, clean, fcfa ->
                    runOnUiThread { showHud(style, clean, fcfa) }
                }
            )
            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showHud(style: Float, clean: Float, fcfa: Long) {
        styleText.text = "STYLE : ${(style * 100).toInt()}%"
        cleanText.text = "PROPRETÉ : ${(clean * 100).toInt()}%"
        amountText.text = String.format(Locale.US, "%,d", fcfa).replace(',', ' ') + " FCFA"
        if (fcfa >= 150_000_000L) {
            if (!jackpotPlayed) {
                jackpotPlayed = true
                ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    .startTone(ToneGenerator.TONE_PROP_ACK, 400)
            }
        } else {
            jackpotPlayed = false
        }
    }
}
