package app.soundlife

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

fun fmtSec(s: Long): String {
    val t = if (s < 0) 0L else s
    return (t / 60).toString() + ":" + (t % 60).toString().padStart(2, '0')
}

sealed interface Screen {
    data class AlbumS(val key: String) : Screen
    data class ArtistS(val key: String) : Screen
}

enum class Tab(val label: String, val icon: ImageVector) {
    Catalog("Каталог", Icons.Filled.LibraryMusic),
    Albums("Альбомы", Icons.Filled.Album),
    Artists("Артисты", Icons.Filled.Person),
    Search("Поиск", Icons.Filled.Search),
}

// ---------------------------------------------------------------- каркас

@Composable
fun SoundLifeApp(vm: AppViewModel) {
    val lib = vm.library
    var tab by rememberSaveable { mutableStateOf(Tab.Catalog) }
    val stack = remember { mutableStateListOf<Screen>() }
    var full by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            vm.tick()
            delay(500)
        }
    }
    BackHandler(enabled = full) { full = false }
    BackHandler(enabled = !full && stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val cur = lib?.byKey?.get(vm.currentKey)

    Box(Modifier.fillMaxSize().background(Bg)) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                Column {
                    if (cur != null) MiniPlayer(vm, cur) { full = true }
                    NavigationBar(containerColor = Panel) {
                        Tab.values().forEach { t ->
                            NavigationBarItem(
                                selected = tab == t && stack.isEmpty(),
                                onClick = {
                                    tab = t
                                    stack.clear()
                                },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = Accent,
                                    indicatorColor = Hover,
                                    unselectedIconColor = Mut,
                                    unselectedTextColor = Mut,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (lib == null) {
                    LoadingOrError(vm)
                } else if (stack.isNotEmpty()) {
                    when (val s = stack.last()) {
                        is Screen.AlbumS -> {
                            val a = lib.albumByKey[s.key]
                            if (a != null) {
                                AlbumPage(
                                    vm = vm,
                                    a = a,
                                    artistKey = a.artist.lowercase().takeIf { lib.artistByKey.containsKey(it) },
                                    onBack = { stack.removeAt(stack.lastIndex) },
                                    onArtist = { stack.add(Screen.ArtistS(it)) },
                                )
                            }
                        }
                        is Screen.ArtistS -> {
                            val ar = lib.artistByKey[s.key]
                            if (ar != null) {
                                ArtistPage(
                                    vm = vm,
                                    ar = ar,
                                    onBack = { stack.removeAt(stack.lastIndex) },
                                    onAlbum = { stack.add(Screen.AlbumS(it)) },
                                )
                            }
                        }
                    }
                } else {
                    when (tab) {
                        Tab.Catalog -> CatalogTab(vm, lib)
                        Tab.Albums -> AlbumsTab(lib) { stack.add(Screen.AlbumS(it)) }
                        Tab.Artists -> ArtistsTab(lib) { stack.add(Screen.ArtistS(it)) }
                        Tab.Search -> SearchTab(vm, lib, { stack.add(Screen.AlbumS(it)) }, { stack.add(Screen.ArtistS(it)) })
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = full && cur != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            if (cur != null) FullPlayer(vm, cur) { full = false }
        }
    }
}

@Composable
fun LoadingOrError(vm: AppViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (vm.loading) {
            CircularProgressIndicator(color = Accent)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Не удалось загрузить каталог", color = TextC, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(vm.error ?: "", color = Mut, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.loadLibrary() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                ) { Text("Повторить") }
            }
        }
    }
}

// ---------------------------------------------------------------- общие кусочки

@Composable
fun Cover(url: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).background(Hover), contentAlignment = Alignment.Center) {
        if (url.isBlank()) {
            Icon(Icons.Filled.Album, contentDescription = null, tint = Mut)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun Header(title: String, sub: String, modifier: Modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) {
    Column(modifier) {
        Text(title, color = TextC, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        if (sub.isNotBlank()) Text(sub, color = Mut, fontSize = 14.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = TextC,
        fontSize = 19.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 6.dp),
    )
}

@Composable
fun PlayButtons(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onPlay,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Слушать", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onShuffle,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Hover, contentColor = TextC),
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Вперемешку", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TrackRow(t: Track, playing: Boolean, number: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(number.toString(), color = if (playing) Accent else Mut, fontSize = 14.sp, modifier = Modifier.width(28.dp))
        } else {
            Cover(t.cover, Modifier.size(48.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                color = if (playing) Accent else TextC,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                t.artist + (if (t.album.isNotBlank() && number == null) " · " + t.album else ""),
                color = Mut,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (t.dur > 0) {
            Spacer(Modifier.width(8.dp))
            Text(fmtSec(t.dur.toLong()), color = Mut, fontSize = 13.sp)
        }
    }
}

@Composable
fun AlbumCard(a: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Cover(a.cover, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(10.dp))
        Spacer(Modifier.height(8.dp))
        Text(a.title, color = TextC, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            a.artist + (if (a.year.isNotBlank()) " · " + a.year else ""),
            color = Mut,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArtistCard(ar: Artist, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(ar.cover, Modifier.fillMaxWidth().aspectRatio(1f), CircleShape)
        Spacer(Modifier.height(8.dp))
        Text(ar.name, color = TextC, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(ar.tracks.size.toString() + " " + plural(ar.tracks.size, "трек", "трека", "треков"), color = Mut, fontSize = 13.sp)
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return if (m10 == 1 && m100 != 11) one else if (m10 in 2..4 && (m100 < 10 || m100 >= 20)) few else many
}

// ---------------------------------------------------------------- вкладки

@Composable
fun CatalogTab(vm: AppViewModel, lib: Library) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Header("Каталог", lib.tracks.size.toString() + " " + plural(lib.tracks.size, "трек", "трека", "треков")) }
        item { PlayButtons(onPlay = { vm.play(lib.tracks, 0) }, onShuffle = { vm.playShuffled(lib.tracks) }) }
        itemsIndexed(lib.tracks, key = { _, t -> t.key }) { i, t ->
            TrackRow(t, vm.currentKey == t.key) { vm.play(lib.tracks, i) }
        }
    }
}

@Composable
fun AlbumsTab(lib: Library, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("Альбомы", lib.albums.size.toString() + " " + plural(lib.albums.size, "альбом", "альбома", "альбомов"), Modifier.padding(top = 16.dp, bottom = 4.dp))
        }
        items(lib.albums, key = { it.key }) { a -> AlbumCard(a) { onOpen(a.key) } }
    }
}

@Composable
fun ArtistsTab(lib: Library, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("Артисты", lib.artists.size.toString() + " " + plural(lib.artists.size, "артист", "артиста", "артистов"), Modifier.padding(top = 16.dp, bottom = 4.dp))
        }
        items(lib.artists, key = { it.key }) { ar -> ArtistCard(ar) { onOpen(ar.key) } }
    }
}

@Composable
fun SearchTab(vm: AppViewModel, lib: Library, onAlbum: (String) -> Unit, onArtist: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val query = q.trim().lowercase()
    val ars = remember(query, lib) {
        if (query.isEmpty()) emptyList() else lib.artists.filter { it.name.lowercase().contains(query) }.take(5)
    }
    val als = remember(query, lib) {
        if (query.isEmpty()) emptyList() else lib.albums.filter { (it.title + " " + it.artist).lowercase().contains(query) }.take(8)
    }
    val trs = remember(query, lib) {
        if (query.isEmpty()) emptyList() else lib.tracks.filter { (it.title + " " + it.artist + " " + it.album).lowercase().contains(query) }.take(40)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Header("Поиск", "")
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                placeholder = { Text("Что хочешь послушать?") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Line,
                    cursorColor = Accent,
                    focusedTextColor = TextC,
                    unfocusedTextColor = TextC,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        if (query.isNotEmpty() && ars.isEmpty() && als.isEmpty() && trs.isEmpty()) {
            item { Text("Ничего не найдено", color = Mut, modifier = Modifier.padding(16.dp)) }
        }
        if (ars.isNotEmpty()) {
            item { SectionTitle("Исполнители") }
            itemsIndexed(ars, key = { _, a -> "ar" + a.key }) { _, ar ->
                Row(
                    Modifier.fillMaxWidth().clickable { onArtist(ar.key) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(ar.cover, Modifier.size(52.dp), CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ar.name, color = TextC, fontWeight = FontWeight.SemiBold)
                        Text("Исполнитель", color = Mut, fontSize = 13.sp)
                    }
                }
            }
        }
        if (als.isNotEmpty()) {
            item { SectionTitle("Альбомы") }
            itemsIndexed(als, key = { _, a -> "al" + a.key }) { _, a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onAlbum(a.key) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(a.cover, Modifier.size(52.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(a.title, color = TextC, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Альбом · " + a.artist, color = Mut, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (trs.isNotEmpty()) {
            item { SectionTitle("Треки") }
            itemsIndexed(trs, key = { _, t -> "tr" + t.key }) { i, t ->
                TrackRow(t, vm.currentKey == t.key) { vm.play(trs, i) }
            }
        }
    }
}

// ---------------------------------------------------------------- страницы альбома и артиста

@Composable
fun AlbumPage(vm: AppViewModel, a: Album, artistKey: String?, onBack: () -> Unit, onArtist: (String) -> Unit) {
    val multi = a.tracks.map { it.disc }.distinct().size > 1
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextC)
                    }
                }
                Cover(a.cover, Modifier.size(220.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.height(16.dp))
                Text(a.title, color = TextC, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    a.artist,
                    color = if (artistKey != null) Accent else Mut,
                    fontWeight = FontWeight.SemiBold,
                    modifier = if (artistKey != null) Modifier.clickable { onArtist(artistKey) } else Modifier,
                )
                Text(
                    (if (a.year.isNotBlank()) a.year + " · " else "") + a.tracks.size + " " + plural(a.tracks.size, "трек", "трека", "треков"),
                    color = Mut,
                    fontSize = 13.sp,
                )
            }
        }
        item { PlayButtons(onPlay = { vm.play(a.tracks, 0) }, onShuffle = { vm.playShuffled(a.tracks) }) }
        itemsIndexed(a.tracks, key = { _, t -> t.key }) { i, t ->
            if (multi && (i == 0 || a.tracks[i - 1].disc != t.disc)) {
                Text(
                    "ДИСК " + t.disc,
                    color = Mut,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 2.dp),
                )
            }
            TrackRow(t, vm.currentKey == t.key, number = if (t.no > 0) t.no else i + 1) { vm.play(a.tracks, i) }
        }
    }
}

