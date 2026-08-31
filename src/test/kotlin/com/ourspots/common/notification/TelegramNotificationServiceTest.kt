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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramNotificationServiceTest {

    private val restTemplate: RestTemplate = mockk(relaxed = true)

    private fun newService(
        botToken: String = "test-token",
        chatId: String = "test-chat-id",
        expenseChatId: String = "",
        scheduleChatId: String = ""
    ) = TelegramNotificationService(botToken, chatId, expenseChatId, scheduleChatId, restTemplate)

    @Suppress("UNCHECKED_CAST")
    private fun capturedBody(): Map<String, Any> {
        val slot = slot<HttpEntity<Map<String, Any>>>()
        verify { restTemplate.postForObject(any<String>(), capture(slot), String::class.java) }
        return slot.captured.body!!
    }

    private fun capturedText(): String = capturedBody()["text"] as String

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
        fun notifyWeeklyExpenseSummary_whenExpenseChatIdConfigured_shouldSendThere() {
            val service = newService(chatId = "default-chat", expenseChatId = "expense-group-chat")

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 500_000,
                foodSpend = CategorySpend(total = 0, jinwooTotal = 0, choyoungTotal = 0),
                livingSpend = CategorySpend(total = 0, jinwooTotal = 0, choyoungTotal = 0),
                topItems = emptyList(),
                irregularTotal = 0,
                irregularItems = emptyList()
            )

            assertEquals("expense-group-chat", capturedBody()["chat_id"])
        }

        @Test
        fun notifyWeeklyExpenseSummary_whenExpenseChatIdBlank_shouldFallBackToDefaultChat() {
            val service = newService(chatId = "default-chat", expenseChatId = "")

            service.notifyWeeklyExpenseSummary(
                weekLabel = "8/17~8/23",
                budget = 500_000,
                foodSpend = CategorySpend(total = 0, jinwooTotal = 0, choyoungTotal = 0),
                livingSpend = CategorySpend(total = 0, jinwooTotal = 0, choyoungTotal = 0),
                topItems = emptyList(),
                irregularTotal = 0,
                irregularItems = emptyList()
            )

            assertEquals("default-chat", capturedBody()["chat_id"])
        }

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

    @Nested
    @DisplayName("notifyScheduleCreated")
    inner class NotifyScheduleCreated {

        @Test
        fun notifyScheduleCreated_shouldIncludeTitleCategoryAndDateTime() {
            val service = newService()

            service.notifyScheduleCreated(ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", null))

            val text = capturedText()
            assertTrue(text.contains("새 일정 등록"))
            assertTrue(text.contains("커피약속"))
            assertTrue(text.contains("진우 일정"))
            assertTrue(text.contains("8월 15일(토) 오후 12:00"))
        }

        @Test
        fun notifyScheduleCreated_whenMemoNull_shouldOmitMemoLine() {
            val service = newService()

            service.notifyScheduleCreated(ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", null))

            assertFalse(capturedText().contains("메모"))
        }

        @Test
        fun notifyScheduleCreated_whenScheduleChatIdConfigured_shouldSendThere() {
            val service = newService(chatId = "default-chat", scheduleChatId = "schedule-group-chat")

            service.notifyScheduleCreated(ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", null))

            assertEquals("schedule-group-chat", capturedBody()["chat_id"])
        }

        @Test
        fun notifyScheduleCreated_whenScheduleChatIdBlank_shouldFallBackToDefaultChat() {
            val service = newService(chatId = "default-chat", scheduleChatId = "")

            service.notifyScheduleCreated(ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", null))

            assertEquals("default-chat", capturedBody()["chat_id"])
        }
    }

    @Nested
    @DisplayName("notifyScheduleUpdated")
    inner class NotifyScheduleUpdated {

        @Test
        fun notifyScheduleUpdated_whenTitleChanged_shouldShowArrow() {
            val service = newService()
            val before = ScheduleEventSummary("곤충이야기 체험", "공유 일정", "8월 15일(토) 오후 12:00", null)
            val after = ScheduleEventSummary("벌레 관찰 체험", "공유 일정", "8월 15일(토) 오후 12:00", null)

            service.notifyScheduleUpdated(before, after)

            val text = capturedText()
            assertTrue(text.contains("제목: 곤충이야기 체험 → 벌레 관찰 체험"))
            assertTrue(text.contains("구분: 공유 일정"))
            assertFalse(text.contains("구분: 공유 일정 → 공유 일정"))
        }

        @Test
        fun notifyScheduleUpdated_whenNothingChanged_shouldNotSend() {
            val service = newService()
            val summary = ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", null)

            service.notifyScheduleUpdated(summary, summary.copy())

            verify(exactly = 0) { restTemplate.postForObject(any<String>(), any(), String::class.java) }
        }

        @Test
        fun notifyScheduleUpdated_whenMemoAdded_shouldShowPlaceholderForBefore() {
            val service = newService()
            val before = ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", null)
            val after = before.copy(memo = "1시간반")

            service.notifyScheduleUpdated(before, after)

            assertTrue(capturedText().contains("메모: (없음) → 1시간반"))
        }

        @Test
        fun notifyScheduleUpdated_whenMemoUnchanged_shouldShowPlainValue() {
            val service = newService()
            val before = ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", "메모")
            val after = before.copy(title = "변경된 제목")

            service.notifyScheduleUpdated(before, after)

            val text = capturedText()
            assertTrue(text.contains("메모: 메모\n") || text.trimEnd().endsWith("메모: 메모"))
            assertFalse(text.contains("메모: 메모 →"))
        }

        @Test
        fun notifyScheduleUpdated_whenMemoRemoved_shouldShowPlaceholderForAfter() {
            val service = newService()
            val before = ScheduleEventSummary("커피약속", "진우 일정", "8월 15일(토) 오후 12:00", "1시간반")
            val after = before.copy(memo = null)

            service.notifyScheduleUpdated(before, after)

            assertTrue(capturedText().contains("메모: 1시간반 → (없음)"))
        }
    }

    // 테스트 전반에서 label/amount만 바뀌고 나머지는 그대로인 기본 요약을 자주 써서 헬퍼로 뺌
    private fun fixedCostSummary(
        label: String = "통신비",
        vendor: String? = "SKT",
        amount: Long = 33_250L,
        payerLabel: String? = "초영",
        debitDay: Int? = 10,
        memo: String? = null
    ) = HouseholdItemSummary(
        sectionLabel = "고정비",
        label = label,
        vendor = vendor,
        amount = amount,
        payerLabel = payerLabel,
        debitDay = debitDay,
        memo = memo
    )

    @Nested
    @DisplayName("notifyHouseholdItemCreated")
    inner class NotifyHouseholdItemCreated {

        @Test
        fun notifyHouseholdItemCreated_shouldIncludeAllFields() {
            val service = newService()

            service.notifyHouseholdItemCreated(fixedCostSummary(memo = "메모입니다"))

            val text = capturedText()
            assertTrue(text.contains("가계 현황 추가"))
            assertTrue(text.contains("[고정비]"))
            assertTrue(text.contains("통신비 33,250원"))
            assertTrue(text.contains("업체명: SKT"))
            assertTrue(text.contains("대상자: 초영"))
            assertTrue(text.contains("이체일: 10일"))
            assertTrue(text.contains("비고: 메모입니다"))
        }

        @Test
        fun notifyHouseholdItemCreated_whenOptionalFieldsMissing_shouldOmitThoseLines() {
            val service = newService()

            service.notifyHouseholdItemCreated(
                HouseholdItemSummary(
                    sectionLabel = "수입",
                    label = "급여",
                    vendor = null,
                    amount = 5_700_000L,
                    payerLabel = null,
                    debitDay = null,
                    memo = null
                )
            )

            val text = capturedText()
            assertFalse(text.contains("대상자:"))
            assertFalse(text.contains("이체일:"))
            assertFalse(text.contains("비고:"))
        }

        @Test
        fun notifyHouseholdItemCreated_whenExpenseChatIdConfigured_shouldSendThere() {
            val service = newService(chatId = "default-chat", expenseChatId = "expense-group-chat")

            service.notifyHouseholdItemCreated(
                HouseholdItemSummary(
                    sectionLabel = "수입",
                    label = "급여",
                    vendor = null,
                    amount = 5_700_000L,
                    payerLabel = null,
                    debitDay = null,
                    memo = null
                )
            )

            assertEquals("expense-group-chat", capturedBody()["chat_id"])
        }
    }

    @Nested
    @DisplayName("notifyHouseholdItemUpdated")
    inner class NotifyHouseholdItemUpdated {

        @Test
        fun notifyHouseholdItemUpdated_whenAmountChanged_shouldShowArrow() {
            val service = newService()

            service.notifyHouseholdItemUpdated(fixedCostSummary(amount = 33_250L), fixedCostSummary(amount = 35_000L))

            val text = capturedText()
            assertTrue(text.contains("통신비: 33,250원 → 35,000원"))
        }

        @Test
        fun notifyHouseholdItemUpdated_whenOnlyAmountChanged_shouldStillShowUnchangedFields() {
            // 안 바뀐 필드도 값이 있으면 항상 보여줌(일정 수정 알림과 동일한 방식) — 바뀐 필드만
            // 골라 보여주지 않고, 어떤 항목들이 있는지 전체 맥락을 같이 보여달라는 요청 반영
            val service = newService()

            service.notifyHouseholdItemUpdated(fixedCostSummary(amount = 33_250L), fixedCostSummary(amount = 35_000L))

            val text = capturedText()
            assertTrue(text.contains("업체명: SKT"))
            assertTrue(text.contains("대상자: 초영"))
            assertTrue(text.contains("이체일: 10일"))
        }

        @Test
        fun notifyHouseholdItemUpdated_whenBothMemoNull_shouldOmitMemoLine() {
            val service = newService()

            service.notifyHouseholdItemUpdated(fixedCostSummary(memo = null), fixedCostSummary(amount = 35_000L, memo = null))

            assertFalse(capturedText().contains("비고:"))
        }

        @Test
        fun notifyHouseholdItemUpdated_whenNothingChanged_shouldNotSend() {
            val service = newService()
            val summary = fixedCostSummary()

            service.notifyHouseholdItemUpdated(summary, summary.copy())

            verify(exactly = 0) { restTemplate.postForObject(any<String>(), any(), String::class.java) }
        }

        @Test
        fun notifyHouseholdItemUpdated_whenVendorChanged_shouldShowArrow() {
            val service = newService()

            service.notifyHouseholdItemUpdated(fixedCostSummary(vendor = "SKT"), fixedCostSummary(vendor = "KT"))

            assertTrue(capturedText().contains("업체명: SKT → KT"))
        }

        @Test
        fun notifyHouseholdItemUpdated_whenPayerChanged_shouldShowArrow() {
            val service = newService()

            service.notifyHouseholdItemUpdated(fixedCostSummary(payerLabel = "초영"), fixedCostSummary(payerLabel = "진우"))

            assertTrue(capturedText().contains("대상자: 초영 → 진우"))
        }

        @Test
        fun notifyHouseholdItemUpdated_whenDebitDayChanged_shouldShowArrow() {
            val service = newService()

            service.notifyHouseholdItemUpdated(fixedCostSummary(debitDay = 10), fixedCostSummary(debitDay = 15))

            assertTrue(capturedText().contains("이체일: 10일 → 15일"))
        }

        @Test
        fun notifyHouseholdItemUpdated_whenMemoChanged_shouldShowArrow() {
            val service = newService()

            service.notifyHouseholdItemUpdated(fixedCostSummary(memo = null), fixedCostSummary(memo = "새 메모"))

            assertTrue(capturedText().contains("비고: - → 새 메모"))
        }
    }

    @Nested
    @DisplayName("notifyHouseholdItemDeleted")
    inner class NotifyHouseholdItemDeleted {

        @Test
        fun notifyHouseholdItemDeleted_shouldIncludeAllFields() {
            val service = newService()

            service.notifyHouseholdItemDeleted(
                HouseholdItemSummary(
                    sectionLabel = "구독료",
                    label = "티빙몰 구독",
                    vendor = null,
                    amount = 9_850L,
                    payerLabel = null,
                    debitDay = 6,
                    memo = "매년 6/7 결제"
                )
            )

            val text = capturedText()
            assertTrue(text.contains("가계 현황 삭제"))
            assertTrue(text.contains("[구독료]"))
            assertTrue(text.contains("티빙몰 구독 9,850원"))
            assertTrue(text.contains("이체일: 6일"))
            assertTrue(text.contains("비고: 매년 6/7 결제"))
        }
    }
}
