package com.ourspots.common.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Service
class TelegramNotificationService(
    @Value("\${app.telegram.bot-token}") private val botToken: String,
    // 나중에 가계부 알림만 배우자 공동 채팅방으로 분리할 수 있어서 이름을 defaultChatId로 — send()가 override 받을 수 있게 열어둠
    @Value("\${app.telegram.chat-id}") private val defaultChatId: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate().apply {
        requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(5))
        }
    }

    companion object {
        // 즉시성 알림(방명록/비정상접근/에러)은 폰 알림으로 한눈에 읽을 수 있는 길이로 강제 컷
        private const val MAX_MESSAGE_LENGTH = 300
        // 주간 정산은 본인이 의도적으로 누르는 리포트 성격이라 좀 더 넉넉하게
        private const val WEEKLY_SUMMARY_MAX_LENGTH = 800
    }

    fun notifyNewFeedback(content: String) {
        send("📝 <b>새 방명록</b>\n${escapeHtml(truncate(content, 150))}")
    }

    fun notifyAccessDenied(method: String, path: String, ipAddress: String, message: String?) {
        send(
            "🚨 <b>비정상 접근 감지</b>\n" +
                "${escapeHtml(truncate(method, 10))} ${escapeHtml(truncate(path, 100))}\n" +
                "IP: ${escapeHtml(truncate(ipAddress, 50))}\n" +
                "사유: ${escapeHtml(truncate(message ?: "-", 150))}"
        )
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
        foodTotal: Long,
        foodTop3: List<Pair<String, Long>>,
        livingTotal: Long,
        livingTop3: List<Pair<String, Long>>,
        irregularTotal: Long,
        irregularItems: List<Pair<String, Long>>
    ) {
        val regularTotal = foodTotal + livingTotal
        val regularDiff = budget - regularTotal
        val regularLine = if (regularDiff >= 0) "✅ <b>${format(regularDiff)}원 절약</b>" else "⚠️ <b>${format(-regularDiff)}원 초과</b>"

        val totalDiff = budget - (regularTotal + irregularTotal)
        val totalLine = if (totalDiff >= 0) {
            "✅ ${format(totalDiff)}원 절약 (비정기지출 포함시)"
        } else {
            "⚠️ ${format(-totalDiff)}원 초과 (비정기지출 포함시)"
        }

        val sb = StringBuilder()
        sb.append("📅 <b>$weekLabel 주간 정산</b>\n\n")
        sb.append("📊 <b>정기 예산</b>\n")
        sb.append("정기 예산 ${format(budget)}원\n")
        sb.append("정기 지출 ${format(regularTotal)}원\n")
        sb.append("정기 잔액 ${format(regularDiff)}원\n")
        sb.append("$regularLine\n\n")
        sb.append("🎲 <b>비정기 예산</b>\n")
        sb.append("비정기 지출 ${format(irregularTotal)}원\n")
        sb.append("$totalLine\n\n")
        sb.append("📋 <b>정기 지출 주요 내역</b>\n")
        sb.append("[식비] ${format(foodTotal)}원\n")
        appendItems(sb, foodTop3)
        sb.append("\n[생활비] ${format(livingTotal)}원\n")
        appendItems(sb, livingTop3)
        if (irregularItems.isNotEmpty()) {
            sb.append("\n🧾 <b>비정기지출 내역</b>\n")
            appendItems(sb, irregularItems)
        }
        sb.append("\nhttps://ourspots.life")

        send(sb.toString().trimEnd(), maxLength = WEEKLY_SUMMARY_MAX_LENGTH)
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
            val finalText = truncate(text, maxLength)
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

    private fun truncate(value: String, maxLength: Int): String =
        if (value.length > maxLength) value.take(maxLength) + "…" else value

    // parse_mode=HTML에서는 <, >, & 세 개만 이스케이프하면 됨 (Telegram Bot API Bold/Italic 등 HTML 서식 문서 기준)
    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
