package com.ourspots.common.exception

import com.ourspots.common.errorlog.ErrorLog
import com.ourspots.common.errorlog.ErrorLogRepository
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.common.response.ApiResponse
import com.ourspots.common.util.RequestUtils
import com.ourspots.domain.auth.entity.AccessDeniedLog
import com.ourspots.domain.auth.repository.AccessDeniedLogRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler(
    private val errorLogRepository: ErrorLogRepository,
    private val accessDeniedLogRepository: AccessDeniedLogRepository,
    private val telegramNotificationService: TelegramNotificationService
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFoundException(e: NotFoundException): ApiResponse<Nothing> {
        return ApiResponse.error(e.message ?: "Not found")
    }

    @ExceptionHandler(DuplicateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleDuplicateException(e: DuplicateException): ApiResponse<Nothing> {
        return ApiResponse.error(e.message ?: "Duplicate entry")
    }

    @ExceptionHandler(UnauthorizedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUnauthorizedException(e: UnauthorizedException, request: HttpServletRequest): ApiResponse<Nothing> {
        // /api/auth/login의 비밀번호 오류는 login_attempts가 이미 기록하므로 여기서는 중복 기록하지 않음 —
        // 이 테이블은 "토큰 없이/만료된 토큰으로 보호된 리소스에 접근"한 시도만 추적하는 용도
        if (request.requestURI != "/api/auth/login") {
            val clientIp = RequestUtils.getClientIp(request)
            try {
                accessDeniedLogRepository.save(
                    AccessDeniedLog(
                        ipAddress = clientIp,
                        method = request.method,
                        path = request.requestURI,
                        message = e.message,
                        userAgent = request.getHeader("User-Agent")
                    )
                )
            } catch (logError: Exception) {
                logger.error("Failed to persist access denied log", logError)
            }
            telegramNotificationService.notifyAccessDenied(
                method = request.method,
                path = request.requestURI,
                ipAddress = clientIp,
                message = e.message
            )
        }
        return ApiResponse.error(e.message ?: "Unauthorized")
    }

    @ExceptionHandler(TooManyRequestsException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun handleTooManyRequestsException(e: TooManyRequestsException): ApiResponse<Nothing> {
        return ApiResponse.error(e.message ?: "Too many requests")
    }

    @ExceptionHandler(ServiceUnavailableException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleServiceUnavailableException(e: ServiceUnavailableException): ApiResponse<Nothing> {
        return ApiResponse.error(e.message ?: "Service unavailable")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(e: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ApiResponse.error(message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException): ApiResponse<Nothing> {
        return ApiResponse.error("요청 형식이 올바르지 않습니다.")
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingParameter(e: MissingServletRequestParameterException): ApiResponse<Nothing> {
        return ApiResponse.error("필수 파라미터가 누락되었습니다: ${e.parameterName}")
    }

    @ExceptionHandler(NoResourceFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNoResourceFoundException(e: NoResourceFoundException): ApiResponse<Nothing> {
        return ApiResponse.error("요청한 경로를 찾을 수 없습니다.")
    }

    // 쿼리 파라미터가 타입에 안 맞을 때(예: enum 파라미터에 잘못된 문자열, budget에 숫자 아닌 값) — 서버 버그가 아니라 잘못된 요청이므로 400
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMethodArgumentTypeMismatch(e: MethodArgumentTypeMismatchException): ApiResponse<Nothing> {
        return ApiResponse.error("파라미터 형식이 올바르지 않습니다: ${e.name}")
    }

    // @RequestParam에 직접 붙인 제약(@Positive 등)이 실패했을 때 — @Valid @RequestBody와 달리 별도 예외 타입으로 발생함
    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraintViolationException(e: ConstraintViolationException): ApiResponse<Nothing> {
        val message = e.constraintViolations.joinToString(", ") { "${it.propertyPath}: ${it.message}" }
        return ApiResponse.error(message)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception, request: HttpServletRequest): ApiResponse<Nothing> {
        logger.error("Unexpected error occurred", e)
        // 로그 저장 자체가 실패해도(예: DB 커넥션 문제가 원래 예외 원인인 경우) 사용자에게 가는 500 응답은 막지 않음
        try {
            errorLogRepository.save(
                ErrorLog(
                    exceptionType = e.javaClass.simpleName,
                    message = e.message?.take(1000),
                    method = request.method,
                    path = request.requestURI,
                    stackTrace = e.stackTraceToString().take(4000)
                )
            )
        } catch (logError: Exception) {
            logger.error("Failed to persist error log", logError)
        }
        telegramNotificationService.notifyServerError(
            exceptionType = e.javaClass.simpleName,
            method = request.method,
            path = request.requestURI,
            message = e.message
        )
        return ApiResponse.error("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
    }
}
