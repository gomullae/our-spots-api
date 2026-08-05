package com.ourspots.batch

/**
 * 시행된 행정구역 개편 이력을 모아두는 단일 소스.
 * UpdateReorganizedAddressRunner가 이 목록으로 대상 장소를 걸러내고(isTarget) 새 주소를
 * 조립한다(buildUpdatedAddress). 나중에 다른 지역 개편이 실제 시행되면 이 파일의 [ALL]에
 * 항목만 추가하면 된다 — 좌표만으로는 대상 여부를 판단할 수 없어서(전국 어디든 좌표는 있음),
 * "주소에 개편 전 이름이 남아있는가"가 유일한 판단 기준이라 이렇게 목록으로 관리한다.
 */
object AdministrativeReorganizations {

    /**
     * @param sourceRegion1Names 개편 전 시/도 이름의 표기 변형 (예: "인천", "인천광역시")
     * @param sourceRegion2Names 개편 전 구/시/군 이름 제한. null이면 해당 시/도 전체가 대상
     *   (광주-전남 통합처럼 자치구/시/군 이름은 그대로 두고 시/도 이름만 바뀌는 경우)
     */
    data class Reorganization(
        val effectiveDate: String,
        val description: String,
        val sourceRegion1Names: List<String>,
        val sourceRegion2Names: List<String>? = null
    )

    val ALL = listOf(
        Reorganization(
            effectiveDate = "2026-07-01",
            description = "인천 서구 분구(서구->서해구/검단구), 중구+동구 재편(->영종구/제물포구)",
            sourceRegion1Names = listOf("인천", "인천광역시"),
            sourceRegion2Names = listOf("서구", "중구", "동구")
        ),
        Reorganization(
            effectiveDate = "2026-07-01",
            description = "광주광역시-전라남도 통합 (전남광주통합특별시)",
            sourceRegion1Names = listOf("광주", "광주광역시", "전남", "전라남도")
        )
    )

    private val PREFIX_REGEX = run {
        val region1Alt = ALL.flatMap { it.sourceRegion1Names }.distinct().joinToString("|")
        Regex("^($region1Alt)\\s+([가-힣0-9]+(?:구|시|군))\\s+")
    }

    fun isTarget(address: String): Boolean {
        val match = PREFIX_REGEX.find(address) ?: return false
        val region1 = match.groupValues[1]
        val region2 = match.groupValues[2]
        return ALL.any { reorg ->
            region1 in reorg.sourceRegion1Names &&
                (reorg.sourceRegion2Names == null || region2 in reorg.sourceRegion2Names)
        }
    }

    /** 주소 접두어(시/도+구시군)만 새 이름으로 교체하고 도로명/번지/건물명/괄호 동 표기는 보존한다. */
    fun buildUpdatedAddress(original: String, newRegion1: String, newRegion2: String): String? {
        val match = PREFIX_REGEX.find(original) ?: return null
        val rest = original.substring(match.range.last + 1)
        val newAddress = "$newRegion1 $newRegion2 $rest"
        return if (newAddress == original) null else newAddress
    }
}
