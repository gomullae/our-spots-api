package com.ourspots

import com.ourspots.config.OtelLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class OurSpotsApiApplication

fun main(args: Array<String>) {
	// Logback은 Spring 컨텍스트보다 먼저 초기화되므로 runApplication보다 앞에서 호출해야 함(OtelLogging 참고)
	OtelLogging.install()
	runApplication<OurSpotsApiApplication>(*args)
}
