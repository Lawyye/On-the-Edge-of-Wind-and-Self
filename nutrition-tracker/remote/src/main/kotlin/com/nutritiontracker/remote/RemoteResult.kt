package com.nutritiontracker.remote

import com.nutritiontracker.core.model.FoodData
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

enum class RemoteErrorKind(val messageRu: String) {
    NO_NETWORK("Нет подключения к интернету"),
    TIMEOUT("Сервер не ответил вовремя"),
    RATE_LIMITED("Слишком много запросов. Подождите немного или укажите свой ключ USDA в настройках"),
    INVALID_KEY("Ключ USDA API не принят. Проверьте его в настройках"),
    NOT_FOUND("Продукт не найден в источнике"),
    SERVER("Сервис временно недоступен"),
    BAD_RESPONSE("Сервис вернул данные в неожиданном формате"),
}

class RemoteError(val kind: RemoteErrorKind, val source: String, cause: Throwable? = null, val httpCode: Int? = null) :
    Exception("$source: ${kind.messageRu}" + (httpCode?.let { " (HTTP $it)" } ?: ""), cause) {
    /** Сообщение для интерфейса: источник, причина и код ответа, если он есть. */
    val userMessage: String get() = "$source: ${kind.messageRu}" + (httpCode?.let { " (код $it)" } ?: "")
}

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val error: RemoteError) : RemoteResult<Nothing>
}

/**
 * Продукт из внешнего источника. [isFullRecord] = false для результатов
 * поиска USDA: в них нет справочных весов порций, полная карточка
 * запрашивается отдельно при выборе продукта.
 */
data class RemoteFood(val food: FoodData, val isFullRecord: Boolean)

internal suspend fun <T> remoteCall(source: String, block: suspend () -> T): RemoteResult<T> =
    try {
        RemoteResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: RemoteError) {
        RemoteResult.Failure(e)
    } catch (e: HttpException) {
        val body = try { e.response()?.errorBody()?.string() } catch (_: IOException) { null }
        val kind = when {
            e.code() == 429 || body?.contains("OVER_RATE_LIMIT") == true -> RemoteErrorKind.RATE_LIMITED
            body?.contains("API_KEY_INVALID") == true || body?.contains("API_KEY_MISSING") == true -> RemoteErrorKind.INVALID_KEY
            e.code() == 404 -> RemoteErrorKind.NOT_FOUND
            else -> RemoteErrorKind.SERVER
        }
        RemoteResult.Failure(RemoteError(kind, source, e, e.code()))
    } catch (e: SocketTimeoutException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.TIMEOUT, source, e))
    } catch (e: UnknownHostException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.NO_NETWORK, source, e))
    } catch (e: ConnectException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.NO_NETWORK, source, e))
    } catch (e: SSLException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.NO_NETWORK, source, e))
    } catch (e: IOException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.NO_NETWORK, source, e))
    } catch (e: SerializationException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.BAD_RESPONSE, source, e))
    } catch (e: IllegalArgumentException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.BAD_RESPONSE, source, e))
    }
