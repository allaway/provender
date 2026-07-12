package com.provender.data

import androidx.room.TypeConverter
import com.provender.data.model.Category
import com.provender.data.model.ChangeReason

class Converters {
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
}
