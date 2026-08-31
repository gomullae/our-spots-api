package com.ourspots.domain.household.entity

// household_budget_items의 4개 섹션 — 원래 스프레드시트의 "월비용/자산/지출예정액/고정지출" 4개 표에
// 대응. 행 수가 적고(다 합쳐 수십 건) 공통 필드가 대부분이라 테이블 4개로 쪼개지 않고 이 enum 하나로 구분
enum class HouseholdSectionType { FIXED_COST, ASSET, PLANNED_EXPENSE, SUBSCRIPTION }

// sectionType=ASSET인 행에서만 사용(자산/부채 구분) — 순자산 = ASSET 합계 - LIABILITY 합계
enum class HouseholdAssetKind { ASSET, LIABILITY }

// 대상자 — 부부 개인 지출(JINWOO/CHOYOUNG) + 가족 공용 지출(FAMILY) 3개
enum class HouseholdPayer { JINWOO, CHOYOUNG, FAMILY }

enum class HouseholdHistoryAction { CREATE, UPDATE, DELETE, RESTORE }

// HouseholdHistory 한 테이블이 HouseholdIncome/HouseholdBudgetItem 두 종류를 다 기록하기 위한 구분자
enum class HouseholdHistoryItemType { INCOME, BUDGET_ITEM }
