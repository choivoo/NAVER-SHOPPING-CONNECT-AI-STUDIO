package com.shoppingconnect.aistudio.core.common

/**
 * Every failure the user can see is mapped to one of these kinds, each with a
 * human-readable Korean message and the recovery actions that make sense for it.
 */
enum class ErrorKind(val userMessage: String) {
    NetworkError("인터넷 연결이 필요합니다. 네트워크 상태를 확인해 주세요."),
    AuthExpired("로그인이 만료되었습니다. 다시 연결해 주세요."),
    InvalidUrl("지원하지 않는 주소입니다. http/https 상품 링크를 입력해 주세요."),
    ProductExtractionFailed("상품 정보를 불러오지 못했습니다."),
    AiTimeout("AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."),
    AiParseFailure("AI 응답을 해석하지 못했습니다. 다시 시도해 주세요."),
    AiUnauthorized("Claude API 키가 올바르지 않습니다. 설정에서 키를 확인해 주세요."),
    AiRateLimited("요청이 많아 잠시 제한되었습니다. 잠시 후 다시 시도해 주세요."),
    AiUnavailable("선택한 AI 모델을 사용할 수 없습니다. 설정에서 모델을 변경해 주세요."),
    AiNotConfigured("AI가 연결되지 않았습니다. 설정 → AI에서 API 키 또는 프록시를 설정하거나 데모 모드를 사용하세요."),
    AiRefused("AI가 이 요청을 처리하지 않았습니다. 입력 내용을 확인해 주세요."),
    ContentTooLong("입력 내용이 너무 깁니다. 내용을 줄여 다시 시도해 주세요."),
    RenderFailure("영상 렌더링에 실패했습니다."),
    StorageFailure("저장 공간에 접근하지 못했습니다. 저장 공간을 확인해 주세요."),
    UnsupportedFormat("지원하지 않는 파일 형식입니다."),
    NotConfigured("필요한 설정이 없습니다."),
    Cancelled("작업이 취소되었습니다."),
    Unknown("알 수 없는 오류가 발생했습니다."),
}

class AppException(
    val kind: ErrorKind,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: kind.userMessage, cause) {
    val userMessage: String get() = if (detail.isNullOrBlank()) kind.userMessage else "${kind.userMessage}\n$detail"
    val retryable: Boolean
        get() = kind in setOf(
            ErrorKind.NetworkError, ErrorKind.AiTimeout, ErrorKind.AiRateLimited,
            ErrorKind.AiParseFailure, ErrorKind.ProductExtractionFailed, ErrorKind.RenderFailure,
        )
}

fun Throwable.toAppException(default: ErrorKind = ErrorKind.Unknown): AppException = when (this) {
    is AppException -> this
    is kotlinx.coroutines.CancellationException -> AppException(ErrorKind.Cancelled, cause = this)
    is java.net.SocketTimeoutException -> AppException(ErrorKind.AiTimeout, cause = this)
    is java.net.UnknownHostException, is java.net.ConnectException, is javax.net.ssl.SSLException ->
        AppException(ErrorKind.NetworkError, cause = this)
    is java.io.IOException -> AppException(ErrorKind.NetworkError, message, this)
    else -> AppException(default, message, this)
}

sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(val error: AppException) : AppResult<Nothing>
}

inline fun <T> appRunCatching(default: ErrorKind = ErrorKind.Unknown, block: () -> T): AppResult<T> = try {
    AppResult.Ok(block())
} catch (c: kotlinx.coroutines.CancellationException) {
    throw c
} catch (t: Throwable) {
    AppResult.Err(t.toAppException(default))
}
