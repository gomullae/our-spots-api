package com.ourspots.config

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import java.time.Duration

// 서버 로그를 Grafana Cloud(Loki)로 직접 push — 메트릭(Micrometer의 OTLP registry, management.otlp.*)과는
// 완전히 별개의 파이프라인. 트레이서/미터 프로바이더는 등록하지 않고 로그 전용으로만 SDK를 최소 구성해서
// 메트릭 파이프라인과 안 겹치게 함(둘 다 Actuator/Micrometer에 얹으면 이중 수집될 위험이 있어 분리)
//
// Logback은 Spring 컨텍스트가 뜨기 전에 이미 초기화되므로, install()은 반드시 main()에서
// runApplication()보다 먼저 호출해야 함 — logback-spring.xml에 선언된 OpenTelemetryAppender는
// install() 전까지는 아무것도 안 보내고 대기(그 사이 나가는 극초반 로그만 Grafana로는 못 감, 콘솔에는 정상 출력됨)
object OtelLogging {
    fun install() {
        val endpoint = System.getenv("GRAFANA_OTLP_LOGS_ENDPOINT")
        val auth = System.getenv("GRAFANA_OTLP_AUTH")
        // 로컬/테스트 등 미설정 환경에서는 아예 시도하지 않음(값이 없으면 Grafana 전송을 건너뛰고 콘솔 로그만 유지)
        if (endpoint.isNullOrBlank() || auth.isNullOrBlank()) return

        val exporter = OtlpHttpLogRecordExporter.builder()
            .setEndpoint(endpoint)
            .addHeader("Authorization", auth)
            .setTimeout(Duration.ofSeconds(10))
            .build()

        val resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "our-spots-api")))

        val loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).build())
            .build()

        val sdk = OpenTelemetrySdk.builder()
            .setLoggerProvider(loggerProvider)
            .build()

        OpenTelemetryAppender.install(sdk)
    }
}