@Composable
fun ArtistPage(vm: AppViewModel, ar: Artist, onBack: () -> Unit, onAlbum: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextC)
                    }
                }
                Cover(ar.cover, Modifier.size(170.dp), CircleShape)
                Spacer(Modifier.height(16.dp))
                Text(ar.name, color = TextC, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(
                    ar.tracks.size.toString() + " " + plural(ar.tracks.size, "трек", "трека", "треков") +
                        (if (ar.albums.isNotEmpty()) " · " + ar.albums.size + " " + plural(ar.albums.size, "альбом", "альбома", "альбомов") else ""),
                    color = Mut,
                    fontSize = 13.sp,
                )
            }
        }
        item { PlayButtons(onPlay = { vm.play(ar.tracks, 0) }, onShuffle = { vm.playShuffled(ar.tracks) }) }
        if (ar.albums.isNotEmpty()) {
            item { SectionTitle("Альбомы") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ar.albums.size) { i ->
                        val a = ar.albums[i]
                        AlbumCard(a, Modifier.width(150.dp)) { onAlbum(a.key) }
                    }
                }
            }
        }
        item { SectionTitle("Треки") }
        itemsIndexed(ar.tracks, key = { _, t -> t.key }) { i, t ->
            TrackRow(t, vm.currentKey == t.key) { vm.play(ar.tracks, i) }
        }
    }
}

