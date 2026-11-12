package app.soundlife

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Track.toItem(): MediaItem = MediaItem.Builder()
    .setMediaId(key)
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album.ifBlank { null })
            .setArtworkUri(if (cover.isNotBlank()) Uri.parse(cover) else null)
            .build()
    )
    .build()

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var library by mutableStateOf<Library?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var currentKey by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    private var controller: MediaController? = null

    init {
        loadLibrary()
        connect()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                library = withContext(Dispatchers.IO) { LibraryLoader.load(getApplication()) }
            } catch (e: Exception) {
                error = e.message ?: "Не удалось загрузить каталог"
            }
            loading = false
        }
    }

    private fun connect() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = try {
                future.get()
            } catch (e: Exception) {
                null
            }
            if (c != null) {
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        sync()
                    }
                })
                sync()
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync() {
        val c = controller ?: return
        currentKey = c.currentMediaItem?.mediaId
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeat = c.repeatMode
        val d = c.duration
        duration = if (d == C.TIME_UNSET || d < 0) 0L else d
        position = c.currentPosition
    }

    /** Вызывается раз в полсекунды, чтобы двигать ползунок. */
    fun tick() {
        val c = controller ?: return
        position = c.currentPosition
        val d = c.duration
        duration = if (d == C.TIME_UNSET || d < 0) 0L else d
    }

    fun play(list: List<Track>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toItem() }, index.coerceIn(0, list.size - 1), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Track>) {
        if (list.isEmpty()) return
        play(list.shuffled(), 0)
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 5000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        position = ms
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
