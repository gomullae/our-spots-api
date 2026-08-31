package com.ourspots.common.notification

import com.ourspots.common.util.RestTemplateFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// 카테고리(식비/생활비)별 지출 합계 + 결제자(진우/초영) 구분 합계
data class CategorySpend(val total: Long, val jinwooTotal: Long, val choyoungTotal: Long)

// 일정 알림용 사람이 읽는 형태로 이미 가공된 값 — ScheduleCategory 등 도메인 타입을 common 계층에 끌어들이지 않기 위해 라벨/날짜 포맷은 호출부(ScheduleService)가 만들어서 넘김
data class ScheduleEventSummary(val title: String, val categoryLabel: String, val dateTimeText: String, val memo: String?)

// 가계 현황(수입/고정비/자산/지출예정액/구독료) 알림용 — HouseholdSectionType/HouseholdPayer 등 도메인
// 타입을 common 계층에 끌어들이지 않기 위해 섹션/대상자 라벨은 호출부(HouseholdBudgetService)가 만들어서
// 넘김. 알림에 노출하는 필드를 이 7개로만 한정해서, 여기 없는 필드(자산구분/자동이체/계좌/예정월 등)의
// 변경은 애초에 감지 대상이 아니게 함(수정 알림은 이 7개 중 실제로 바뀐 게 있을 때만 발송)
data class HouseholdItemSummary(
    val sectionLabel: String,
    val label: String,
    val vendor: String?,
    val amount: Long,
    val payerLabel: String?,
    val debitDay: Int?,
    val memo: String?
)

