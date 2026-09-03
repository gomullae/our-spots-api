package com.ourspots.common.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// 생활비 내역(household_budget_items 등)의 금액 컬럼 전용 — DB를 직접 열어봐도 숫자를 못 읽게
// AES-256-GCM으로 암호화해서 저장. 키(HOUSEHOLD_ENCRYPTION_KEY)는 DB가 아니라 서버 .env에만 있어서,
// DB만 유출돼도(비밀번호 유출/Supabase 침해 등) 키 없이는 복호화 불가능.
// @Component + @Converter(autoApply=false)로 등록하면 Hibernate가 Spring 빈 컨테이너를 통해 이 컨버터를
// 가져와서 @Value 주입이 가능해짐(Spring Boot 2.5+/Hibernate 5.3+ 표준 지원) — 각 엔티티 필드에는
// @Convert(converter = EncryptedLongConverter::class)로 명시 적용
@Component
@Converter(autoApply = false)
class EncryptedLongConverter(
    @Value("\${app.household.encryption-key}") private val encryptionKeyBase64: String
) : AttributeConverter<Long, String> {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val secureRandom = SecureRandom()

    // JWT_SECRET과 동일하게 "키가 없거나 잘못되면 앱 시작 자체를 막는" fail-fast 방식 — 이 컨버터가 적용된
    // 컬럼이 있는 이상 키 없이 조용히 돌아가다가 나중에 런타임 에러로 터지는 것보다 배포 시점에 바로 드러나는 게 안전함.
    // init 블록에서 나중에 대입하는 대신 프로퍼티를 직접 초기화 — Spring 프록시용으로 open된 클래스에서
    // val을 init 블록에서만 대입하면 "초기화/final/abstract 중 하나여야 함" 경고가 남(향후 Kotlin에서 에러로 승격 예정)
    private val secretKey: SecretKeySpec = createSecretKey(encryptionKeyBase64)

    private fun createSecretKey(base64Key: String): SecretKeySpec {
        require(base64Key.isNotBlank()) {
            "HOUSEHOLD_ENCRYPTION_KEY가 설정되지 않았습니다. openssl rand -base64 32 로 생성해서 .env에 추가하세요."
        }
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == 32) {
            "HOUSEHOLD_ENCRYPTION_KEY는 32바이트(AES-256, base64 인코딩)여야 합니다."
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    override fun convertToDatabaseColumn(attribute: Long?): String? {
        if (attribute == null) return null
        // GCM은 매 암호화마다 새 IV(nonce)가 필수 — 같은 IV를 재사용하면 보안이 깨짐. IV 자체는 비밀이 아니라서
        // 암호문 앞에 그냥 붙여서 하나의 base64 문자열로 저장(복호화 시 앞 12바이트를 다시 잘라냄)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(attribute.toString().toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    override fun convertToEntityAttribute(dbData: String?): Long? {
        if (dbData.isNullOrBlank()) return null
        try {
            val combined = Base64.getDecoder().decode(dbData)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8).toLong()
        } catch (e: Exception) {
            // 원본 예외를 cause로 그대로 물려주지 않음 — 예: 손상된 암호문이 우연히 복호화되다 숫자 변환에
            // 실패하면 NumberFormatException 메시지에 그 조각(잠재적으로 평문 일부)이 그대로 실릴 수 있고,
            // 이 예외는 GlobalExceptionHandler가 error_logs 테이블(관리자 백업 다운로드 대상)과 서버 로그에
            // 그대로 남기므로 고정 메시지만 던져서 원천 차단
            throw IllegalStateException("가계 현황 금액 복호화에 실패했습니다.")
        }
    }
}
