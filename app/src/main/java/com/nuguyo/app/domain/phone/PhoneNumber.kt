package com.nuguyo.app.domain.phone

/**
 * 전화번호를 비교 가능한 형태로 정규화한 결과.
 *
 * @param e164 국제 표기(`+821012345678`). 국가번호를 확정할 수 없으면 null.
 * @param loose 국가번호와 국내 통화용 0 을 떼어낸 뒤쪽 최대 8자리. 표기가 뒤죽박죽인
 *   번호를 느슨하게 맞추기 위한 **보조** 키다. `02-1234-5678` 과 `010-1234-5678` 이
 *   같은 값을 가질 수 있으므로 단독으로 신뢰해서는 안 된다.
 *   ([com.nuguyo.app.domain.lookup.CallerLookup] 가 중복을 걸러낸다.)
 * @param digits 부호를 제거한 순수 숫자열. 표시/디버깅용.
 */
data class NormalizedNumber(
    val e164: String?,
    val loose: String?,
    val digits: String,
) {
    val isUsable: Boolean get() = e164 != null || loose != null

    companion object {
        val EMPTY = NormalizedNumber(null, null, "")
    }
}

/**
 * 한국 번호 표기를 정규화한다.
 *
 * libphonenumber 를 쓰지 않고 직접 구현한 이유:
 * - 이 앱이 다루는 건 사실상 국내 번호뿐이라 메타데이터 1MB 를 넣을 이유가 없다.
 * - 발신번호 표시제한/안심번호/사내 내선처럼 libphonenumber 가 invalid 로 떨구는
 *   입력도 우리는 최대한 살려서 매칭해야 한다.
 *
 * 해외 번호는 국가번호 경계를 판별하지 않고 숫자열 전체를 그대로 통과시킨다
 * (`+` 표기이거나 국제 접속 프리픽스가 붙어 있을 때만 E.164 를 확정한다).
 */
object PhoneNumberNormalizer {

    private const val KR = "82"

    /** 느슨한 매칭에 쓰는 뒤쪽 자릿수. */
    private const val LOOSE_LEN = 8

    /** 이보다 짧으면 매칭 키로 쓰지 않는다(112, 119 같은 특수번호 오매칭 방지). */
    private const val MIN_LOOSE_LEN = 4

    /** `*23#`, `#31#` 처럼 번호 앞에 붙는 GSM 서비스 코드. */
    private val GSM_PREFIX = Regex("""^[*#]+\d+[*#]+""")

    /** 발신자 표시가 없을 때 통신사/단말이 넣는 문자열들. */
    private val UNKNOWN_TOKENS = setOf(
        "unknown", "private", "restricted", "anonymous", "withheld", "voicemail",
    )

    fun normalize(raw: String?): NormalizedNumber {
        if (raw.isNullOrBlank()) return NormalizedNumber.EMPTY

        val trimmed = raw.trim()
        if (trimmed.lowercase() in UNKNOWN_TOKENS) return NormalizedNumber.EMPTY

        // tel: URI 스킴과 GSM 서비스 코드를 먼저 벗긴다.
        val withoutScheme = trimmed.removePrefix("tel:").removePrefix("TEL:")
        val work = GSM_PREFIX.replace(withoutScheme, "")

        val hadPlus = work.trimStart().startsWith("+")
        val digits = work.filter { it.isDigit() }
        if (digits.isEmpty()) return NormalizedNumber.EMPTY

        val (countryCode, nsn) = split(digits, hadPlus)

        val e164 = if (countryCode != null && nsn.isNotEmpty()) "+$countryCode$nsn" else null
        val loose = when {
            nsn.length >= LOOSE_LEN -> nsn.takeLast(LOOSE_LEN)
            nsn.length >= MIN_LOOSE_LEN -> nsn
            else -> null
        }
        return NormalizedNumber(e164 = e164, loose = loose, digits = digits)
    }

    /** 저장할 때 쓰는 매칭 키 두 개를 한 번에 만든다. */
    fun keysOf(raw: String?): Pair<String?, String?> =
        normalize(raw).let { it.e164 to it.loose }

