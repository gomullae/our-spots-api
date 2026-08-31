package com.ourspots.domain.household.service

import com.ourspots.api.dto.HouseholdBudgetItemRequest
import com.ourspots.api.dto.HouseholdIncomeRequest
import com.ourspots.common.notification.HouseholdItemSummary
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.common.exception.NotFoundException
import com.ourspots.domain.household.entity.HouseholdBudgetItem
import com.ourspots.domain.household.entity.HouseholdHistory
import com.ourspots.domain.household.entity.HouseholdHistoryAction
import com.ourspots.domain.household.entity.HouseholdIncome
import com.ourspots.domain.household.entity.HouseholdPayer
import com.ourspots.domain.household.entity.HouseholdSectionType
import com.ourspots.domain.household.repository.HouseholdBudgetItemRepository
import com.ourspots.domain.household.repository.HouseholdHistoryRepository
import com.ourspots.domain.household.repository.HouseholdIncomeRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import kotlin.test.assertEquals

class HouseholdBudgetServiceTest {

    @MockK
    private lateinit var incomeRepository: HouseholdIncomeRepository

    @MockK
    private lateinit var itemRepository: HouseholdBudgetItemRepository

    @MockK(relaxed = true)
    private lateinit var historyRepository: HouseholdHistoryRepository

    @MockK(relaxed = true)
    private lateinit var telegramNotificationService: TelegramNotificationService

    @InjectMockKs
    private lateinit var service: HouseholdBudgetService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    @DisplayName("getMeta")
    inner class GetMeta {

        @Test
        fun getMeta_shouldReturnCombinedCountAndLatestLastModified() {
            val incomeLastModified = java.time.LocalDateTime.of(2026, 8, 20, 9, 0)
            val itemLastModified = java.time.LocalDateTime.of(2026, 8, 25, 9, 0)
            every { incomeRepository.count() } returns 2L
            every { incomeRepository.findMaxUpdatedAt() } returns incomeLastModified
            every { itemRepository.count() } returns 20L
            every { itemRepository.findMaxUpdatedAt() } returns itemLastModified

            val result = service.getMeta()

            assertEquals(22L, result.count)
            assertEquals(itemLastModified, result.lastModified)
        }

        @Test
        fun getMeta_whenOnlyIncomeHasLastModified_shouldReturnIncomeValue() {
            val incomeLastModified = java.time.LocalDateTime.of(2026, 8, 20, 9, 0)
            every { incomeRepository.count() } returns 2L
            every { incomeRepository.findMaxUpdatedAt() } returns incomeLastModified
            every { itemRepository.count() } returns 0L
            every { itemRepository.findMaxUpdatedAt() } returns null

            val result = service.getMeta()

            assertEquals(2L, result.count)
            assertEquals(incomeLastModified, result.lastModified)
        }

        @Test
        fun getMeta_whenNoRecords_shouldReturnZeroCountAndNullLastModified() {
            every { incomeRepository.count() } returns 0L
            every { incomeRepository.findMaxUpdatedAt() } returns null
            every { itemRepository.count() } returns 0L
            every { itemRepository.findMaxUpdatedAt() } returns null

            val result = service.getMeta()

            assertEquals(0L, result.count)
            assertEquals(null, result.lastModified)
        }
    }

    @Nested
    @DisplayName("createItem")
    inner class CreateItem {

        @Test
        fun createItem_shouldSaveRecordHistoryAndNotify() {
            val request = HouseholdBudgetItemRequest(
                sectionType = HouseholdSectionType.FIXED_COST,
                label = "통신비",
                vendor = "SKT",
                amount = 33_250L,
                payer = HouseholdPayer.CHOYOUNG
            )
            val saved = createItemEntity(id = 1L, label = "통신비", amount = 33_250L)
            every { itemRepository.save(any()) } returns saved

            val result = service.createItem(request)

            assertEquals(33_250L, result.amount)
            verify { historyRepository.save(match<HouseholdHistory> { it.action == HouseholdHistoryAction.CREATE && it.amount == 33_250L }) }
            verify {
                telegramNotificationService.notifyHouseholdItemCreated(
                    HouseholdItemSummary(
                        sectionLabel = "고정비",
                        label = "통신비",
                        vendor = "SKT",
                        amount = 33_250L,
                        payerLabel = "초영",
                        debitDay = null,
                        memo = null
                    )
                )
            }
        }

        // 구독료는 "나중에 추가할게"라는 요청으로 일단 알림 대상에서 제외됨(2026-09-01)
        @Test
        fun createItem_whenSubscription_shouldNotNotify() {
            val request = HouseholdBudgetItemRequest(
                sectionType = HouseholdSectionType.SUBSCRIPTION,
                label = "유튜브 구독",
                amount = 0L
            )
            val saved = createItemEntity(id = 1L, label = "유튜브 구독", amount = 0L).apply {
                sectionType = HouseholdSectionType.SUBSCRIPTION
            }
            every { itemRepository.save(any()) } returns saved

            service.createItem(request)

            verify(exactly = 0) { telegramNotificationService.notifyHouseholdItemCreated(any()) }
        }
    }

