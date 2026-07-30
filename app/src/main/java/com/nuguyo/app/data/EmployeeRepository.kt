package com.nuguyo.app.data

import com.nuguyo.app.data.db.EmployeeDao
import com.nuguyo.app.data.db.EmployeeEntity
import com.nuguyo.app.data.db.EmployeeWithNumbers
import com.nuguyo.app.data.db.PhoneNumberEntity
import com.nuguyo.app.domain.lookup.CallerDirectory
import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.model.StaffNumber
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class EmployeeRepository(private val dao: EmployeeDao) : CallerDirectory {

    fun observeAll(): Flow<List<Employee>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    fun observe(id: String): Flow<Employee?> =
        dao.observe(id).map { it?.toModel() }

    suspend fun find(id: String): Employee? = dao.find(id)?.toModel()

    override suspend fun findByE164(e164: String): List<Employee> =
        dao.findByE164(e164).map { it.toModel() }

    override suspend fun findByLoose(loose: String): List<Employee> =
        dao.findByLoose(loose).map { it.toModel() }

    override suspend fun touch() {
        dao.count()
    }

    suspend fun count(): Int = dao.count()

    /**
     * 저장 시점에 매칭 키를 계산해 둔다. 전화가 울리는 순간에는 정규화 비용조차
     * 아끼고 색인 조회만 하도록 하는 게 목적이다.
     *
     * @return 저장된 직원 id (신규면 새로 만든 id)
     */
    suspend fun save(employee: Employee): String {
        val id = employee.id.ifBlank { UUID.randomUUID().toString() }
        val entity = EmployeeEntity(
            id = id,
            name = employee.name.trim(),
            department = employee.department?.trim()?.takeIf { it.isNotEmpty() },
            title = employee.title?.trim()?.takeIf { it.isNotEmpty() },
            employeeNo = employee.employeeNo?.trim()?.takeIf { it.isNotEmpty() },
            photoUri = employee.photoUri,
            memo = employee.memo?.trim()?.takeIf { it.isNotEmpty() },
            tags = employee.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            updatedAt = System.currentTimeMillis(),
        )
        val numbers = employee.numbers
            .filter { it.raw.isNotBlank() }
            .distinctBy { PhoneNumberNormalizer.normalize(it.raw).digits }
            .map { number ->
                val (e164, loose) = PhoneNumberNormalizer.keysOf(number.raw)
                PhoneNumberEntity(
                    id = number.id.ifBlank { UUID.randomUUID().toString() },
                    employeeId = id,
                    raw = number.raw.trim(),
                    e164 = e164,
                    loose = loose,
                    label = number.label?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        dao.save(entity, numbers)
        return id
    }

    suspend fun delete(id: String) = dao.delete(id)
}

private fun EmployeeWithNumbers.toModel() = Employee(
    id = employee.id,
    name = employee.name,
    department = employee.department,
    title = employee.title,
    employeeNo = employee.employeeNo,
    photoUri = employee.photoUri,
    memo = employee.memo,
    tags = employee.tags,
    numbers = numbers.map {
        StaffNumber(
            id = it.id,
            raw = it.raw,
            e164 = it.e164,
            loose = it.loose,
            label = it.label,
        )
    },
    updatedAt = employee.updatedAt,
)
