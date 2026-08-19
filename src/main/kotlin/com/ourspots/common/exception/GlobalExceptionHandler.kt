package com.ourspots.common.exception

import com.ourspots.common.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

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
    fun handleUnauthorizedException(e: UnauthorizedException): ApiResponse<Nothing> {
        return ApiResponse.error(e.message ?: "Unauthorized")
    }

    @ExceptionHandler(TooManyRequestsException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun handleTooManyRequestsException(e: TooManyRequestsException): ApiResponse<Nothing> {
        return ApiResponse.error(e.message ?: "Too many requests")
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

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception): ApiResponse<Nothing> {
        logger.error("Unexpected error occurred", e)
        return ApiResponse.error("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
    }
}
