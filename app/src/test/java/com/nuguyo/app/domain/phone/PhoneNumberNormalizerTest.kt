package com.nuguyo.app.domain.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실제로 명부에 들어오는 표기와 통신사가 넘겨주는 표기가 섞여도 같은 사람으로
 * 인식되는지가 이 앱의 전부다. 그래서 표기 케이스를 표로 못박아 둔다.
 */
class PhoneNumberNormalizerTest {

    private fun check(raw: String?, e164: String?, loose: String?, formatted: String) {
        val normalized = PhoneNumberNormalizer.normalize(raw)
        assertEquals("e164 (입력=$raw)", e164, normalized.e164)
        assertEquals("loose (입력=$raw)", loose, normalized.loose)
        assertEquals("format (입력=$raw)", formatted, PhoneNumberNormalizer.format(raw))
    }

    @Test
    fun `휴대폰 번호는 표기가 달라도 같은 키로 정규화된다`() {
        check("010-1234-5678", "+821012345678", "12345678", "010-1234-5678")
        check("01012345678", "+821012345678", "12345678", "010-1234-5678")
        check("+821012345678", "+821012345678", "12345678", "010-1234-5678")
        check("+82 10-1234-5678", "+821012345678", "12345678", "010-1234-5678")
        check("(010) 1234-5678", "+821012345678", "12345678", "010-1234-5678")
        check("tel:+821012345678", "+821012345678", "12345678", "010-1234-5678")
    }

    @Test
    fun `국가번호와 국내 통화용 0 이 함께 붙은 표기도 처리한다`() {
        check("+82 010 1234 5678", "+821012345678", "12345678", "010-1234-5678")
        check("0082 10 1234 5678", "+821012345678", "12345678", "010-1234-5678")
    }

    @Test
    fun `011 은 국제 프리픽스가 아니라 옛 이동전화 국번이다`() {
        check("011-123-4567", "+82111234567", "11234567", "011-123-4567")
        check("017-1234-5678", "+821712345678", "12345678", "017-1234-5678")
    }

    @Test
    fun `유선 번호의 지역번호를 떼어낸다`() {
        check("02-1234-5678", "+82212345678", "12345678", "02-1234-5678")
        check("02-123-4567", "+8221234567", "21234567", "02-123-4567")
        check("031-123-4567", "+82311234567", "11234567", "031-123-4567")
        check("070-8888-9999", "+827088889999", "88889999", "070-8888-9999")
    }

    @Test
    fun `0 없이 시작하는 대표번호에는 0 을 붙이지 않는다`() {
        check("1588-1234", "+8215881234", "15881234", "1588-1234")
        check("15881234", "+8215881234", "15881234", "1588-1234")
    }

    @Test
    fun `안심번호도 일반 국내 번호로 다룬다`() {
        check("050-4123-4567", "+825041234567", "41234567", "050-4123-4567")
    }

    @Test
    fun `사내 내선처럼 짧은 번호는 E164 를 만들지 않는다`() {
        check("1234", null, "1234", "1234")
        // 특수번호 오매칭을 막기 위해 4자리 미만은 매칭 키로 쓰지 않는다.
        check("119", null, null, "119")
        assertFalse(PhoneNumberNormalizer.normalize("119").isUsable)
        assertTrue(PhoneNumberNormalizer.normalize("1234").isUsable)
    }

    @Test
    fun `해외 번호는 국가번호를 쪼개지 않고 통과시킨다`() {
        check("+1 415 555 2671", "+14155552671", "55552671", "+14155552671")
        check("001-1-415-555-2671", "+14155552671", "55552671", "+14155552671")
        check("+81 90 1234 5678", "+819012345678", "12345678", "+819012345678")
    }

    @Test
    fun `발신번호 표시제한과 쓰레기 입력은 매칭 대상이 아니다`() {
        check(null, null, null, "")
        check("", null, null, "")
        check("unknown", null, null, "unknown")
        check("Unknown", null, null, "Unknown")
        assertNull(PhoneNumberNormalizer.normalize("private").e164)
    }

    @Test
    fun `번호 앞에 붙은 GSM 서비스 코드를 벗겨낸다`() {
        check("*23#010-1234-5678", "+821012345678", "12345678", "010-1234-5678")
    }

    @Test
    fun `유선과 휴대폰의 뒤 8자리는 겹칠 수 있다`() {
        // 그래서 loose 키만으로는 사람을 단정할 수 없다.
        // CallerLookup 이 후보가 유일할 때만 인정하는 이유.
        val mobile = PhoneNumberNormalizer.normalize("010-1234-5678")
        val landline = PhoneNumberNormalizer.normalize("02-1234-5678")
        assertEquals(mobile.loose, landline.loose)
        assertTrue(mobile.e164 != landline.e164)
    }
}
