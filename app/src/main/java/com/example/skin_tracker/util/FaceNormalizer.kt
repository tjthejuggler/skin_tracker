package com.example.skin_tracker.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Normalises face photos so that the facial features (eyes, nose, mouth) are
 * aligned to canonical positions. This produces a consistent crop where the
 * face occupies the same region and orientation in every output bitmap,
 * making it possible to create a smooth "morphing" animation across days.
 *
 * Uses Google ML Kit for landmark detection and a least-squares similarity
 * transform (Umeyama algorithm) computed from the detected landmarks.
 */
object FaceNormalizer {

    /** Output dimension (square). */
    const val OUTPUT_SIZE = 512

    // ── Canonical landmark positions (in output-pixel coordinates) ──────────
    // Tight crop: eyes at ~35% from top, spaced ~40% of width apart (zoomed in).
    // This frames the head with minimal background — forehead/hair above,
    // chin/neck below.
    private val CANONICAL_LEFT_EYE = PointF(OUTPUT_SIZE * 0.30f, OUTPUT_SIZE * 0.35f)
    private val CANONICAL_RIGHT_EYE = PointF(OUTPUT_SIZE * 0.70f, OUTPUT_SIZE * 0.35f)
    private val CANONICAL_NOSE = PointF(OUTPUT_SIZE * 0.50f, OUTPUT_SIZE * 0.50f)
    private val CANONICAL_MOUTH = PointF(OUTPUT_SIZE * 0.50f, OUTPUT_SIZE * 0.62f)

    private val canonicalPoints = listOf(
        CANONICAL_LEFT_EYE, CANONICAL_RIGHT_EYE, CANONICAL_NOSE, CANONICAL_MOUTH
    )

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Loads a bitmap from [path], detects the primary face, and returns a
     * normalised square [OUTPUT_SIZE]×[OUTPUT_SIZE] bitmap with the face
     * landmarks aligned to canonical positions.
     *
     * Returns `null` if the file cannot be decoded or no face with sufficient
     * landmarks is found.
     */
    suspend fun normalizeFromFile(path: String): Bitmap? {
        val bitmap = decodeBitmap(path) ?: return null
        return normalize(bitmap).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    /**
     * Detects the primary face in [bitmap] and returns a normalised
     * [OUTPUT_SIZE]×[OUTPUT_SIZE] bitmap.
     */
    suspend fun normalize(bitmap: Bitmap): Bitmap? {
        val face = detectFace(bitmap) ?: return null

        val srcPoints = collectLandmarks(face) ?: return null

        val matrix = computeSimilarityTransform(srcPoints, canonicalPoints)

        // Draw the transformed source bitmap directly onto an OUTPUT_SIZE canvas.
        // This avoids creating a huge intermediate bitmap and produces clean
        // output with no black/transparent borders.
        val result = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(bitmap, matrix, null)
        return result
    }

    // ── Face detection ──────────────────────────────────────────────────────

    private suspend fun detectFace(bitmap: Bitmap): Face? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces -> cont.resume(faces.firstOrNull()) }
            .addOnFailureListener { cont.resume(null) }
    }

    /**
     * Collects the four key landmarks (left eye, right eye, nose base, mouth
     * bottom) from a [Face]. Returns `null` if any landmark is missing.
     */
    private fun collectLandmarks(face: Face): List<PointF>? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return null
        return listOf(
            PointF(leftEye.x, leftEye.y),
            PointF(rightEye.x, rightEye.y),
            PointF(nose.x, nose.y),
            PointF(mouth.x, mouth.y)
        )
    }

    // ── Similarity transform (Umeyama least-squares) ────────────────────────

    /**
     * Computes the similarity transform (scale + rotation + translation) that
     * best maps [src] points onto [dst] points in a least-squares sense.
     *
     * Uses the closed-form Umeyama algorithm. The resulting [Matrix] maps
     * source coordinates to destination coordinates.
     */
    private fun computeSimilarityTransform(
        src: List<PointF>,
        dst: List<PointF>
    ): Matrix {
        val n = src.size
        require(n == dst.size && n >= 2) { "Need at least 2 corresponding points" }

        // Centroids
        val srcCx = src.sumOf { it.x.toDouble() } / n
        val srcCy = src.sumOf { it.y.toDouble() } / n
        val dstCx = dst.sumOf { it.x.toDouble() } / n
        val dstCy = dst.sumOf { it.y.toDouble() } / n

        // Cross-covariance accumulators and source variance
        var sxx = 0.0; var sxy = 0.0
        var syx = 0.0; var syy = 0.0
        var srcVar = 0.0

        for (i in 0 until n) {
            val sx = src[i].x - srcCx
            val sy = src[i].y - srcCy
            val dx = dst[i].x - dstCx
            val dy = dst[i].y - dstCy
            sxx += sx * dx
            sxy += sx * dy
            syx += sy * dx
            syy += sy * dy
            srcVar += sx * sx + sy * sy
        }

        if (srcVar < 1e-10) {
            // Degenerate: all source points are identical
            return Matrix().apply { setTranslate(dstCx.toFloat() - srcCx.toFloat(), dstCy.toFloat() - srcCy.toFloat()) }
        }

        // Optimal rotation angle
        val theta = atan2(syx - sxy, sxx + syy)

        // Optimal scale
        val traceHRT = (sxx + syy) * cos(theta) + (syx - sxy) * sin(theta)
        val scale = traceHRT / srcVar

        // Optimal translation
        val cosT = cos(theta)
        val sinT = sin(theta)
        val tx = dstCx - scale * cosT * srcCx + scale * sinT * srcCy
        val ty = dstCy - scale * sinT * srcCx - scale * cosT * srcCy

        // Build Android Matrix: [scale*cos, -scale*sin, tx, scale*sin, scale*cos, ty]
        val values = floatArrayOf(
            (scale * cosT).toFloat(), (-scale * sinT).toFloat(), tx.toFloat(),
            (scale * sinT).toFloat(), (scale * cosT).toFloat(), ty.toFloat(),
            0f, 0f, 1f
        )
        return Matrix().apply { setValues(values) }
    }

    // ── Bitmap helpers ──────────────────────────────────────────────────────

    private fun decodeBitmap(path: String): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun padToSize(src: Bitmap, size: Int): Bitmap {
        if (src.width >= size && src.height >= size) return src
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Leave transparent / black background; the face content is in the top-left
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(src, 0f, 0f, null)
        return result
    }

    /**
     * Quick sanity check: does the file at [path] contain a detectable face?
     * Useful for filtering before the heavier normalisation pass.
     */
    suspend fun hasFace(path: String): Boolean {
        val bitmap = decodeBitmap(path) ?: return false
        return try {
            detectFace(bitmap) != null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Computes the inter-pupillary distance (IPD) in pixels for the detected
     * face, useful for diagnostics.
     */
    fun getEyeDistance(face: Face): Float {
        val l = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return 0f
        val r = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return 0f
        return sqrt(((r.x - l.x) * (r.x - l.x) + (r.y - l.y) * (r.y - l.y)).toDouble()).toFloat()
    }
}
