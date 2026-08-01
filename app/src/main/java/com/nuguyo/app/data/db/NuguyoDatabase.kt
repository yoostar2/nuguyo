package com.nuguyo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EmployeeEntity::class, PhoneNumberEntity::class, ContactEventEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NuguyoDatabase : RoomDatabase() {

    abstract fun employeeDao(): EmployeeDao
    abstract fun contactEventDao(): ContactEventDao

    companion object {
        private const val NAME = "nuguyo.db"

        /**
         * 구글 시트 동기화를 위해 출처 열을 추가한다.
         * 이미 들어 있는 직원은 전부 앱에서 직접 만든 것이므로 LOCAL 로 둔다
         * (그래야 첫 동기화가 기존 명부를 지우지 않는다).
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE employee ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'",
                )
                db.execSQL("ALTER TABLE employee ADD COLUMN sourceKey TEXT")
            }
        }

        fun build(context: Context): NuguyoDatabase =
            Room.databaseBuilder(context, NuguyoDatabase::class.java, NAME)
                // 외래키 CASCADE 로 직원 삭제 시 번호까지 정리되게 한다.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
