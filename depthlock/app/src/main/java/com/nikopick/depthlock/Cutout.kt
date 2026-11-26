package com.nikopick.depthlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Crops the picked photo to this screen's shape and cuts the subject out of it. */
object Cutout {
    private const val MAX_SOURCE = 3600
    private const val MAX_HEIGHT = 2400

    /** Photo in, background and automatic cut-out out. */
    suspend fun process(ctx: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val bg = decodeCrop(ctx, uri)
            val segmenter = SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            )
            try {
                val result = suspendCancellableCoroutine<SubjectSegmentationResult> { cont ->
                    segmenter.process(InputImage.fromBitmap(bg, 0))
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                val subject = result.foregroundBitmap ?: error("No subject found in that photo")
                Store.save(ctx, bg, subject)
            } finally {
                segmenter.close()
            }
        }
    }

    /** Your own background. Keeps the current cut-out, so use the same framing for both. */
    suspend fun ownBackground(ctx: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching { Store.saveBg(ctx, decodeCrop(ctx, uri)) }
    }

    /** Your own cut-out: a PNG with a transparent background, same framing as the background. */
    suspend fun ownSubject(ctx: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val b = decodeCrop(ctx, uri)
            if (!b.hasAlpha()) error("That image has no transparent area. Use a PNG with the background removed")
            Store.saveSubject(ctx, b)
        }
    }

    /** Decodes (honouring rotation), crops to this screen's shape, caps the size. */
    private fun decodeCrop(ctx: Context, uri: Uri): Bitmap {
        val dm = ctx.resources.displayMetrics
        val aspect = dm.widthPixels.toFloat() / dm.heightPixels

        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SOURCE) {
                val k = MAX_SOURCE.toFloat() / longest
                d.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
            }
        }

        var cw = src.width
        var ch = (cw / aspect).toInt()
        if (ch > src.height) {
            ch = src.height
            cw = (ch * aspect).toInt()
        }
        var out = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        if (out.height > MAX_HEIGHT) {
            out = Bitmap.createScaledBitmap(out, (out.width * MAX_HEIGHT.toFloat() / out.height).toInt(), MAX_HEIGHT, true)
        }
        return out
    }
}
