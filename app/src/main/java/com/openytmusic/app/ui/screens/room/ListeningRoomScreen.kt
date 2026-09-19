package com.openytmusic.app.ui.screens.room

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.openytmusic.app.LocalPlayerAwareWindowInsets
import com.openytmusic.app.LocalPlayerConnection
import com.openytmusic.app.R
import com.openytmusic.app.constants.RoomNameKey
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.utils.backToMain
import com.openytmusic.app.ui.utils.highResThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.openytmusic.app.utils.RoomMember
import com.openytmusic.app.utils.RoomSnapshot
import com.openytmusic.app.utils.RoomStatus
import com.openytmusic.app.utils.RoomTrack
import com.openytmusic.app.utils.rememberPreference
import kotlin.random.Random

/**
 * Escuchar música con alguien más.
 *
 * La conexión NO vive aquí (vive en MusicService), así que salir de esta pantalla
 * o bloquear el teléfono no corta la sincronización: esto solo dibuja lo que el
 * servicio ya está haciendo. Por eso, dentro de la sala, la pantalla es una
 * escena completa (portada difuminada de fondo, la canción al frente y el chat
 * abajo) en lugar de una lista de ajustes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningRoomScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val service = playerConnection.service

    val room by service.roomState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val clipboard = LocalClipboardManager.current
    var name by rememberPreference(RoomNameKey, "")
    var codeInput by rememberSaveable { mutableStateOf("") }
    var leaveConfirm by rememberSaveable { mutableStateOf(false) }

    // Mientras la sala está en pantalla no hacen falta avisos: ya los estoy
    // viendo. Al salir, vuelven (el aviso los manda el servicio).
    DisposableEffect(Unit) {
        service.roomScreenVisible = true
        onDispose { service.roomScreenVisible = false }
    }

    // Android 13 y arriba no deja mostrar avisos sin pedir el permiso. Se pide
    // aqui, al entrar a la sala: es justo cuando tienen sentido (los mensajes y
    // quien entra), y no al abrir la app por primera vez.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(room.status) {
        if (room.status == RoomStatus.CONNECTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Nadie debería aparecer como "Alguien": si es la primera vez, ya viene un
    // nombre puesto (editable) en lugar de un hueco vacío.
    val suggestedName = suggestedRoomName()
    LaunchedEffect(suggestedName) {
        if (name.isBlank()) name = suggestedName
    }

    val share = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                context.getString(R.string.listening_room_share_text, room.code)
            )
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
    val copyCode = {
        clipboard.setText(AnnotatedString(room.code))
        Toast.makeText(
            context,
            context.getString(R.string.listening_room_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    if (room.status == RoomStatus.CONNECTED) {
        // El buscador solo vive dentro de la sala: si te vas, sales de la sala.
        BackHandler { leaveConfirm = true }
        if (leaveConfirm) {
            LeaveRoomDialog(
                onStay = { leaveConfirm = false },
                onLeave = {
                    leaveConfirm = false
                    service.leaveRoom()
                    navController.navigateUp()
                }
            )
        }
        RoomStage(
            room = room,
            isPlaying = isPlaying,
            onBack = { leaveConfirm = true },
            onShare = share,
            onCopyCode = copyCode,
            onRename = { newName ->
                name = newName
                service.renameInRoom(newName)
            },
            onTogglePlay = {
                val player = playerConnection.player
                player.playWhenReady = !player.playWhenReady
            },
            onNext = playerConnection::seekToNext,
            onPrevious = playerConnection::seekToPrevious,
            onSendChat = service::sendRoomChat,
            onTyping = service::sendRoomTyping,
            onPlayInRoom = { track -> service.playInRoom(track) },
            onLeave = service::leaveRoom,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
        ) {
            RoomStart(
                name = name,
                onNameChange = { name = it.trim() },
                codeInput = codeInput,
                onCodeChange = { codeInput = it.uppercase() },
                status = room.status,
                error = room.error,
                onCreate = { service.createRoom(name.ifBlank { suggestedName }) {} },
                onJoin = { service.joinRoom(codeInput, name.ifBlank { suggestedName }) },
            )
        }

        TopAppBar(
            title = { Text(stringResource(R.string.listening_room)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

/**
 * Nombre de arranque para la sala: algo con lo que se te pueda llamar sin que
 * tengas que escribir nada. Se puede cambiar antes de entrar y dentro de la sala.
 */