    @Nested
    @DisplayName("updateItem")
    inner class UpdateItem {

        @Test
        fun updateItem_whenAmountChanges_shouldNotifyWithBeforeAndAfter() {
            val existing = createItemEntity(id = 1L, label = "통신비", amount = 33_250L)
            every { itemRepository.findById(1L) } returns Optional.of(existing)
            every { itemRepository.save(any()) } answers { firstArg() }

            val request = HouseholdBudgetItemRequest(
                sectionType = HouseholdSectionType.FIXED_COST,
                label = "통신비",
                vendor = "SKT",
                amount = 35_000L,
                payer = HouseholdPayer.CHOYOUNG
            )

            val result = service.updateItem(1L, request)

            assertEquals(35_000L, result.amount)
            verify {
                telegramNotificationService.notifyHouseholdItemUpdated(
                    HouseholdItemSummary(
                        sectionLabel = "고정비",
                        label = "통신비",
                        vendor = "SKT",
                        amount = 33_250L,
                        payerLabel = "초영",
                        debitDay = null,
                        memo = null
                    ),
                    HouseholdItemSummary(
                        sectionLabel = "고정비",
                        label = "통신비",
                        vendor = "SKT",
                        amount = 35_000L,
                        payerLabel = "초영",
                        debitDay = null,
                        memo = null
                    )
                )
            }
            verify { historyRepository.save(match<HouseholdHistory> { it.action == HouseholdHistoryAction.UPDATE }) }
        }

        @Test
        fun updateItem_whenIdNotExists_shouldThrowNotFoundException() {
            every { itemRepository.findById(999L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                service.updateItem(999L, HouseholdBudgetItemRequest(sectionType = HouseholdSectionType.FIXED_COST, label = "x", amount = 1L))
            }
        }
    }

    @Nested
    @DisplayName("deleteItem")
    inner class DeleteItem {

        @Test
        fun deleteItem_shouldSoftDeleteRecordHistoryAndNotify() {
            val existing = createItemEntity(id = 1L, label = "통신비", amount = 33_250L)
            every { itemRepository.findById(1L) } returns Optional.of(existing)
            every { itemRepository.delete(existing) } just runs

            service.deleteItem(1L)

            verify { itemRepository.delete(existing) }
            verify { historyRepository.save(match<HouseholdHistory> { it.action == HouseholdHistoryAction.DELETE }) }
            verify {
                telegramNotificationService.notifyHouseholdItemDeleted(
                    HouseholdItemSummary(
                        sectionLabel = "고정비",
                        label = "통신비",
                        vendor = "SKT",
                        amount = 33_250L,
                        payerLabel = "초영",
                        debitDay = null,
                        memo = null
                    )
                )
            }
        }
    }

    @Nested
    @DisplayName("createIncome / updateIncome")
    inner class IncomeCrud {

        @Test
        fun createIncome_shouldRecordHistoryAndNotify() {
            val request = HouseholdIncomeRequest(label = "급여", amount = 5_700_000L)
            every { incomeRepository.save(any()) } returns HouseholdIncome(id = 1L, label = "급여", amount = 5_700_000L)

            val result = service.createIncome(request)

            assertEquals(5_700_000L, result.amount)
            verify { historyRepository.save(match<HouseholdHistory> { it.action == HouseholdHistoryAction.CREATE && it.amount == 5_700_000L }) }
            verify {
                telegramNotificationService.notifyHouseholdItemCreated(
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
            }
        }

        @Test
        fun updateIncome_whenNoActualChange_shouldStillCallNotifyButServiceLetsTelegramSuppressIt() {
            // 실제로 아무것도 안 바뀌면 TelegramNotificationService.notifyHouseholdItemUpdated 내부에서
            // 알림 자체를 생략함(별도 유닛 테스트로 검증) — 여기서는 서비스가 항상 호출은 한다는 것만 확인
            val existing = HouseholdIncome(id = 1L, label = "급여", amount = 5_700_000L)
            every { incomeRepository.findById(1L) } returns Optional.of(existing)
            every { incomeRepository.save(any()) } answers { firstArg() }

            service.updateIncome(1L, HouseholdIncomeRequest(label = "급여", amount = 5_700_000L))

            verify { telegramNotificationService.notifyHouseholdItemUpdated(any(), any()) }
        }
    }

    private fun createItemEntity(id: Long, label: String, amount: Long): HouseholdBudgetItem = HouseholdBudgetItem(
        id = id,
        sectionType = HouseholdSectionType.FIXED_COST,
        label = label,
        vendor = "SKT",
        amount = amount,
        payer = HouseholdPayer.CHOYOUNG
    )
}
