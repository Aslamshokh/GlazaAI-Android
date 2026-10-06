package com.aslamshoh.glazaai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** Загрузка картинок: фото из галереи и обрезка кусочка кадра для миниатюры. */
object ImageLoading {
    /** Читает фото из галереи с уменьшением (не больше maxSide по большей стороне) и с учётом
     * поворота из EXIF — иначе снимки с телефона оказываются «лёжа». */
    fun decodeUri(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val rotation = resolver.openInputStream(uri)?.use { readRotation(it) } ?: 0
            if (rotation == 0) {
                decoded
            } else {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readRotation(stream: java.io.InputStream): Int {
        return try {
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /** Вырезает прямоугольник box = [x1, y1, x2, y2] (доли кадра 0..1) с небольшим запасом. */
    fun cropNormalized(source: Bitmap, box: List<Double>): Bitmap? {
        if (box.size < 4) return null
        return try {
            val w = source.width
            val h = source.height
            val padX = ((box[2] - box[0]) * 0.08 * w).toInt()
            val padY = ((box[3] - box[1]) * 0.08 * h).toInt()
            val x1 = ((box[0] * w).toInt() - padX).coerceIn(0, w - 1)
            val y1 = ((box[1] * h).toInt() - padY).coerceIn(0, h - 1)
            val x2 = ((box[2] * w).toInt() + padX).coerceIn(x1 + 1, w)
            val y2 = ((box[3] * h).toInt() + padY).coerceIn(y1 + 1, h)
            Bitmap.createBitmap(source, x1, y1, x2 - x1, y2 - y1)
        } catch (e: Exception) {
            null
        }
    }
}

/** Короткие пояснения к частым предметам (как «Диван — предмет мебели» на макете). Это
 * справочник внутри приложения, а не ответ нейросети; для остальных предметов пояснения нет. */
object ObjectInfo {
    private val notes = mapOf(
        "диван" to "Предмет мебели для сидения. Обычно стоит в гостиной.",
        "стул" to "Мебель для сидения на одного человека.",
        "стол" to "Мебель с ровной поверхностью: за ним едят, работают, пишут.",
        "обеденный стол" to "Стол, за которым обычно едят.",
        "письменный стол" to "Рабочий стол для письма и компьютера.",
        "лампа" to "Источник света. Может быть настольной или напольной.",
        "ноутбук" to "Портативный компьютер с экраном и клавиатурой.",
        "телефон" to "Мобильный телефон.",
        "кружка" to "Посуда для горячих напитков.",
        "чашка кофе" to "Посуда для кофе или чая.",
        "бутылка" to "Ёмкость для жидкости.",
        "дверь" to "Проход в помещение. Проверьте, в какую сторону она открывается.",
        "окно" to "Остеклённый проём в стене.",
        "лестница" to "Ступени — будьте осторожны, лучше держаться за перила.",
        "скамейка" to "Место, где можно присесть на улице.",
        "светофор" to "Сигнал для движения машин и пешеходов.",
        "машина" to "Автомобиль. Будьте осторожны рядом с дорогой.",
        "велосипед" to "Двухколёсный транспорт, может двигаться быстро.",
        "человек" to "Человек рядом с вами.",
        "собака" to "Домашнее животное.",
        "кошка" to "Домашнее животное.",
        "телевизор" to "Экран для просмотра передач и фильмов.",
        "холодильник" to "Бытовой прибор для хранения продуктов.",
        "книга" to "Печатное издание.",
        "сумка" to "Сумка для вещей.",
        "рюкзак" to "Сумка, которую носят на спине.",
        "зеркало" to "Отражающая поверхность.",
        "подушка" to "Мягкий предмет, обычно на диване или кровати.",
        "растение" to "Комнатное или уличное растение."
    )

    fun note(label: String): String? = notes[label.trim().lowercase()]
}