@Composable
private fun suggestedRoomName(): String {
    val number = remember { Random.nextInt(10, 99) }
    return stringResource(R.string.listening_room_default_name, number)
}

/** Pantalla previa: ponerte nombre, crear una sala o entrar con un código. */
@Composable
private fun RoomStart(
    name: String,
    onNameChange: (String) -> Unit,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    status: RoomStatus,
    error: String?,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val busy = status == RoomStatus.CREATING || status == RoomStatus.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.listening_room),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.listening_room_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.listening_room_your_name)) },
            supportingText = { Text(stringResource(R.string.listening_room_name_helper)) },
            leadingIcon = {
                Icon(painterResource(R.drawable.edit), contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onCreate,
            enabled = !busy,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.listening_room_create))
        }

        Spacer(Modifier.height(28.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Text(
                text = stringResource(R.string.listening_room_or_join),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = codeInput,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.listening_room_code)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onJoin,
                enabled = !busy && codeInput.length >= 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(painterResource(R.drawable.arrow_forward), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.listening_room_enter))
            }
        }

        if (busy) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (status == RoomStatus.CREATING) R.string.listening_room_creating
                    else R.string.listening_room_connecting
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        error?.let { message ->
            Spacer(Modifier.height(20.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.listening_room_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * La sala, a pantalla completa: la portada de lo que suena tiñe todo el fondo
 * (difuminada y oscurecida, para que el texto se lea siempre), la canción va al
 * frente con sus controles y el chat se queda con el resto del espacio.
 */
@Composable
private fun RoomStage(
    room: RoomSnapshot,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCopyCode: () -> Unit,
    onRename: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSendChat: (String) -> Unit,
    onTyping: () -> Unit,
    onPlayInRoom: (RoomTrack) -> Unit,
    onLeave: () -> Unit,
) {
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Resultados en vivo: se busca mientras escribes, con un respiro de 300 ms
    // para no disparar una busqueda por letra.
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        val found = withContext(Dispatchers.IO) {
            YouTube.search(text, FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
        }
        results = found.take(20)
        searching = false
    }

    // Predicciones mientras escribes (las mismas que la busqueda principal de la
    // app). Aqui sirven mas que en ningun lado: escribir con una mano para poner
    // una cancion en la sala es incómodo, y una prediccion la pones de un toque.
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = withContext(Dispatchers.IO) {
            YouTube.searchSuggestions(text).getOrNull()?.queries.orEmpty()
        }.filter { it.isNotBlank() && !it.equals(text, ignoreCase = true) }.take(5)
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) runCatching { focus.requestFocus() }
    }
    if (renameOpen) {
        RenameDialog(
            current = room.myName,
            onDismiss = { renameOpen = false },
            onConfirm = { newName ->
                onRename(newName)
                renameOpen = false
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        RoomBackground(room.track?.artwork)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            // --- Arriba: buscador de la sala, el código y compartirlo ---------
            if (searchOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.listening_room_search_hint)) },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (query.isBlank()) searchOpen = false else query = ""
                                },
                                onLongClick = {}
                            ) {
                                Icon(
                                    painterResource(R.drawable.close),
                                    contentDescription = stringResource(R.string.listening_room_search_close)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus)
                    )
                }
            } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, onLongClick = {}) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable(onClick = onCopyCode)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = room.code,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { searchOpen = true },
                    onLongClick = {}
                ) {
                    Icon(
                        painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.listening_room_search),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onShare, onLongClick = {}) {
                    Icon(
                        painterResource(R.drawable.share),
                        contentDescription = stringResource(R.string.listening_room_share),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            }

            Spacer(Modifier.height(10.dp))

            // --- Quiénes están (con tu nombre editable a un toque) ------------
            MembersRow(
                members = room.members,
                onEditMe = { renameOpen = true }
            )

            Spacer(Modifier.height(12.dp))

            // --- Lo que está sonando + controles ------------------------------
            NowPlayingCard(
                track = room.track,
                playing = room.playing,
                preparing = room.preparing,
                alone = room.members.size <= 1,
                isPlaying = isPlaying,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
            )

            Spacer(Modifier.height(14.dp))

            if (searchOpen) {
                // --- Buscador de la sala (solo para esta función) --------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.listening_room_search_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.listening_room_search_for_both),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (suggestions.isNotEmpty()) {
                    RoomSuggestions(
                        suggestions = suggestions,
                        onPick = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                }

                RoomSearchResults(
                    query = query,
                    results = results,
                    onPick = { song ->
                        onPlayInRoom(song.toRoomTrack())
                        query = ""
                        searchOpen = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 120.dp)
                )
            } else {
            // --- Chat ---------------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.listening_room_chat),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                val typing = room.typingName
                if (typing.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.listening_room_chat_private),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                } else {
                    TypingIndicator(typing)
                }
            }

            ChatList(
                chat = room.chat,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 120.dp)
            )

            if (!searchOpen) {
                ChatInput(
                    onSend = onSendChat,
                    onTyping = onTyping,
                    bottomPadding = 6.dp,
                )
            }
            }

            TextButton(
                onClick = onLeave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Icon(
                    painterResource(R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.listening_room_leave),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** Fondo inmersivo: la portada, muy difuminada, bajo un velo que da contraste. */
@Composable
private fun RoomBackground(artwork: String?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (!artwork.isNullOrBlank()) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .alpha(0.45f)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )
    }
}

