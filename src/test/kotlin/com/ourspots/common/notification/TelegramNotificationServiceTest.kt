package com.ourspots.common.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestTemplate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramNotificationServiceTest {

    private val restTemplate: RestTemplate = mockk(relaxed = true)

    private fun newService(botToken: String = "test-token", chatId: String = "test-chat-id") =
        TelegramNotificationService(botToken, chatId, restTemplate)

    @Suppress("UNCHECKED_CAST")
    private fun capturedText(): String {
        val slot = slot<HttpEntity<Map<String, Any>>>()
        verify { restTemplate.postForObject(any<String>(), capture(slot), String::class.java) }
        return slot.captured.body!!["text"] as String
    }

    @Nested
    @DisplayName("설정 여부에 따른 발송 스킵")
    inner class SkipWhenUnconfigured {

        @Test
        fun notifyNewFeedback_whenBotTokenBlank_shouldNotCallRestTemplate() {
            val service = newService(botToken = "")

            service.notifyNewFeedback("내용")

            verify(exactly = 0) { restTemplate.postForObject(any<String>(), any(), String::class.java) }
        }

        @Test
        fun notifyNewFeedback_whenChatIdBlank_shouldNotCallRestTemplate() {
            val service = newService(chatId = "")

            service.notifyNewFeedback("내용")

            verify(exactly = 0) { restTemplate.postForObject(any<String>(), any(), String::class.java) }
        }
    }

    @Nested
    @DisplayName("notifyNewFeedback")
    inner class NotifyNewFeedback {

        @Test
        fun notifyNewFeedback_shouldEscapeHtmlAndIncludeContent() {
            val service = newService()

            service.notifyNewFeedback("<script>좋아요 & 감사</script>")

            val text = capturedText()
            assertTrue(text.contains("&lt;script&gt;좋아요 &amp; 감사&lt;/script&gt;"))
        }
    }

    @Nested
    @DisplayName("notifyAccessDenied")
    inner class NotifyAccessDenied {

        @Test
        fun notifyAccessDenied_shouldIncludeMethodPathIpAndMessage() {
            val service = newService()

            service.notifyAccessDenied("POST", "/api/weights", "1.2.3.4", "토큰이 없습니다")

            val text = capturedText()
            assertTrue(text.contains("POST"))
            assertTrue(text.contains("/api/weights"))
            assertTrue(text.contains("1.2.3.4"))
            assertTrue(text.contains("토큰이 없습니다"))
        }

        @Test
        fun notifyAccessDenied_whenSameIpWithinCooldown_shouldSuppressSecondNotification() {
            val service = newService()

            service.notifyAccessDenied("POST", "/api/weights", "1.2.3.4", "사유1")
            service.notifyAccessDenied("GET", "/api/expenses", "1.2.3.4", "사유2")

            verify(exactly = 1) { restTemplate.postForObject(any<String>(), any(), String::class.java) }
        }

        @Test
        fun notifyAccessDenied_whenDifferentIp_shouldNotifyBoth() {
            val service = newService()

            service.notifyAccessDenied("POST", "/api/weights", "1.2.3.4", "사유1")
            service.notifyAccessDenied("POST", "/api/weights", "5.6.7.8", "사유2")

            verify(exactly = 2) { restTemplate.postForObject(any<String>(), any(), String::class.java) }
        }
    }

    @Nested
    @DisplayName("notifyServerError")
    inner class NotifyServerError {

        @Test
        fun notifyServerError_shouldIncludeExceptionTypeMethodPathAndMessage() {
            val service = newService()

            service.notifyServerError("RuntimeException", "GET", "/api/places", "DB 연결 실패")

            val text = capturedText()
            assertTrue(text.contains("RuntimeException"))
            assertTrue(text.contains("GET"))
            assertTrue(text.contains("/api/places"))
            assertTrue(text.contains("DB 연결 실패"))
        }

        @Test
        fun notifyServerError_whenMessageNull_shouldUseDashPlaceholder() {
            val service = newService()

            service.notifyServerError("RuntimeException", "GET", "/api/places", null)

            assertTrue(capturedText().contains("-"))
        }
    }

    @Nested
    @DisplayName("notifyWeeklyExpenseSummary")
    inner class NotifyWeeklyExpenseSummary {

        @Test
        fun notifyWeeklyExpenseSummary_whenUnderBudget_shouldShowSavedMessage() {
            val service = newService()

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 500_000,
                foodSpend = CategorySpend(total = 100_000, jinwooTotal = 100_000, choyoungTotal = 0),
                livingSpend = CategorySpend(total = 80_000, jinwooTotal = 80_000, choyoungTotal = 0),
                topItems = listOf(Triple("식비", "이마트", 45_000L), Triple("생활비", "다이소", 80_000L)),
                irregularTotal = 0,
                irregularItems = emptyList()
            )

            val text = capturedText()
            assertTrue(text.contains("✅"))
            assertTrue(text.contains("320,000원 절약"))
            assertFalse(text.contains("초과"))
        }

        @Test
        fun notifyWeeklyExpenseSummary_whenOverBudget_shouldShowExceededMessage() {
            val service = newService()

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 100_000,
                foodSpend = CategorySpend(total = 80_000, jinwooTotal = 80_000, choyoungTotal = 0),
                livingSpend = CategorySpend(total = 50_000, jinwooTotal = 50_000, choyoungTotal = 0),
                topItems = listOf(Triple("식비", "이마트", 80_000L), Triple("생활비", "다이소", 50_000L)),
                irregularTotal = 0,
                irregularItems = emptyList()
            )

            val text = capturedText()
            assertTrue(text.contains("⚠️"))
            assertTrue(text.contains("30,000원 초과"))
        }

        @Test
        fun notifyWeeklyExpenseSummary_shouldIncludeJinwooAndChoyoungBreakdownPerCategory() {
            val service = newService()

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 500_000,
                foodSpend = CategorySpend(total = 100_000, jinwooTotal = 82_000, choyoungTotal = 18_000),
                livingSpend = CategorySpend(total = 80_000, jinwooTotal = 60_000, choyoungTotal = 20_000),
                topItems = emptyList(),
                irregularTotal = 0,
                irregularItems = emptyList()
            )

            val text = capturedText()
            assertTrue(text.contains("- 식비 100,000원"))
            assertTrue(text.contains("  ㄴ 진우 결제 82,000원"))
            assertTrue(text.contains("  ㄴ 초영 결제 18,000원"))
            assertTrue(text.contains("- 생활비 80,000원"))
            assertTrue(text.contains("  ㄴ 진우 결제 60,000원"))
            assertTrue(text.contains("  ㄴ 초영 결제 20,000원"))
        }

        @Test
        fun notifyWeeklyExpenseSummary_shouldIncludeCategoryTaggedTopItemsInOrder() {
            val service = newService()

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 500_000,
                foodSpend = CategorySpend(total = 100_000, jinwooTotal = 100_000, choyoungTotal = 0),
                livingSpend = CategorySpend(total = 80_000, jinwooTotal = 80_000, choyoungTotal = 0),
                topItems = listOf(
                    Triple("생활비", "다이소", 80_000L),
                    Triple("식비", "이마트", 45_000L)
                ),
                irregularTotal = 0,
                irregularItems = emptyList()
            )

            val text = capturedText()
            assertTrue(text.contains("1. [생활비] 다이소 80,000"))
            assertTrue(text.contains("2. [식비] 이마트 45,000"))
        }

        @Test
        fun notifyWeeklyExpenseSummary_whenNoIrregularItems_shouldOmitIrregularSection() {
            val service = newService()

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 500_000,
                foodSpend = CategorySpend(total = 100_000, jinwooTotal = 100_000, choyoungTotal = 0),
                livingSpend = CategorySpend(total = 0, jinwooTotal = 0, choyoungTotal = 0),
                topItems = listOf(Triple("식비", "이마트", 100_000L)),
                irregularTotal = 0,
                irregularItems = emptyList()
            )

            assertFalse(capturedText().contains("비정기지출 내역"))
        }

        @Test
        fun notifyWeeklyExpenseSummary_whenMessageExceedsMaxLength_shouldStripHtmlBeforeTruncating() {
            val service = newService()
            // 병합 시 800자를 넘도록 긴 항목을 다수 포함
            val manyItems = (1..50).map { "아주아주아주긴가맹점이름$it" to 10_000L }

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 500_000,
                foodSpend = CategorySpend(total = 500_000, jinwooTotal = 500_000, choyoungTotal = 0),
                livingSpend = CategorySpend(total = 0, jinwooTotal = 0, choyoungTotal = 0),
                topItems = manyItems.take(5).map { Triple("식비", it.first, it.second) },
                irregularTotal = 0,
                irregularItems = manyItems
            )

            val text = capturedText()
            // 안전망 truncate가 발동하면 태그가 통째로 제거되므로 닫히지 않은 태그가 남지 않아야 함
            assertFalse(text.contains("<b>") || text.contains("</b>"))
            assertTrue(text.length <= 800)
        }
    }
}
