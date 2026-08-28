package com.ourspots.common.util

import com.ourspots.common.exception.NotFoundException
import org.springframework.data.repository.CrudRepository

// 여러 서비스(Weight/Expense/Schedule/Place/Photo)에 각자 반복되던
// "findById 실패 시 NotFoundException으로 변환" 패턴을 하나로 통일
// ID : Any — CrudRepository.findById(ID id)가 Java 쪽에서 @NonNull이라 Kotlin이 "ID & Any"를 요구함
fun <T, ID : Any> CrudRepository<T, ID>.findByIdOrThrow(id: ID, entityName: String): T =
    findById(id).orElseThrow { NotFoundException("$entityName not found: $id") }
