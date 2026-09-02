package com.ourspots.common.crypto

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class EncryptedLongConverterTest {

    // 테스트 전용 키(application-test.yaml과 무관하게 이 테스트 파일이 직접 생성) — 32바이트(AES-256) base64
    private val validKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val converter = EncryptedLongConverter(validKey)

    @Nested
    @DisplayName("암복호화 라운드트립")
    inner class RoundTrip {

        @Test
        fun convert_whenPositiveAmount_shouldRoundTripToSameValue() {
            val encrypted = converter.convertToDatabaseColumn(35000L)
            val decrypted = converter.convertToEntityAttribute(encrypted)

            assertEquals(35000L, decrypted)
        }

        @Test
        fun convert_whenZero_shouldRoundTripToSameValue() {
            val encrypted = converter.convertToDatabaseColumn(0L)
            assertEquals(0L, converter.convertToEntityAttribute(encrypted))
        }

        @Test
        fun convert_whenLargeAmount_shouldRoundTripToSameValue() {
            val encrypted = converter.convertToDatabaseColumn(631_000_000L)
            assertEquals(631_000_000L, converter.convertToEntityAttribute(encrypted))
        }

        @Test
        fun convert_whenNull_shouldReturnNull() {
            assertNull(converter.convertToDatabaseColumn(null))
            assertNull(converter.convertToEntityAttribute(null))
        }

        // GCM은 매번 새 IV(nonce)를 써야 하므로, 같은 평문이라도 암호문은 매번 달라야 함 —
        // 같으면 IV가 고정돼있다는 뜻이라 보안이 깨진 것(같은 키+IV 재사용은 GCM에서 치명적)
        @Test
        fun convertToDatabaseColumn_whenCalledTwiceWithSameValue_shouldProduceDifferentCiphertext() {
            val first = converter.convertToDatabaseColumn(50000L)
            val second = converter.convertToDatabaseColumn(50000L)

            assertNotEquals(first, second)
            // 그래도 둘 다 복호화하면 같은 원래 값이 나와야 함
            assertEquals(50000L, converter.convertToEntityAttribute(first))
            assertEquals(50000L, converter.convertToEntityAttribute(second))
        }

        @Test
        fun convertToDatabaseColumn_shouldNotContainPlaintextNumberAsSubstring() {
            // DB를 그냥 열어봤을 때 원래 숫자가 그대로 보이면 암호화 의미가 없음
            val encrypted = converter.convertToDatabaseColumn(1234567L)!!
            assertEquals(false, encrypted.contains("1234567"))
        }
    }

    @Nested
    @DisplayName("복호화 실패")
    inner class DecryptFailure {

        // 다른 키로 암호화된 값을 잘못된 키로 복호화 시도 — GCM 태그 불일치로 실패해야 함
        @Test
        fun convertToEntityAttribute_whenWrongKey_shouldThrowWithoutLeakingOriginalMessage() {
            val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
            val encryptedWithOtherKey = EncryptedLongConverter(otherKey).convertToDatabaseColumn(5_700_000L)!!

            val exception = assertThrows<IllegalStateException> {
                converter.convertToEntityAttribute(encryptedWithOtherKey)
            }
            // 원본 crypto 예외의 메시지/타입을 그대로 노출하지 않고 고정 메시지만 담아야 함(로그/error_logs 유출 방지)
            assertEquals("가계 현황 금액 복호화에 실패했습니다.", exception.message)
            assertNull(exception.cause)
        }

        @Test
        fun convertToEntityAttribute_whenCorruptedBase64_shouldThrowSafeException() {
            assertThrows<IllegalStateException> { converter.convertToEntityAttribute("이건-유효한-base64가-아님") }
        }
    }

    @Nested
    @DisplayName("키 검증")
    inner class KeyValidation {

        @Test
        fun constructor_whenKeyBlank_shouldThrow() {
            assertThrows<IllegalArgumentException> { EncryptedLongConverter("") }
        }

        @Test
        fun constructor_whenKeyWrongLength_shouldThrow() {
            val shortKey = Base64.getEncoder().encodeToString(ByteArray(16)) // AES-128 길이, 32바이트 아님
            assertThrows<IllegalArgumentException> { EncryptedLongConverter(shortKey) }
        }

        @Test
        fun constructor_whenKeyNotValidBase64_shouldThrow() {
            assertThrows<IllegalArgumentException> { EncryptedLongConverter("not-valid-base64!!!") }
        }
    }
}