    /** 화면 표시용. `+821012345678` → `010-1234-5678` */
    fun format(raw: String?): String {
        val e164 = normalize(raw).e164 ?: return raw?.trim().orEmpty()
        if (!e164.startsWith("+$KR")) return e164

        val nsn = e164.removePrefix("+$KR")
        // 1588-1234 같은 8자리 대표번호는 앞에 0 을 붙이지 않는다.
        val isServiceNumber = nsn.length == 8 && nsn.startsWith("1")
        val national = if (nsn.length >= 8 && !isServiceNumber) "0$nsn" else nsn

        return when {
            // 010-1234-5678
            national.length == 11 ->
                "${national.take(3)}-${national.substring(3, 7)}-${national.takeLast(4)}"
            // 02-1234-5678
            national.length == 10 && national.startsWith("02") ->
                "${national.take(2)}-${national.substring(2, 6)}-${national.takeLast(4)}"
            // 031-123-4567 / 011-123-4567
            national.length == 10 ->
                "${national.take(3)}-${national.substring(3, 6)}-${national.takeLast(4)}"
            // 02-123-4567
            national.length == 9 && national.startsWith("02") ->
                "${national.take(2)}-${national.substring(2, 5)}-${national.takeLast(4)}"
            // 1588-1234
            national.length == 8 -> "${national.take(4)}-${national.takeLast(4)}"
            else -> national
        }
    }

    /**
     * 국내 국제전화 사업자 식별 번호. 긴 것부터 검사해야 `007` 이 `00700` 을 가로채지 않는다.
     * 마지막 `00` 은 유럽식 일반 국제 접속 프리픽스 대응.
     */
    private val KR_IDD_PREFIXES =
        listOf("00700", "00365", "00988", "001", "002", "005", "006", "007", "008", "00")

    /** @return (국가번호, 국내유효번호). 국가번호가 null 이면 E.164 를 만들 수 없다는 뜻. */
    private fun split(digits: String, hadPlus: Boolean): Pair<String?, String> {
        if (hadPlus) {
            // `+82…` 는 우리 번호, 그 외 `+` 표기는 국가번호 경계를 따지지 않고 통째로 넘긴다.
            return if (digits.startsWith(KR) && digits.length > 3) {
                KR to stripTrunk(digits.substring(KR.length))
            } else {
                "" to digits
            }
        }

        if (digits.startsWith("00")) {
            // `0082…` 를 먼저 본다. `008`(온세통신) 로 잘려나가면 안 되기 때문.
            if (digits.startsWith("00$KR") && digits.length > 5) {
                return KR to stripTrunk(digits.substring(2 + KR.length))
            }
            // `001-1-415…` 처럼 사업자 번호 뒤에 국가번호가 오는 형태.
            // `0014155…` 는 "00+1" 인지 "001+…" 인지 원리적으로 구분할 수 없다.
            // 이때는 사업자 번호로 해석하며, 어긋나도 loose 키가 받아준다.
            val idd = KR_IDD_PREFIXES.firstOrNull {
                digits.startsWith(it) && digits.length > it.length + 3
            }
            if (idd != null) {
                val rest = digits.substring(idd.length)
                return if (rest.startsWith(KR) && rest.length > 3) {
                    KR to stripTrunk(rest.substring(KR.length))
                } else {
                    "" to rest
                }
            }
        }

        // `0` 으로 시작하는 국내 표기 → 국내 통화용 0 을 떼고 +82 를 붙인다.
        // 011/016/017 같은 옛 이동전화 국번도 여기서 처리된다.
        if (digits.startsWith("0")) return KR to stripTrunk(digits)

        // 0 없이 시작: 15xx/16xx/18xx 대표번호이거나 사내 내선.
        // 8자리 이상이면 국내 번호로 보고, 짧으면 내선으로 보아 E.164 를 만들지 않는다.
        return if (digits.length >= LOOSE_LEN) KR to digits else null to digits
    }

    /** `+82 010-1234-5678` 처럼 국가번호와 국내 0 이 함께 쓰인 표기를 바로잡는다. */
    private fun stripTrunk(nsn: String): String = nsn.trimStart('0')
}
