package com.openytmusic.app.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openytmusic.app.MainActivity
import com.openytmusic.app.R
import com.openytmusic.app.db.InternalDatabase
import com.openytmusic.app.db.MusicDatabase
import com.openytmusic.app.extensions.div
import com.openytmusic.app.extensions.tryOrNull
import com.openytmusic.app.extensions.zipInputStream
import com.openytmusic.app.extensions.zipOutputStream
import com.openytmusic.app.playback.MusicService
import com.openytmusic.app.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.openytmusic.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    /**
     * La copia corre en Dispatchers.IO: antes se hacia en el hilo que llamaba (la UI),
     * con el fichero de la base de datos y el DataStore enteros de por medio.
     */
    fun backup(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered().use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }
                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }.onSuccess {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            reportException(it)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restore(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use {
                it.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                    while (entry != null) {
                        when (entry.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            InternalDatabase.DB_NAME -> {
                                // Capturar la ruta ANTES de close(): leer
                                // openHelper.writableDatabase despues reabria la conexion
                                // y se sobrescribia el fichero con la base viva detras.
                                val path = database.openHelper.writableDatabase.path
                                runBlocking(Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()
                                FileOutputStream(path).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                    }
                }
            }
            context.stopService(Intent(context, MusicService::class.java))
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            context.startActivity(Intent(context, MainActivity::class.java))
            exitProcess(0)
        }.onFailure {
            reportException(it)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
