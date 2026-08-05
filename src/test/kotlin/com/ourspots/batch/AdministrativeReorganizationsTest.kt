package com.ourspots.batch

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdministrativeReorganizationsTest {

    @Nested
    @DisplayName("isTarget")
    inner class IsTarget {

        @Test
        fun isTarget_whenIncheonSeogu_shouldBeTrue() {
            assertTrue(AdministrativeReorganizations.isTarget("인천 서구 원당대로 861"))
        }

        @Test
        fun isTarget_whenIncheonJunggu_shouldBeTrue() {
            assertTrue(AdministrativeReorganizations.isTarget("인천 중구 운북동 1264-1"))
        }

        @Test
        fun isTarget_whenIncheonDonggu_shouldBeTrue() {
            assertTrue(AdministrativeReorganizations.isTarget("인천 동구 화도진로 1"))
        }

        @Test
        fun isTarget_whenGwangju_shouldBeTrue() {
            assertTrue(AdministrativeReorganizations.isTarget("광주 서구 상무대로 653"))
        }

        @Test
        fun isTarget_whenJeonnam_shouldBeTrue() {
            assertTrue(AdministrativeReorganizations.isTarget("전남 해남군 송지면 땅끝해안로 2286"))
        }

        @Test
        fun isTarget_whenIncheonUnaffectedGu_shouldBeFalse() {
            // 부평구는 이번 개편과 무관 — 대상에 섞이면 안 됨
            assertFalse(AdministrativeReorganizations.isTarget("인천 부평구 굴포로 1"))
        }

        @Test
        fun isTarget_whenSeoulGangseogu_shouldBeFalse() {
            // "강서구"에 "서구"가 부분 문자열로 들어있는 오탐 함정
            assertFalse(AdministrativeReorganizations.isTarget("서울 강서구 방화대로21길 85"))
        }

        @Test
        fun isTarget_whenGoyangIlsanSeogu_shouldBeFalse() {
            // "일산서구"도 "서구"를 포함하는 오탐 함정
            assertFalse(AdministrativeReorganizations.isTarget("경기 고양시 일산서구 킨텍스로 217-59"))
        }

        @Test
        fun isTarget_whenAlreadyUpdatedToGeomdangu_shouldBeFalse() {
            // 이미 개편 후 이름이면 재실행해도 다시 대상에 안 잡혀야 함 (idempotency)
            assertFalse(AdministrativeReorganizations.isTarget("인천 검단구 원당대로 861"))
        }

        @Test
        fun isTarget_whenAlreadyUpdatedToJeonnamGwangju_shouldBeFalse() {
            assertFalse(AdministrativeReorganizations.isTarget("전남광주통합특별시 해남군 송지면 땅끝해안로 2286"))
        }
    }

    @Nested
    @DisplayName("buildUpdatedAddress - 인천")
    inner class Incheon {

        @Test
        fun buildUpdatedAddress_whenSeoguSplitsToGeomdangu_shouldReplacePrefixOnly() {
            // 시천동은 서구/검단구로 쪼개진 동이라 텍스트만으론 판별 불가했던 실제 사례
            val original = "인천 서구 아라로105번길 17 (시천동)"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "인천", "검단구")

            assertEquals("인천 검단구 아라로105번길 17 (시천동)", result)
        }

        @Test
        fun buildUpdatedAddress_whenSeoguStaysSeohaegu_shouldReplacePrefixOnly() {
            // 오류동인데 아라뱃길 이남 직관과 달리 검단구로 판정됐던 것과 대비되는, 서해구로 남는 사례
            val original = "인천 서구 청라동 1-794"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "인천", "서해구")

            assertEquals("인천 서해구 청라동 1-794", result)
        }

        @Test
        fun buildUpdatedAddress_whenJunggu_shouldMapToYeongjongguOrJemulpogu() {
            val yeongjong = "인천 중구 운북동 1264-1"
            val jemulpo = "인천 중구 차이나타운로 56-14"

            assertEquals(
                "인천 영종구 운북동 1264-1",
                AdministrativeReorganizations.buildUpdatedAddress(yeongjong, "인천", "영종구")
            )
            assertEquals(
                "인천 제물포구 차이나타운로 56-14",
                AdministrativeReorganizations.buildUpdatedAddress(jemulpo, "인천", "제물포구")
            )
        }

        @Test
        fun buildUpdatedAddress_whenDonggu_shouldMapToJemulpogu() {
            val original = "인천 동구 화도진로 1"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "인천", "제물포구")

            assertEquals("인천 제물포구 화도진로 1", result)
        }

        @Test
        fun buildUpdatedAddress_shouldPreserveBuildingAndFloorSuffix() {
            // 도로명 뒤 건물명/층/호수 정보는 손대면 안 됨
            val original = "인천 서구 원당대로 1029 1층 113호 (원당동)"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "인천", "검단구")

            assertEquals("인천 검단구 원당대로 1029 1층 113호 (원당동)", result)
        }
    }

    @Nested
    @DisplayName("buildUpdatedAddress - 광주/전남 통합")
    inner class GwangjuJeonnam {

        @Test
        fun buildUpdatedAddress_whenGwangju_shouldReplaceRegion1OnlyAndKeepGu() {
            // 자치구 이름은 그대로, 최상위 시/도 이름만 바뀌는 케이스
            val original = "광주 서구 상무대로 653 다스리가구백화점 1층 (마륵동)"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "전남광주통합특별시", "서구")

            assertEquals("전남광주통합특별시 서구 상무대로 653 다스리가구백화점 1층 (마륵동)", result)
        }

        @Test
        fun buildUpdatedAddress_whenJeonnam_shouldReplaceRegion1OnlyAndKeepSigungu() {
            val original = "전남 해남군 삼산면 대흥사길 88-30 (삼산면 구림리)"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "전남광주통합특별시", "해남군")

            assertEquals("전남광주통합특별시 해남군 삼산면 대흥사길 88-30 (삼산면 구림리)", result)
        }
    }

    @Nested
    @DisplayName("buildUpdatedAddress - 변경 불필요/불가 케이스")
    inner class NoChange {

        @Test
        fun buildUpdatedAddress_whenAddressAlreadyUpdated_shouldReturnNull() {
            // 이미 개편 후 이름으로 저장돼 있으면 카카오가 같은 값을 다시 돌려줌
            val original = "인천 검단구 아라로105번길 17 (시천동)"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "인천", "검단구")

            assertNull(result)
        }

        @Test
        fun buildUpdatedAddress_whenAddressHasNoRecognizedPrefix_shouldReturnNull() {
            // 이번 개편과 무관한 지역
            val original = "서울 강남구 테헤란로 1"

            val result = AdministrativeReorganizations.buildUpdatedAddress(original, "서울", "강남구")

            assertNull(result)
        }
    }
}
