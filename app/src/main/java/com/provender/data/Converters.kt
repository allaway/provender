package com.provender.data

import androidx.room.TypeConverter
import com.provender.data.entity.SnapshotStatus
import com.provender.data.model.Category
import com.provender.data.model.ChangeReason
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json

    @TypeConverter
    fun categoryToString(value: Category): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): Category =
        runCatching { Category.valueOf(value) }.getOrDefault(Category.OTHER)

    @TypeConverter
    fun changeReasonToString(value: ChangeReason): String = value.name

    @TypeConverter
    fun stringToChangeReason(value: String): ChangeReason =
        runCatching { ChangeReason.valueOf(value) }.getOrDefault(ChangeReason.MANUAL_EDIT)

    @TypeConverter
    fun snapshotStatusToString(value: SnapshotStatus): String = value.name

    @TypeConverter
    fun stringToSnapshotStatus(value: String): SnapshotStatus =
        runCatching { SnapshotStatus.valueOf(value) }.getOrDefault(SnapshotStatus.FAILED)

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}