// ---------------------------------------------------------------- плеер

@Composable
fun MiniPlayer(vm: AppViewModel, t: Track, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Hover).clickable(onClick = onOpen)) {
        val p = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Accent,
            trackColor = Line,
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t.cover, Modifier.size(44.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = TextC, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.toggle() }) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Играть или пауза", tint = TextC)
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Следующий", tint = TextC)
            }
        }
    }
}

@Composable
fun FullPlayer(vm: AppViewModel, t: Track, onClose: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = vm.duration
    val shown = drag ?: if (dur > 0) (vm.position.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            // чтобы нажатия не проваливались на экран под плеером
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Свернуть", tint = TextC, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Сейчас играет", color = Mut, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.weight(1f))
        Cover(t.cover, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(16.dp))
        Spacer(Modifier.height(28.dp))
        Text(
            t.title,
            color = TextC,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(t.artist, color = Mut, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        Slider(
            value = shown,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { vm.seekTo((it * dur).toLong()) }
                drag = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Line,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtSec((shown * dur).toLong() / 1000), color = Mut, fontSize = 12.sp)
            Text(fmtSec(dur / 1000), color = Mut, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Вперемешку", tint = if (vm.shuffle) Accent else Mut)
            }
            IconButton(onClick = { vm.prev() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущий", tint = TextC, modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Accent).clickable { vm.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Играть или пауза",
                    tint = OnAccent,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Следующий", tint = TextC, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.cycleRepeat() }) {
                Icon(
                    if (vm.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Повтор",
                    tint = if (vm.repeat != Player.REPEAT_MODE_OFF) Accent else Mut,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
