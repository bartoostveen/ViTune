package app.vitune.android.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media.ALBUM_ID
import android.provider.MediaStore.Audio.Media.ARTIST
import android.provider.MediaStore.Audio.Media.DISPLAY_NAME
import android.provider.MediaStore.Audio.Media.DURATION
import android.provider.MediaStore.Audio.Media.IS_MUSIC
import android.provider.MediaStore.Audio.Media._ID
import app.vitune.core.ui.utils.isAtLeastAndroid10
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import androidx.core.net.toUri

typealias CursorAccessor<T> = ReadOnlyProperty<Any?, T>

@Suppress("unused")
abstract class CursorDao(private val cursor: Cursor) {
    private val mutableProjection = mutableListOf<String>()
    val projection get() = mutableProjection.toTypedArray()

    private inline fun <T> column(
        column: String,
        crossinline get: Cursor.(Int) -> T
    ): CursorAccessor<T> {
        mutableProjection += column

        return object : ReadOnlyProperty<Any?, T> {
            private val idx by lazy { cursor[column] }

            override fun getValue(thisRef: Any?, property: KProperty<*>) = cursor.get(idx)
        }
    }

    private inline fun <T> nullableColumn(
        column: String,
        crossinline get: Cursor.(Int) -> T
    ): CursorAccessor<T?> {
        mutableProjection += column

        return object : ReadOnlyProperty<Any?, T?> {
            private val idx by lazy { cursor[column] }

            override fun getValue(thisRef: Any?, property: KProperty<*>) =
                if (cursor.isNull(idx)) null else cursor.get(idx)
        }
    }

    fun next() = cursor.moveToNext()

    fun string(col: String) = column<String>(col) { getString(it) }
    fun nullableString(col: String) = nullableColumn(col) { getString(it) }

    fun long(col: String) = column(col) { getLong(it) }
    fun nullableLong(col: String) = nullableColumn(col) { getLong(it) }

    fun int(col: String) = column(col) { getInt(it) }
    fun nullableInt(col: String) = nullableColumn(col) { getInt(it) }

    fun byteArray(col: String) = column<ByteArray>(col) { getBlob(it) }
    fun nullableByteArray(col: String) = nullableColumn(col) { getBlob(it) }

    fun double(col: String) = column(col) { getDouble(it) }
    fun nullableDouble(col: String) = nullableColumn(col) { getDouble(it) }

    fun float(col: String) = column(col) { getFloat(it) }
    fun nullableFloat(col: String) = nullableColumn(col) { getFloat(it) }

    fun short(col: String) = column(col) { getShort(it) }
    fun nullableShort(col: String) = nullableColumn(col) { getShort(it) }

    // pseudo-boolean
    fun boolean(col: String) = column(col) { getInt(it) != 0 }
    fun nullableBoolean(col: String) = column(col) { getInt(it) != 0 }
}

abstract class CursorDaoCompanion<T : CursorDao> {
    @Suppress("unused")
    enum class SortOrder(val sql: String) {
        Ascending("ASC"),
        Descending("DESC")
    }

    internal abstract fun order(order: SortOrder): String
    internal abstract fun new(cursor: Cursor): T

    internal abstract val uri: Uri
    internal abstract val projection: Array<String>

    fun <R> query(
        contentResolver: ContentResolver,
        order: SortOrder = SortOrder.Ascending,
        block: T.() -> R
    ) = contentResolver.query(
        /* uri = */ uri,
        /* projection = */ projection,
        /* selection = */ null,
        /* selectionArgs = */ null,
        /* sortOrder = */ order(order)
    )?.use { cursor ->
        new(cursor).block()
    }
}

operator fun Cursor.get(column: String): Int = getColumnIndexOrThrow(column)

object NoOpCursor : Cursor {
    override fun close() = Unit
    override fun getCount() = 0
    override fun getPosition() = 0
    override fun move(offset: Int) = false
    override fun moveToPosition(position: Int) = false
    override fun moveToFirst() = false
    override fun moveToLast() = false
    override fun moveToNext() = false
    override fun moveToPrevious() = false
    override fun isFirst() = false
    override fun isLast() = false
    override fun isBeforeFirst() = false
    override fun isAfterLast() = false
    override fun getColumnIndex(columnName: String?) = 0
    override fun getColumnIndexOrThrow(columnName: String?) = 0
    override fun getColumnName(columnIndex: Int) = ""
    override fun getColumnNames() = arrayOf<String>()
    override fun getColumnCount() = 0
    override fun getBlob(columnIndex: Int) = ByteArray(0)
    override fun getString(columnIndex: Int) = ""
    override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer?) = Unit
    override fun getShort(columnIndex: Int): Short = 0
    override fun getInt(columnIndex: Int) = 0
    override fun getLong(columnIndex: Int) = 0L
    override fun getFloat(columnIndex: Int) = 0f
    override fun getDouble(columnIndex: Int) = 0.0
    override fun getType(columnIndex: Int) = Cursor.FIELD_TYPE_NULL
    override fun isNull(columnIndex: Int) = true

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun deactivate() = Unit

    @Deprecated("Deprecated in Java", ReplaceWith("false"))
    override fun requery() = false

    override fun isClosed() = true
    override fun registerContentObserver(observer: ContentObserver?) = Unit
    override fun unregisterContentObserver(observer: ContentObserver?) = Unit
    override fun registerDataSetObserver(observer: DataSetObserver?) = Unit
    override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit
    override fun setNotificationUri(cr: ContentResolver?, uri: Uri?) = Unit
    override fun getNotificationUri(): Uri = Uri.EMPTY
    override fun getWantsAllOnMoveCalls() = false
    override fun setExtras(extras: Bundle?) = Unit
    override fun getExtras(): Bundle = Bundle.EMPTY
    override fun respond(extras: Bundle?): Bundle = Bundle.EMPTY
}

class AudioMediaCursor(cursor: Cursor) : CursorDao(cursor) {
    companion object : CursorDaoCompanion<AudioMediaCursor>() {
        val ALBUM_URI_BASE: Uri = "content://media/external/audio/albumart".toUri()

        override fun order(order: SortOrder) = "$DISPLAY_NAME ${order.sql}"
        override fun new(cursor: Cursor) = AudioMediaCursor(cursor)

        override val uri by lazy {
            if (isAtLeastAndroid10) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        override val projection by lazy {
            AudioMediaCursor(NoOpCursor).projection
        }
    }

    val isMusic by boolean(IS_MUSIC)
    val id by long(_ID)
    val name by string(DISPLAY_NAME)
    val duration by int(DURATION)
    val artist by string(ARTIST)
    private val albumId by long(ALBUM_ID)

    val albumUri get() = ContentUris.withAppendedId(ALBUM_URI_BASE, albumId)
}