/** Los que están en la sala: avatar con su inicial, nombre y "Tú" en el tuyo. */
@Composable
private fun MembersRow(members: List<RoomMember>, onEditMe: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        members.forEach { member ->
            MemberChip(member = member, onEditMe = onEditMe)
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.listening_room_members_short, members.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemberChip(member: RoomMember, onEditMe: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50),
        modifier = Modifier.then(
            if (member.isMe) Modifier.clickable(onClick = onEditMe) else Modifier
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colorForName(member.name))
            ) {
                Text(
                    text = initialOf(member.name),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = member.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (member.isMe) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painterResource(R.drawable.edit),
                    contentDescription = stringResource(R.string.listening_room_rename),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Lo que suena ahora mismo, con los controles: cualquiera de los dos puede poner
 * pausa o cambiar de canción, y eso viaja a la sala.
 */
@Composable
private fun NowPlayingCard(
    track: RoomTrack?,
    playing: Boolean,
    preparing: Boolean,
    alone: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = track?.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: stringResource(R.string.listening_room_nothing_yet),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (track != null) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                StatusLabel(playing = playing, preparing = preparing, alone = alone)
            }

            // Mientras los dos cargan la cancion los controles se cierran: un
            // toque ahi arrancaria la musica de un solo lado.
            if (track != null && !preparing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onPrevious, onLongClick = {}) {
                        Icon(
                            painterResource(R.drawable.skip_previous),
                            contentDescription = stringResource(R.string.listening_room_previous),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(42.dp)
                            .clickable(onClick = onTogglePlay)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.listening_room_pause
                                    else R.string.listening_room_play
                                ),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNext, onLongClick = {}) {
                        Icon(
                            painterResource(R.drawable.skip_next),
                            contentDescription = stringResource(R.string.listening_room_next),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/** El estado de la sala, dicho claro: esperando, preparando o sonando juntos. */
@Composable
private fun StatusLabel(playing: Boolean, preparing: Boolean, alone: Boolean) {
    val text = when {
        preparing -> stringResource(R.string.listening_room_preparing)
        alone -> stringResource(R.string.listening_room_waiting_other)
        playing -> stringResource(R.string.listening_room_synced_playing)
        else -> stringResource(R.string.listening_room_synced_paused)
    }
    val color = when {
        preparing -> MaterialTheme.colorScheme.tertiary
        alone -> MaterialTheme.colorScheme.onSurfaceVariant
        playing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (preparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
                color = color
            )
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** El chat: burbujas amplias, sin repetir el nombre en mensajes seguidos. */
@Composable
private fun ChatList(chat: List<com.openytmusic.app.utils.RoomChatMessage>, modifier: Modifier) {
    val listState = rememberLazyListState()

    // El chat se queda abajo solo: es lo que quieres leer.
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) {
            listState.animateScrollToItem(chat.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (chat.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.listening_room_chat_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        itemsIndexed(chat) { index, message ->
            val previous = if (index > 0) chat[index - 1] else null

            if (message.name.isBlank()) {
                // Aviso del sistema (alguien entró o salió).
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            } else {
                val showName = previous?.name != message.name
                Column(
                    horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = if (showName) 8.dp else 0.dp
                        )
                ) {
                    if (showName) {
                        Text(
                            text = if (message.fromMe) {
                                stringResource(R.string.listening_room_you)
                            } else {
                                message.name
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorForName(message.name),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = if (message.fromMe) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (message.fromMe) 18.dp else 5.dp,
                            bottomEnd = if (message.fromMe) 5.dp else 18.dp
                        ),
                        modifier = Modifier.fillMaxWidth(0.86f)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.fromMe) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInput(
    onSend: (String) -> Unit,
    onTyping: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var text by rememberSaveable { mutableStateOf("") }

    fun send() {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = bottomPadding)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                // Aviso "está escribiendo" (se manda con freno para no inundar).
                if (it.isNotBlank()) onTyping()
            },
            placeholder = { Text(stringResource(R.string.listening_room_chat_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            trailingIcon = {
                IconButton(onClick = { send() }, onLongClick = {}) {
                    Icon(
                        painterResource(R.drawable.arrow_forward),
                        contentDescription = stringResource(R.string.listening_room_chat_send),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Cambiar tu nombre dentro de la sala (sin cuenta: solo para esta sesión). */
@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.listening_room_rename_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(24) },
                label = { Text(stringResource(R.string.listening_room_your_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) {
                Text(stringResource(R.string.listening_room_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.listening_room_cancel))
            }
        }
    )
}

/** Aviso de que el otro está escribiendo, con los puntitos animados. */
@Composable
private fun TypingIndicator(name: String) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.listening_room_typing, name),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "typingDot$index"
            )
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(5.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Aviso al salir: el buscador y la sincronización solo viven dentro de la sala. */
@Composable
private fun LeaveRoomDialog(onStay: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.listening_room_leave_title)) },
        text = { Text(stringResource(R.string.listening_room_leave_text)) },
        confirmButton = {
            TextButton(onClick = onLeave) {
                Text(
                    text = stringResource(R.string.listening_room_leave_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onStay) {
                Text(stringResource(R.string.listening_room_leave_stay))
            }
        }
    )
}

/**
 * Predicciones del buscador de la sala (las mismas que la busqueda principal de
 * la app). Tocar una la pone en el campo: escribir con una mano para elegir que
 * suene en la sala es incomodo, y esto lo resuelve de un toque.
 */
@Composable
private fun RoomSuggestions(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Resultados del buscador de la sala. Es una lista corta y compacta a propósito:
 * aquí solo se busca para poner algo y que lo oigan los dos.
 */
@Composable
private fun RoomSearchResults(
    query: String,
    results: List<SongItem>,
    onPick: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (query.trim().length < 2 || results.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier) {
            Text(
                text = stringResource(
                    if (query.trim().length < 2) R.string.listening_room_search_empty
                    else R.string.listening_room_search_none
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(results, key = { it.id }) { song ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(song) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    AsyncImage(
                        model = song.thumbnail.highResThumbnail(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artists.joinToString { it.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** Canción del buscador -> canción de la sala. */
private fun SongItem.toRoomTrack() = RoomTrack(
    id = id,
    title = title,
    artist = artists.joinToString { it.name },
    artwork = thumbnail.highResThumbnail(),
    durationMs = (duration ?: 0).toLong() * 1000L,
)

/** Su inicial, para el avatar del chat y de la lista de la sala. */
private fun initialOf(name: String): String =
    name.trim().take(1).uppercase().ifEmpty { "?" }

/** Color del avatar: siempre el mismo para el mismo nombre, distinto entre los dos. */
private fun colorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF7C4DFF),
        Color(0xFF00BFA5),
        Color(0xFFFF6D00),
        Color(0xFF2979FF),
        Color(0xFFD81B60),
        Color(0xFF00C853),
        Color(0xFF00838F),
        Color(0xFF6D4C41),
    )
    val hash = name.fold(0) { acc, char -> acc * 31 + char.code }
    val index = (if (hash < 0) -hash else hash) % palette.size
    return palette[index]
}
