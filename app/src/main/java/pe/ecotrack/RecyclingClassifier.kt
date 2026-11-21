package pe.ecotrack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File

internal data class RecyclingGuess(val material: String, val confident: Boolean)

internal suspend fun decodeMaterialPhoto(file: File): Bitmap = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("No pudimos abrir la foto. Vuelve a tomarla.")
    val exif = ExifInterface(file)
    if (!exif.isFlipped && exif.rotationDegrees == 0) return@withContext bitmap
    val transform = Matrix().apply {
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
}

internal suspend fun classifyRecycling(context: Context, photo: Bitmap): RecyclingGuess? = withContext(Dispatchers.Default) {
    val model = context.assets.open("recycling.tflite").use { it.readBytes() }
    val weights = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
    weights.put(model).rewind()
    Interpreter(weights, Interpreter.Options().setNumThreads(2)).use { interpreter ->
        val full = classifyFrame(interpreter, photo)
        if (full.confident) return@withContext full.guess()
        val side = minOf(photo.width, photo.height)
        val crop = Bitmap.createBitmap(photo, (photo.width - side) / 2, (photo.height - side) / 2, side, side)
        val centered = classifyFrame(interpreter, crop)
        val chosen = if (centered.index == full.index && centered.score > full.score) centered else full
        if (chosen.confident) return@withContext chosen.guess()
        if (chosen.score >= .55f && chosen.margin >= .08f) return@withContext chosen.guess()
        null
    }
}

private data class Prediction(val index: Int, val score: Float, val margin: Float) {
    val confident get() = score >= .68f && margin >= .12f

    fun guess(): RecyclingGuess? {
        val material = when (index) {
            2 -> "Vidrio"
            3 -> "Cartón"
            7 -> "Papel"
            8 -> "Plástico"
            else -> return null
        }
        return RecyclingGuess(material, confident)
    }
}

private fun classifyFrame(interpreter: Interpreter, photo: Bitmap): Prediction {
    val size = 224
    val pixels = IntArray(size * size)
    val square = Bitmap.createScaledBitmap(photo, size, size, true)
    square.getPixels(pixels, 0, size, 0, 0, size, size)
    val input = ByteBuffer.allocateDirect(pixels.size * 3 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    pixels.forEach { pixel ->
        input.putFloat((pixel shr 16 and 255) / 255f)
        input.putFloat((pixel shr 8 and 255) / 255f)
        input.putFloat((pixel and 255) / 255f)
    }
    input.rewind()
    val scores = FloatArray(12)
    interpreter.run(input, arrayOf(scores))
    // The model distinguishes brown, green, and clear glass. EcoTrack uses one glass category.
    scores[2] += scores[5] + scores[11]
    scores[5] = 0f
    scores[11] = 0f
    val best = scores.indices.maxBy { scores[it] }
    val second = scores.indices.filter { it != best }.maxOf { scores[it] }
    return Prediction(best, scores[best], scores[best] - second)
}

