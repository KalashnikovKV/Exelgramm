package com.example.exelgramm.core

import android.os.NetworkOnMainThreadException
import com.google.gson.JsonSyntaxException
import java.net.UnknownHostException

object ErrorTexts {

    fun from(throwable: Throwable): String {
        val chain = generateSequence(throwable) { it.cause }.toList()
        for (t in chain) {
            when (t) {
                is NetworkOnMainThreadException ->
                    return "Сетевая ошибка приложения. Обновите сборку."
                is UnknownHostException ->
                    return "Нет интернета или неверный адрес."
                is JsonSyntaxException ->
                    return "Неверный ответ сервера. Проверьте URL веб-приложения (.../exec)."
            }
        }
        return throwable.message?.take(300) ?: throwable.javaClass.simpleName
    }
}
