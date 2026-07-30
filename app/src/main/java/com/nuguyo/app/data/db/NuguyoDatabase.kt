package com.nuguyo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [EmployeeEntity::class, PhoneNumberEntity::class, ContactEventEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NuguyoDatabase : RoomDatabase() {

    abstract fun employeeDao(): EmployeeDao
    abstract fun contactEventDao(): ContactEventDao

    companion object {
        private const val NAME = "nuguyo.db"

        fun build(context: Context): NuguyoDatabase =
            Room.databaseBuilder(context, NuguyoDatabase::class.java, NAME)
                // 외래키 CASCADE 로 직원 삭제 시 번호까지 정리되게 한다.
                .build()
    }
}
