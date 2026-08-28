package com.ourspots.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

// Cloudflare R2는 S3 호환 API라 AWS SDK를 그대로 쓰되 엔드포인트만 R2로 바꿔서 사용 — 리전 개념이 없어서 "auto" 고정
@Configuration
class R2Config(
    @Value("\${app.r2.account-id}") private val accountId: String,
    @Value("\${app.r2.access-key-id}") private val accessKeyId: String,
    @Value("\${app.r2.secret-access-key}") private val secretAccessKey: String
) {
    private fun endpoint(): URI = URI.create("https://$accountId.r2.cloudflarestorage.com")

    private fun credentials() = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))

    // R2는 AWS S3와 달리 virtual-hosted-style(버킷명.엔드포인트) 주소를 지원하지 않고 path-style(엔드포인트/버킷명)만 지원함 —
    // 이걸 안 켜면 SDK가 기본값(virtual-hosted-style)으로 내부 엔드포인트를 계산하려다 region "auto"와 충돌해 URI 파싱 자체가 깨짐.
    // chunkedEncodingEnabled(false)는 R2 공식 권장 설정 — 켜져 있으면 서명 불일치 에러가 날 수 있음
    private fun pathStyleConfig(): S3Configuration = S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .chunkedEncodingEnabled(false)
        .build()

    // 업로드용 presigned URL 발급 전용 — 실제 파일 바이트는 브라우저가 이 URL로 R2에 직접 PUT(서버를 거치지 않음)
    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.builder()
        .endpointOverride(endpoint())
        .region(Region.of("auto"))
        .credentialsProvider(credentials())
        .serviceConfiguration(pathStyleConfig())
        .build()

    // 삭제 등 서버가 직접 R2를 호출해야 하는 소량의 관리 작업용
    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(endpoint())
        .region(Region.of("auto"))
        .credentialsProvider(credentials())
        .serviceConfiguration(pathStyleConfig())
        .build()
}
