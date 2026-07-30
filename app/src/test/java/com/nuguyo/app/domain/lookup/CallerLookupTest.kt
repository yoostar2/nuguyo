package com.nuguyo.app.domain.lookup

import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.model.StaffNumber
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerLookupTest {

    @Test
    fun `E164 가 정확히 맞으면 EXACT 로 찾는다`() = runTest {
        val lookup = CallerLookup(FakeDirectory(employee("김하나", "010-1234-5678")))

        val match = lookup.identify("+82 10 1234 5678")

        assertEquals("김하나", match?.employee?.name)
        assertEquals(CallerLookup.Confidence.EXACT, match?.confidence)
    }

    @Test
    fun `E164 가 없더라도 뒤 8자리 후보가 유일하면 LOOSE 로 찾는다`() = runTest {
        // 명부에는 내선 표기로만 들어 있고, 걸려온 건 외부 회선인 상황.
        val lookup = CallerLookup(FakeDirectory(employee("이두리", "1234-5678")))

        val match = lookup.identify("02-1234-5678")

        assertEquals("이두리", match?.employee?.name)
        assertEquals(CallerLookup.Confidence.LOOSE, match?.confidence)
    }

    @Test
    fun `뒤 8자리 후보가 둘이면 아무도 지목하지 않는다`() = runTest {
        // 010-1234-5678 과 02-1234-5678 은 뒤 8자리가 같다.
        val lookup = CallerLookup(
            FakeDirectory(
                employee("김하나", "010-1234-5678"),
                employee("박세찌", "02-1234-5678"),
            ),
        )

        // 두 사람 중 누구도 E164 로는 맞지 않는 제3의 표기로 조회한다.
        assertNull(lookup.identify("031-1234-5678"))
    }

    @Test
    fun `같은 뒤 8자리를 가진 사람이 둘이어도 E164 가 맞으면 지목한다`() = runTest {
        val lookup = CallerLookup(
            FakeDirectory(
                employee("김하나", "010-1234-5678"),
                employee("박세찌", "02-1234-5678"),
            ),
        )

        val match = lookup.identify("010-1234-5678")

        assertEquals("김하나", match?.employee?.name)
        assertEquals(CallerLookup.Confidence.EXACT, match?.confidence)
    }

    @Test
    fun `발신번호 표시제한이면 조회 자체를 하지 않는다`() = runTest {
        val directory = FakeDirectory(employee("김하나", "010-1234-5678"))
        val lookup = CallerLookup(directory)

        assertNull(lookup.identify(null))
        assertNull(lookup.identify(""))
        assertNull(lookup.identify("unknown"))
        assertEquals(0, directory.queryCount)
    }

    @Test
    fun `한 사람에게 번호가 여러 개면 어느 쪽으로 와도 찾는다`() = runTest {
        val lookup = CallerLookup(
            FakeDirectory(employee("최네찌", "010-9999-8888", "02-555-1234")),
        )

        assertEquals("최네찌", lookup.identify("010-9999-8888")?.employee?.name)
        assertEquals("최네찌", lookup.identify("02-555-1234")?.employee?.name)
    }
}

private fun employee(name: String, vararg numbers: String) = Employee(
    id = name,
    name = name,
    numbers = numbers.map { raw ->
        val (e164, loose) = PhoneNumberNormalizer.keysOf(raw)
        StaffNumber(id = raw, raw = raw, e164 = e164, loose = loose)
    },
)

/** Room 색인 조회를 그대로 흉내내는 가짜 명부. */
private class FakeDirectory(private vararg val employees: Employee) : CallerDirectory {

    var queryCount = 0
        private set

    override suspend fun findByE164(e164: String): List<Employee> {
        queryCount++
        return employees.filter { employee -> employee.numbers.any { it.e164 == e164 } }
    }

    override suspend fun findByLoose(loose: String): List<Employee> {
        queryCount++
        return employees.filter { employee -> employee.numbers.any { it.loose == loose } }
    }

    override suspend fun touch() = Unit
}