@Service
class TelegramNotificationService(
    @Value("\${app.telegram.bot-token}") private val botToken: String,
    // 나중에 가계부 알림만 배우자 공동 채팅방으로 분리할 수 있어서 이름을 defaultChatId로 — send()가 override 받을 수 있게 열어둠
    @Value("\${app.telegram.chat-id}") private val defaultChatId: String,
    // 가계부 주간 정산 전용 — 배우자와 공동으로 보는 그룹 채팅방. 비어있으면(설정 전) defaultChatId로 폴백
    @Value("\${app.telegram.expense-chat-id}") private val expenseChatId: String,
    // 일정 등록/수정 알림 전용 — 배우자와 공동으로 보는 그룹 채팅방. 비어있으면(설정 전) defaultChatId로 폴백
    @Value("\${app.telegram.schedule-chat-id}") private val scheduleChatId: String,
    // 기본값을 생성자 파라미터로 열어둬서 테스트에서 mock RestTemplate을 주입할 수 있게 함 (컨텍스트에 RestTemplate 빈이 없으면 Spring이 이 기본값을 그대로 사용)
    private val restTemplate: RestTemplate = RestTemplateFactory.create()
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 비정상 접근 텔레그램 알림 쿨다운용 (IP별 마지막 발송 시각) — DB(access_denied_logs) 기록과는 별개 경로라 여기서만 억제됨
    private val lastAccessDeniedNotifiedAt = ConcurrentHashMap<String, Instant>()

    companion object {
        // 즉시성 알림(방명록/비정상접근/에러)은 폰 알림으로 한눈에 읽을 수 있는 길이로 강제 컷
        private const val MAX_MESSAGE_LENGTH = 300
        // 주간 정산은 본인이 의도적으로 누르는 리포트 성격이라 좀 더 넉넉하게
        private const val WEEKLY_SUMMARY_MAX_LENGTH = 800
        // 동일 IP의 반복 비정상 접근은 이 기간 동안 최초 1건만 알림 (도배 방지, DB 로그는 매번 그대로 기록됨)
        private val ACCESS_DENIED_COOLDOWN = Duration.ofMinutes(10)
    }

    fun notifyNewFeedback(content: String) {
        send("📝 <b>새 방명록</b>\n${escapeHtml(truncate(content, 150))}")
    }

    fun notifyAccessDenied(method: String, path: String, ipAddress: String, message: String?) {
        if (!shouldNotifyAccessDenied(ipAddress)) return
        send(
            "🚨 <b>비정상 접근 감지</b>\n" +
                "${escapeHtml(truncate(method, 10))} ${escapeHtml(truncate(path, 100))}\n" +
                "IP: ${escapeHtml(truncate(ipAddress, 50))}\n" +
                "사유: ${escapeHtml(truncate(message ?: "-", 150))}"
        )
    }

    // 같은 IP는 쿨다운 기간 내 최초 1건만 알림 — 그래도 인지는 해야 하니 완전 차단이 아니라 빈도만 줄임
    private fun shouldNotifyAccessDenied(ipAddress: String): Boolean {
        val now = Instant.now()
        val lastNotifiedAt = lastAccessDeniedNotifiedAt[ipAddress]
        if (lastNotifiedAt != null && Duration.between(lastNotifiedAt, now) < ACCESS_DENIED_COOLDOWN) {
            return false
        }
        lastAccessDeniedNotifiedAt[ipAddress] = now
        return true
    }

    fun notifyServerError(exceptionType: String, method: String, path: String, message: String?) {
        send(
            "🔥 <b>서버 에러 발생</b>\n" +
                "${escapeHtml(truncate(exceptionType, 60))}\n" +
                "${escapeHtml(truncate(method, 10))} ${escapeHtml(truncate(path, 100))}\n" +
                escapeHtml(truncate(message ?: "-", 150))
        )
    }

    fun notifyWeeklyExpenseSummary(
        weekLabel: String,
        budget: Long,
        foodSpend: CategorySpend,
        livingSpend: CategorySpend,
        // 식비/생활비 구분 없이 금액 큰 순으로 이미 정렬·상위 N개로 잘라 넘어옴 (카테고리 라벨, 상호명, 금액)
        topItems: List<Triple<String, String, Long>>,
        irregularTotal: Long,
        irregularItems: List<Pair<String, Long>>
    ) {
        val regularTotal = foodSpend.total + livingSpend.total
        val regularDiff = budget - regularTotal
        val regularLine = if (regularDiff >= 0) "✅ <b>${format(regularDiff)}원 절약</b>" else "⚠️ <b>${format(-regularDiff)}원 초과</b>"

        val totalSpend = regularTotal + irregularTotal
        val totalDiff = budget - totalSpend
        val totalLine = if (totalDiff >= 0) "✅ ${format(totalDiff)}원 절약" else "⚠️ ${format(-totalDiff)}원 초과"

        val sb = StringBuilder()
        sb.append("📅 <b>$weekLabel 주간 정산</b>\n\n")
        sb.append("📊 <b>정기 예산</b>\n")
        sb.append("- 정기 예산 ${format(budget)}원\n")
        sb.append("- 정기 지출 ${format(regularTotal)}원\n")
        sb.append("- 정기 잔액 ${format(regularDiff)}원\n")
        sb.append("$regularLine\n\n")
        sb.append("🎲 <b>비정기 예산</b>\n")
        sb.append("- 비정기 지출 ${format(irregularTotal)}원\n")
        sb.append("- 전체 지출 ${format(totalSpend)}원\n")
        sb.append("$totalLine\n\n")
        sb.append("🗂️ <b>정기 지출 내역</b>\n")
        appendCategorySpend(sb, "식비", foodSpend)
        appendCategorySpend(sb, "생활비", livingSpend)
        sb.append("\n🔍 <b>주요 정기 지출 상세 내역</b>\n")
        topItems.forEachIndexed { i, (category, merchant, amount) ->
            sb.append("${i + 1}. [${escapeHtml(category)}] ${escapeHtml(truncate(merchant, 30))} ${format(amount)}\n")
        }
        if (irregularItems.isNotEmpty()) {
            sb.append("\n🧾 <b>비정기지출 내역</b>\n")
            appendItems(sb, irregularItems)
        }
        sb.append("\nhttps://ourspots.life")

        send(sb.toString().trimEnd(), maxLength = WEEKLY_SUMMARY_MAX_LENGTH, chatId = expenseChatId.ifBlank { defaultChatId })
    }

    fun notifyScheduleCreated(summary: ScheduleEventSummary) {
        val sb = StringBuilder()
        sb.append("🆕 <b>새 일정 등록</b>\n")
        sb.append("제목: ${escapeHtml(truncate(summary.title, 60))}\n")
        sb.append("구분: ${escapeHtml(summary.categoryLabel)}\n")
        sb.append("일시: ${escapeHtml(summary.dateTimeText)}\n")
        summary.memo?.let { sb.append("메모: ${escapeHtml(truncate(it, 150))}\n") }
        send(sb.toString().trimEnd(), chatId = scheduleChatId.ifBlank { defaultChatId })
    }

    // 안 바뀐 필드는 값만, 바뀐 필드는 "이전 → 이후"로 표기 — 아무것도 안 바뀌었으면 알림 자체를 보내지 않음
    fun notifyScheduleUpdated(before: ScheduleEventSummary, after: ScheduleEventSummary) {
        val titleLine = diffText(escapeHtml(truncate(before.title, 60)), escapeHtml(truncate(after.title, 60)))
        val categoryLine = diffText(escapeHtml(before.categoryLabel), escapeHtml(after.categoryLabel))
        val dateTimeLine = diffText(escapeHtml(before.dateTimeText), escapeHtml(after.dateTimeText))
        val memoLine = if (before.memo == null && after.memo == null) {
            null
        } else {
            diffText(
                escapeHtml(truncate(before.memo ?: "(없음)", 150)),
                escapeHtml(truncate(after.memo ?: "(없음)", 150))
            )
        }

        val hasChange = titleLine.changed || categoryLine.changed || dateTimeLine.changed || memoLine?.changed == true
        if (!hasChange) return

        val sb = StringBuilder()
        sb.append("✏️ <b>일정 수정</b>\n")
        sb.append("제목: ${titleLine.text}\n")
        sb.append("구분: ${categoryLine.text}\n")
        sb.append("일시: ${dateTimeLine.text}\n")
        memoLine?.let { sb.append("메모: ${it.text}\n") }
        send(sb.toString().trimEnd(), chatId = scheduleChatId.ifBlank { defaultChatId })
    }

    // 가계 현황 채팅방(가계부 주간 정산과 동일한 expenseChatId) — 금액은 DB엔 암호화해서 저장하지만
    // 알림 메시지엔 당연히 복호화된 실제 숫자가 들어감(가계부 주간 정산도 이미 같은 채널에 실제 금액을 보내고 있어 기존 관례와 동일)
    fun notifyHouseholdItemCreated(summary: HouseholdItemSummary) {
        val sb = StringBuilder()
        sb.append("🆕 <b>가계 현황 추가 [${escapeHtml(summary.sectionLabel)}]</b>\n")
        sb.append("${escapeHtml(truncate(summary.label, 60))} ${format(summary.amount)}원\n")
        summary.vendor?.let { sb.append("업체명: ${escapeHtml(truncate(it, 60))}\n") }
        summary.payerLabel?.let { sb.append("대상자: ${escapeHtml(it)}\n") }
        summary.debitDay?.let { sb.append("이체일: ${it}일\n") }
        summary.memo?.let { sb.append("비고: ${escapeHtml(truncate(it, 150))}\n") }
        send(sb.toString().trimEnd(), chatId = expenseChatId.ifBlank { defaultChatId })
    }

    // 안 바뀐 필드는 값만, 바뀐 필드는 "이전 → 이후"로 표기 — 7개 필드(구분/항목명/업체명/금액/대상자/
    // 이체일/비고) 전부 값이 있으면 항상 표시(변경 여부와 무관, 일정 수정 알림과 동일한 방식), 값 자체가
    // 없는 선택 필드만 그 줄이 생략됨. 이 7개 필드 중 하나도 안 바뀌었으면(즉 여기 없는 자산구분/자동이체/
    // 계좌/예정월만 바뀌었으면) 알림 자체를 보내지 않음
    fun notifyHouseholdItemUpdated(before: HouseholdItemSummary, after: HouseholdItemSummary) {
        val sectionLine = diffText(escapeHtml(before.sectionLabel), escapeHtml(after.sectionLabel))
        val labelLine = diffText(escapeHtml(truncate(before.label, 60)), escapeHtml(truncate(after.label, 60)))
        val amountLine = diffText("${format(before.amount)}원", "${format(after.amount)}원")
        val vendorLine = diffNullableText(before.vendor, after.vendor) { escapeHtml(truncate(it, 60)) }
        val payerLine = diffNullableText(before.payerLabel, after.payerLabel) { escapeHtml(it) }
        val debitDayLine = diffNullableText(before.debitDay?.let { "${it}일" }, after.debitDay?.let { "${it}일" }) { it }
        val memoLine = diffNullableText(before.memo, after.memo) { escapeHtml(truncate(it, 150)) }

        val hasChange = sectionLine.changed || labelLine.changed || amountLine.changed ||
            vendorLine?.changed == true || payerLine?.changed == true || debitDayLine?.changed == true || memoLine?.changed == true
        if (!hasChange) return

        val sb = StringBuilder()
        sb.append("✏️ <b>가계 현황 수정 [${sectionLine.text}]</b>\n")
        sb.append("${labelLine.text}: ${amountLine.text}\n")
        vendorLine?.let { sb.append("업체명: ${it.text}\n") }
        payerLine?.let { sb.append("대상자: ${it.text}\n") }
        debitDayLine?.let { sb.append("이체일: ${it.text}\n") }
        memoLine?.let { sb.append("비고: ${it.text}\n") }
        send(sb.toString().trimEnd(), chatId = expenseChatId.ifBlank { defaultChatId })
    }

    // 소프트 삭제 — 삭제 직전 값을 그대로 알림(일정 관리와 달리 이 도메인은 삭제도 알림 대상)
    fun notifyHouseholdItemDeleted(summary: HouseholdItemSummary) {
        val sb = StringBuilder()
        sb.append("🗑️ <b>가계 현황 삭제 [${escapeHtml(summary.sectionLabel)}]</b>\n")
        sb.append("${escapeHtml(truncate(summary.label, 60))} ${format(summary.amount)}원\n")
        summary.vendor?.let { sb.append("업체명: ${escapeHtml(truncate(it, 60))}\n") }
        summary.payerLabel?.let { sb.append("대상자: ${escapeHtml(it)}\n") }
        summary.debitDay?.let { sb.append("이체일: ${it}일\n") }
        summary.memo?.let { sb.append("비고: ${escapeHtml(truncate(it, 150))}\n") }
        send(sb.toString().trimEnd(), chatId = expenseChatId.ifBlank { defaultChatId })
    }

    private data class DiffResult(val text: String, val changed: Boolean)

    private fun diffText(before: String, after: String): DiffResult =
        if (before == after) DiffResult(before, false) else DiffResult("$before → $after", true)

    // 선택 필드(업체명/대상자/이체일/비고)용 — 일정 수정 알림의 메모 처리와 동일한 규칙: 둘 다 null일
    // 때만 그 줄 자체를 안 만듦(null 반환), 하나라도 값이 있으면 안 바뀌었어도 항상 보여주고(값만 표시)
    // 바뀐 경우만 "이전 → 이후"로 표기(변환 함수는 escapeHtml/truncate 등 후처리용)
    private fun diffNullableText(before: String?, after: String?, transform: (String) -> String = { it }): DiffResult? {
        if (before == null && after == null) return null
        return diffText(transform(before ?: "-"), transform(after ?: "-"))
    }

    private fun appendCategorySpend(sb: StringBuilder, label: String, spend: CategorySpend) {
        sb.append("- $label ${format(spend.total)}원\n")
        sb.append("  ㄴ 진우 결제 ${format(spend.jinwooTotal)}원\n")
        sb.append("  ㄴ 초영 결제 ${format(spend.choyoungTotal)}원\n")
    }

    private fun appendItems(sb: StringBuilder, items: List<Pair<String, Long>>) {
        items.forEachIndexed { i, (merchant, amount) ->
            sb.append("${i + 1}. ${escapeHtml(truncate(merchant, 30))} ${format(amount)}\n")
        }
    }

    private fun format(amount: Long): String = "%,d".format(amount)

    // 발송 실패가 원래 동작(방명록 등록, 예외 처리 등)을 막으면 안 되므로 예외를 삼키고 로그만 남김
    private fun send(text: String, maxLength: Int = MAX_MESSAGE_LENGTH, chatId: String = defaultChatId) {
        // 로컬/테스트 환경처럼 토큰이 설정 안 된 경우 불필요한 외부 호출을 시도하지 않음 (GooglePlaceSyncService.isConfigured()와 동일한 취지)
        if (botToken.isBlank() || chatId.isBlank()) return
        try {
            // 각 조각을 이미 개별적으로 길이 제한했지만, 조합 후에도 maxLength를 넘는 경우를 대비한 안전망.
            // 이 시점의 text는 이미 <b> 등 HTML 태그가 섞여 있어 그대로 자르면 태그 중간이 잘려 깨진 HTML(닫히지 않은 태그)이 될 수 있으므로,
            // 실제로 잘라야 할 때만 태그를 제거한 평문 기준으로 자름 (서식은 포기하더라도 발송 자체가 실패하지 않도록)
            val finalText = if (text.length > maxLength) truncate(stripHtml(text), maxLength) else text
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = mapOf(
                "chat_id" to chatId,
                "text" to finalText,
                "parse_mode" to "HTML"
            )
            restTemplate.postForObject(
                "https://api.telegram.org/bot$botToken/sendMessage",
                HttpEntity(body, headers),
                String::class.java
            )
        } catch (e: Exception) {
            logger.error("Failed to send Telegram notification", e)
        }
    }

    // take(maxLength) + "…"였다면 결과 길이가 maxLength+1이 되는 오프바이원 버그가 있었음 — "…"까지 포함해서 총 길이가 maxLength를 넘지 않게 수정
    private fun truncate(value: String, maxLength: Int): String =
        if (value.length > maxLength) value.take(maxLength - 1) + "…" else value

    private fun stripHtml(value: String): String =
        value.replace(Regex("</?[a-zA-Z][^>]*>"), "")

    // parse_mode=HTML에서는 <, >, & 세 개만 이스케이프하면 됨 (Telegram Bot API Bold/Italic 등 HTML 서식 문서 기준)
    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
