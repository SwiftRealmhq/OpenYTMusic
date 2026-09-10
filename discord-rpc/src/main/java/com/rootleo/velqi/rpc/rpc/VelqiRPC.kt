/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.rootleo.velqi.rpc.rpc

import com.rootleo.velqi.rpc.gateway.DiscordWebSocket
import com.rootleo.velqi.rpc.gateway.entities.presence.Activity
import com.rootleo.velqi.rpc.gateway.entities.presence.Assets
import com.rootleo.velqi.rpc.gateway.entities.presence.Metadata
import com.rootleo.velqi.rpc.gateway.entities.presence.Presence
import com.rootleo.velqi.rpc.gateway.entities.presence.Timestamps
import com.rootleo.velqi.rpc.repository.VelqiRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Modified by Zion Huang
 */
open class VelqiRPC(
    token: String,
    cacheFile: java.io.File? = null,
) {
    private val velqiRepository = VelqiRepository(cacheFile)
    private val discordWebSocket = DiscordWebSocket(token)

    fun closeRPC() {
        discordWebSocket.close()
    }

    /** Limpia la actividad: la presencia desaparece (pausa). */
    suspend fun clearPresence() {
        discordWebSocket.sendActivity(
            Presence(
                activities = emptyList(),
                afk = false,
                since = null,
                status = "online"
            )
        )
    }

    fun isRpcRunning(): Boolean {
        return discordWebSocket.isWebSocketConnected()
    }

    suspend fun setActivity(
        name: String,
        state: String?,
        details: String?,
        largeImage: RpcImage?,
        smallImage: RpcImage?,
        largeText: String? = null,
        smallText: String? = null,
        buttons: List<Pair<String, String>>? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        type: Type = Type.LISTENING,
        streamUrl: String? = null,
        applicationId: String? = null,
        status: String? = "online",
        since: Long? = null,
    ) {
        if (!isRpcRunning()) {
            discordWebSocket.connect()
        }
        // Las imagenes son cosmeticas: si el servicio de resolucion falla o tarda,
        // la presencia se envia igual (sin portada) en lugar de bloquearse.
        val largeResolved = largeImage?.let { img ->
            withTimeoutOrNull(5_000) { runCatching { img.resolveImage(velqiRepository) }.getOrNull() }
        }
        val smallResolved = smallImage?.let { img ->
            withTimeoutOrNull(5_000) { runCatching { img.resolveImage(velqiRepository) }.getOrNull() }
        }
        val presence = Presence(
            activities = listOf(
                Activity(
                    name = name,
                    state = state,
                    details = details,
                    type = type.value,
                    timestamps = Timestamps(startTime, endTime),
                    assets = Assets(
                        largeImage = largeResolved,
                        smallImage = smallResolved,
                        largeText = largeText,
                        smallText = smallText
                    ),
                    buttons = buttons?.map { it.first },
                    metadata = Metadata(buttonUrls = buttons?.map { it.second }),
                    applicationId = applicationId.takeIf { !buttons.isNullOrEmpty() },
                    url = streamUrl
                )
            ),
            afk = true,
            since = since,
            status = status ?: "online"
        )
        discordWebSocket.sendActivity(presence)
    }

    enum class Type(val value: Int) {
        PLAYING(0),
        STREAMING(1),
        LISTENING(2),
        WATCHING(3),
        COMPETING(5)
    }

    companion object {
        suspend fun getUserInfo(token: String): Result<UserInfo> = runCatching {
            val client = HttpClient()
            val response = client.get("https://discord.com/api/v9/users/@me") {
                header("Authorization", token)
            }.bodyAsText()
            val json = JSONObject(response)
            val username = json.getString("username")
            val name = json.getString("global_name")
            client.close()

            UserInfo(username, name)
        }
    }
}
