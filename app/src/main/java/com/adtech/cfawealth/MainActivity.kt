package com.adtech.cfawealth

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var titleText: TextView
    private var useFront = false
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val jackpotIds = HashSet<Int>()

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

    private fun roundButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 24f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC00AACC"))
        }
    }

    private fun buildScreen() {
        val root = FrameLayout(this)

        previewView = PreviewView(this)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        overlay = OverlayView(this)
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        titleText = TextView(this).apply {
            text = "CFA WEALTH SCANNER"
            textSize = 16f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(dp(8), dp(36), dp(8), dp(10))
        }
        root.addView(
            titleText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val photoBtn = roundButton("📸")
        photoBtn.setOnClickListener { takePhoto() }
        val photoParams = FrameLayout.LayoutParams(
            dp(80), dp(80), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        photoParams.bottomMargin = dp(40)
        root.addView(photoBtn, photoParams)

        val flipBtn = roundButton("🔄")
        flipBtn.setOnClickListener {
            useFront = !useFront
            startCamera()
        }
        val flipParams = FrameLayout.LayoutParams(
            dp(60), dp(60), Gravity.BOTTOM or Gravity.END
        )
        flipParams.bottomMargin = dp(50)
        flipParams.marginEnd = dp(24)
        root.addView(flipBtn, flipParams)

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
            analysis.setAnalyzer(bgExecutor, CameraAnalyzer { frame ->
                overlay.update(frame)
                for (p in frame.people) {
                    val v = p.fcfa
                    if (v != null && v >= 150_000_000L && jackpotIds.add(p.id)) {
                        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            .startTone(ToneGenerator.TONE_PROP_ACK, 400)
                    }
                }
            })
            overlay.mirror = useFront
            val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val shot = previewView.bitmap
        if (shot == null) {
            Toast.makeText(this, "Caméra pas prête", Toast.LENGTH_SHORT).show()
            return
        }
        val bmp = shot.copy(Bitmap.Config.ARGB_8888, true)
        overlay.drawFrame(Canvas(bmp), bmp.width, bmp.height)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "CFA_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CFAWealthScanner")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            Toast.makeText(this, "Photo enregistrée dans la Galerie", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Échec de l'enregistrement", Toast.LENGTH_SHORT).show()
        }
    }
}
