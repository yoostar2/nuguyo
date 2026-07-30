package com.nuguyo.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter

@Entity(tableName = "employee")
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val department: String?,
    val title: String?,
    val employeeNo: String?,
    val photoUri: String?,
    val memo: String?,
    val tags: List<String>,
    val updatedAt: Long,
)

@Entity(
    tableName = "phone_number",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // e164 / loose 는 전화가 울리는 순간 조회되는 경로라 반드시 색인이 있어야 한다.
    indices = [Index("employeeId"), Index("e164"), Index("loose")],
)
data class PhoneNumberEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val raw: String,
    val e164: String?,
    val loose: String?,
    val label: String?,
)

@Entity(
    tableName = "contact_event",
    indices = [Index("employeeId"), Index("at")],
)
data class ContactEventEntity(
    @PrimaryKey val id: String,
    /** 미등록 번호였으면 null. 나중에 직원으로 등록해도 소급 연결하지 않는다. */
    val employeeId: String?,
    val number: String,
    val kind: String,
    val at: Long,
    val preview: String?,
)

data class EmployeeWithNumbers(
    @Embedded val employee: EmployeeEntity,
    @Relation(parentColumn = "id", entityColumn = "employeeId")
    val numbers: List<PhoneNumberEntity>,
)

class Converters {
    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString(SEPARATOR)

    @TypeConverter
    fun stringToTags(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(SEPARATOR)

    private companion object {
        /** 태그에 쓰일 일이 없는 제어문자를 구분자로 쓴다. */
        const val SEPARATOR = "\u001F"
    }
}
