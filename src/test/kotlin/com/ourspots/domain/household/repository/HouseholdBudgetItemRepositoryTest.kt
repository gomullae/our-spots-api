package com.ourspots.domain.household.repository

import com.ourspots.domain.household.entity.HouseholdAccount
import com.ourspots.domain.household.entity.HouseholdAutoDebitSource
import com.ourspots.domain.household.entity.HouseholdBudgetItem
import com.ourspots.domain.household.entity.HouseholdPayer
import com.ourspots.domain.household.entity.HouseholdSectionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class HouseholdBudgetItemRepositoryTest {

    @Autowired
    private lateinit var repository: HouseholdBudgetItemRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("암호화 컬럼 실제 DB 왕복")
    inner class EncryptedColumnRoundTrip {

        // 컨버터 단독 테스트(EncryptedLongConverterTest)와 별개로, 실제 Hibernate가 이 컬럼을 저장/조회할 때도
        // 정상적으로 암/복호화되는지 확인 — save 후 영속성 컨텍스트를 비우고 다시 읽어와야 진짜 DB 왕복이 검증됨
        @Test
        fun save_thenReload_shouldReturnSameAmount() {
            val saved = entityManager.persist(createItem(amount = 1_700_000L))
            entityManager.flush()
            entityManager.clear()

            val found = repository.findById(saved.id).orElseThrow()

            assertEquals(1_700_000L, found.amount)
        }

        @Test
        fun save_shouldNotStoreAmountAsPlaintextInDb() {
            val saved = entityManager.persist(createItem(amount = 9_999_999L))
            entityManager.flush()

            // 네이티브 쿼리로 실제 DB 컬럼 값을 직접 확인 — 평문 "9999999"가 그대로 보이면 암호화 실패
            val rawAmount = entityManager.entityManager
                .createNativeQuery("SELECT amount FROM household_budget_items WHERE id = :id")
                .setParameter("id", saved.id)
                .singleResult as String

            assertFalse(rawAmount.contains("9999999"))
        }
    }

    @Nested
    @DisplayName("findAllForDashboard")
    inner class FindAllForDashboard {

        @Test
        fun findAllForDashboard_whenIncludeDeletedFalse_shouldExcludeSoftDeleted() {
            val active = entityManager.persist(createItem(label = "활성"))
            val deleted = entityManager.persist(createItem(label = "삭제될것"))
            entityManager.flush()
            entityManager.clear()

            repository.delete(repository.findById(deleted.id).orElseThrow())
            entityManager.flush()
            entityManager.clear()

            val result = repository.findAllForDashboard(includeDeleted = false)

            assertEquals(1, result.size)
            assertEquals(active.id, result[0].id)
        }

        @Test
        fun findAllForDashboard_whenIncludeDeletedTrue_shouldIncludeSoftDeleted() {
            val item = entityManager.persist(createItem())
            entityManager.flush()
            entityManager.clear()

            repository.delete(repository.findById(item.id).orElseThrow())
            entityManager.flush()
            entityManager.clear()

            val result = repository.findAllForDashboard(includeDeleted = true)

            assertEquals(1, result.size)
            assertTrue(result[0].deletedAt != null)
        }
    }

    @Nested
    @DisplayName("findMaxUpdatedAt")
    inner class FindMaxUpdatedAt {

        @Test
        fun findMaxUpdatedAt_whenNoItems_shouldReturnNull() {
            val result = repository.findMaxUpdatedAt()

            assertNull(result)
        }

        @Test
        fun findMaxUpdatedAt_shouldReturnLatestUpdatedAtAcrossItems() {
            val first = entityManager.persist(createItem(label = "통신비"))
            entityManager.persist(createItem(label = "관리비"))
            entityManager.clear()

            val toUpdate = repository.findById(first.id).get()
            toUpdate.amount = 50_000L
            repository.save(toUpdate)
            entityManager.flush()
            entityManager.clear()

            val result = repository.findMaxUpdatedAt()

            assertEquals(toUpdate.updatedAt, result)
        }

        @Test
        fun findMaxUpdatedAt_shouldIgnoreSoftDeletedItems() {
            val item = entityManager.persist(createItem())
            entityManager.clear()

            repository.delete(repository.findById(item.id).orElseThrow())
            entityManager.flush()
            entityManager.clear()

            val result = repository.findMaxUpdatedAt()

            assertNull(result)
        }
    }

    @Nested
    @DisplayName("findByIdIncludingDeleted")
    inner class FindByIdIncludingDeleted {

        @Test
        fun findByIdIncludingDeleted_whenSoftDeleted_shouldStillReturnIt() {
            val item = entityManager.persist(createItem())
            entityManager.flush()
            entityManager.clear()

            repository.delete(repository.findById(item.id).orElseThrow())
            entityManager.flush()
            entityManager.clear()

            val found = repository.findByIdIncludingDeleted(item.id)

            assertEquals(item.id, found?.id)
            assertTrue(found?.deletedAt != null)
        }
    }

    private fun createItem(
        label: String = "통신비",
        amount: Long = 33_250L
    ): HouseholdBudgetItem = HouseholdBudgetItem(
        sectionType = HouseholdSectionType.FIXED_COST,
        assetKind = null,
        label = label,
        vendor = "SKT",
        amount = amount,
        payer = HouseholdPayer.CHOYOUNG,
        autoDebitBank = HouseholdAutoDebitSource.SHINHAN_BANK,
        debitDay = 10,
        account = HouseholdAccount.UTILITY_ACCOUNT
    )
}
