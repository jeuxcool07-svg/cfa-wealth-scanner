package com.adtech.cfawealth

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class CameraAnalyzer(
    private val onFrame: (ScanFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private class Samples {
        var count = 0
        var style = 0f
        var clean = 0f
        var fcfa: Long? = null
    }

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
    )
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val people = HashMap<Int, Samples>()

    @Volatile
    private var busy = false
    private var lastRun = 0L
    private var lastMeasure = 0L

    override fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (busy || now - lastRun < 120) {
            proxy.close()
            return
        }
        lastRun = now
        busy = true

        val bmp = try {
            upright(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)
        } catch (e: Exception) {
            busy = false
            proxy.close()
            return
        }
        proxy.close()

        faceDetector.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { faces -> handleFaces(bmp, faces) }
            .addOnFailureListener { busy = false }
    }

    private fun handleFaces(bmp: Bitmap, faces: List<Face>) {
        val list = faces.filter { it.trackingId != null }.take(4)
        val now = System.currentTimeMillis()
        val toMeasure = ArrayList<Pair<Int, Bitmap>>()

        if (now - lastMeasure >= 400) {
            for (f in list) {
                val id = f.trackingId ?: continue
                val s = people.getOrPut(id) { Samples() }
                if (s.fcfa == null) {
                    val crop = bodyCrop(bmp, f.boundingBox) ?: bmp
                    toMeasure.add(id to crop)
                }
            }
        }

        if (toMeasure.isEmpty()) {
            publish(bmp, list)
            busy = false
            return
        }

        lastMeasure = now
        var pending = toMeasure.size
        for ((id, crop) in toMeasure) {
            labeler.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener { labels ->
                    val style = WealthScorer.styleScore(labels.map { it.text to it.confidence })
                    addSample(id, style, cleanliness(crop))
                }
                .addOnCompleteListener {
                    pending--
                    if (pending == 0) {
                        publish(bmp, list)
                        busy = false
                    }
                }
        }
    }

    private fun addSample(id: Int, style: Float, clean: Float) {
        val s = people.getOrPut(id) { Samples() }
        s.count++
        s.style += style
        s.clean += clean
        if (s.count >= 5) {
            s.fcfa = WealthScorer.toFcfa(s.style / s.count, s.clean / s.count)
        }
    }

    private fun publish(bmp: Bitmap, list: List<Face>) {
        val results = list.mapNotNull { f ->
            val id = f.trackingId ?: return@mapNotNull null
            val s = people[id]
            val progress = ((s?.count ?: 0) * 100 / 5).coerceAtMost(100)
            ScanResult(id, f.boundingBox, s?.fcfa, progress)
        }
        onFrame(ScanFrame(bmp.width, bmp.height, results))
    }

    private fun bodyCrop(bmp: Bitmap, face: Rect): Bitmap? {
        val w = face.width()
        val h = face.height()
        val left = (face.left - w / 2).coerceAtLeast(0)
        val top = (face.bottom + h / 4).coerceAtLeast(0)
        val right = (face.right + w / 2).coerceAtMost(bmp.width)
        val bottom = (face.bottom + h * 3).coerceAtMost(bmp.height)
        if (right - left < 40 || bottom - top < 40) return null
        return Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
    }

    private fun upright(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun cleanliness(b: Bitmap): Float {
        val sx = maxOf(1, b.width / 24)
        val sy = maxOf(1, b.height / 24)
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                val l = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                sum += l
                sumSq += l * l
                n++
                x += sx
            }
            y += sy
        }
        if (n == 0) return 0.5f
        val mean = sum / n
        if (mean < 1.0) return 0.5f
        val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
        val cv = Math.sqrt(variance) / mean
        return (1.0 - cv / 0.6).toFloat().coerceIn(0f, 1f)
    }
}
