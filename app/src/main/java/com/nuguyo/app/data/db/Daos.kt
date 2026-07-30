package com.nuguyo.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * interface 가 아니라 abstract class 인 이유: 본문이 있는 `@Transaction` 메서드
 * ([save])를 Room 이 확실하게 처리하려면 추상 클래스 쪽이 안전하다.
 */
@Dao
abstract class EmployeeDao {

    @Transaction
    // COLLATE LOCALIZED 는 Room 의 컴파일타임 쿼리 검증기가 모르는 콜레이션이라 쓰지 않는다.
    // 한글 정렬은 유니코드 코드포인트 순서로도 가나다순이 나온다.
    @Query("SELECT * FROM employee ORDER BY name ASC")
    abstract fun observeAll(): Flow<List<EmployeeWithNumbers>>

    @Transaction
    @Query("SELECT * FROM employee WHERE id = :id")
    abstract fun observe(id: String): Flow<EmployeeWithNumbers?>

    @Transaction
    @Query("SELECT * FROM employee WHERE id = :id")
    abstract suspend fun find(id: String): EmployeeWithNumbers?

    /** 정확 매칭. 전화가 울리는 순간 가장 먼저 타는 경로. */
    @Transaction
    @Query(
        """
        SELECT * FROM employee
        WHERE id IN (SELECT employeeId FROM phone_number WHERE e164 = :e164)
        """,
    )
    abstract suspend fun findByE164(e164: String): List<EmployeeWithNumbers>

    /** 보조 매칭. 결과가 2건 이상이면 신뢰할 수 없으므로 호출부에서 버린다. */
    @Transaction
    @Query(
        """
        SELECT * FROM employee
        WHERE id IN (SELECT employeeId FROM phone_number WHERE loose = :loose)
        """,
    )
    abstract suspend fun findByLoose(loose: String): List<EmployeeWithNumbers>

    @Query("SELECT COUNT(*) FROM employee")
    abstract suspend fun count(): Int

    @Upsert
    abstract suspend fun upsert(employee: EmployeeEntity)

    @Insert
    abstract suspend fun insertNumbers(numbers: List<PhoneNumberEntity>)

    @Query("DELETE FROM phone_number WHERE employeeId = :employeeId")
    abstract suspend fun deleteNumbersOf(employeeId: String)

    @Query("DELETE FROM employee WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** 번호 목록은 부분 갱신 대신 통째로 교체한다(순서 변경/삭제를 한 번에 처리). */
    @Transaction
    open suspend fun save(employee: EmployeeEntity, numbers: List<PhoneNumberEntity>) {
        upsert(employee)
        deleteNumbersOf(employee.id)
        if (numbers.isNotEmpty()) insertNumbers(numbers)
    }
}

@Dao
interface ContactEventDao {

    @Transaction
    @Query(
        """
        SELECT e.*, emp.name AS employeeName
        FROM contact_event e
        LEFT JOIN employee emp ON emp.id = e.employeeId
        ORDER BY e.at DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<ContactEventWithName>>

    @Insert
    suspend fun insert(event: ContactEventEntity)

    @Query("SELECT COUNT(*) FROM contact_event WHERE employeeId = :employeeId")
    suspend fun countFor(employeeId: String): Int

    /** 지금 걸려온 이벤트를 제외한 직전 연락 시각. */
    @Query("SELECT MAX(at) FROM contact_event WHERE employeeId = :employeeId AND at < :before")
    suspend fun lastContactBefore(employeeId: String, before: Long): Long?

    /** 이력이 무한정 쌓이지 않게 오래된 것부터 정리한다. */
    @Query("DELETE FROM contact_event WHERE at < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM contact_event")
    suspend fun clear()
}

data class ContactEventWithName(
    @androidx.room.Embedded val event: ContactEventEntity,
    val employeeName: String?,
)
