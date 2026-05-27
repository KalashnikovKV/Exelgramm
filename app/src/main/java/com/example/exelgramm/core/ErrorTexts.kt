package com.example.exelgramm.core

import android.os.NetworkOnMainThreadException
import com.google.gson.JsonSyntaxException
import java.net.UnknownHostException

object ErrorTexts {

    fun from(throwable: Throwable): String {
        val chain = generateSequence(throwable) { it.cause }
        for (t in chain) {
            when (t) {
                is AppError.NoInternet,
                is UnknownHostException ->
                    return "Нет интернета или неверный адрес."

                is AppError.HtmlResponse ->
                    return "Сервер вернул HTML. Проверьте URL (.../exec) и доступ «Все»."

                is AppError.TooManyRedirects ->
                    return "Слишком много редиректов. Проверьте URL веб-приложения."

                is AppError.HttpError ->
                    return "Ошибка HTTP ${t.code}."

                is AppError.ApiError ->
                    return t.detail.take(300)

                is NetworkOnMainThreadException ->
                    return "Сетевая ошибка приложения. Обновите сборку."

                is JsonSyntaxException ->
                    return "Неверный ответ сервера (JSON). Проверьте URL (.../exec)."
            }
        }
        return throwable.message?.take(300) ?: throwable.javaClass.simpleName
    }
}
