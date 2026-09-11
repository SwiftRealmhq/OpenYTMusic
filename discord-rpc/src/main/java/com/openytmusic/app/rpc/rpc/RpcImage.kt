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

package com.openytmusic.app.rpc.rpc

import com.openytmusic.app.rpc.repository.VelqiRepository

/**
 * Modified by Zion Huang
 */
sealed class RpcImage {
    abstract suspend fun resolveImage(repository: VelqiRepository): String?

    class DiscordImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: VelqiRepository): String {
            return "mp:${image}"
        }
    }

    class ExternalImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: VelqiRepository): String? {
            return repository.getImage(image)
        }
    }
}
