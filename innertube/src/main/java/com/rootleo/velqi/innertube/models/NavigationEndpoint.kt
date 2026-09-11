package com.rootleo.velqi.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val watchPlaylistEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
    val searchEndpoint: SearchEndpoint? = null,
    val queueAddEndpoint: QueueAddEndpoint? = null,
    val shareEntityEndpoint: ShareEntityEndpoint? = null,
    // Velqi: el landing de biblioteca moderna envuelve la navegacion en
    // commandExecutorCommand (ej. los chips "Playlists", "Songs"...).
    val commandExecutorCommand: CommandExecutorCommand? = null,
) {
    @Serializable
    data class CommandExecutorCommand(
        val commands: List<NavigationEndpoint>?,
    ) {
        val browseEndpoint: BrowseEndpoint?
            get() = commands?.firstNotNullOfOrNull { it.browseEndpoint }
    }

    val endpoint: Endpoint?
        get() = watchEndpoint
            ?: watchPlaylistEndpoint
            ?: browseEndpoint
            ?: searchEndpoint
            ?: queueAddEndpoint
            ?: shareEntityEndpoint
            ?: commandExecutorCommand?.commands?.firstNotNullOfOrNull { it.endpoint }

    val anyWatchEndpoint: WatchEndpoint?
        get() = watchEndpoint
            ?: watchPlaylistEndpoint
}
