package com.example.photogallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberImagePainter
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min


class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
//            CoilImageExample(viewModel.state.collectAsStateWithLifecycle().value) {
//                viewModel.loadDate()
//            }
//            PhotoPickerScreen()
            MediaApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaApp() {
    // Define the permissions based on the Android version
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)
    val scaffoldState = rememberScaffoldState()

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = { TopAppBar(backgroundColor = Color.Cyan, title = { Text("Photo Gallery") }) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Check if all permissions are granted
            if (permissionsState.allPermissionsGranted) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    MediaListScreen(maxWidth)
                }
            } else {
                PermissionRequestScreen {
                    permissionsState.launchMultiplePermissionRequest()
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("This app requires access to your media files to display images and videos.")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRequest) {
            Text("Grant Permissions")
        }
    }
}


@Composable
fun SelectableGridItem(
    uri: Uri,
    onItemPositioned: (uri: Uri, bounds: Rect) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                // Use boundsInRoot() so that coordinates match the overlay's coordinate system.
                val bounds = layoutCoordinates.boundsInRoot()
                onItemPositioned(uri, bounds)
            }
    ) {
        content()
    }
}

@Composable
fun DraggableSelectionOverlay(
    modifier: Modifier = Modifier,
    onSelectionChange: (Rect) -> Unit,
    onDragEnd: () -> Unit,
    gridState: LazyGridState,
    itemBounds: Map<Uri, Rect>, // New parameter
    fixedItemBounds: MutableMap<Uri, Rect>, // NEW parameter: snapshot of bounds

    content: @Composable () -> Unit
) {
    var fixedGlobalDragStart by remember { mutableStateOf<Offset?>(null) }
    val dragActive = remember { mutableStateOf(false) }
    var fixedStartIndex by remember { mutableStateOf<Int?>(null) }

    // For auto-scroll checks
    val coroutineScope = rememberCoroutineScope()

    // We'll track the overlay's position in root coords
    var overlayPosInRoot by remember { mutableStateOf(Offset.Zero) }
    var overlaySizePx by remember { mutableStateOf(IntSize.Zero) }

    // Once we actually "start" a drag, we lock the overlay's position
    // so it doesn't shift if the user scrolls the list.
    var lockedOverlayPos by remember { mutableStateOf<Offset?>(null) }

    // The local pointer offset where the drag started, and current pointer
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    // A small threshold so we don't interpret tiny accidental moves as a full drag
    val TOUCH_SLOP = 20f
    val HOLD_DURATION_MS = 200L // Adjust timing as needed (200 milliseconds)

    /**
     * Compute a rectangle in "root" coordinates, using the locked overlay offset
     * (once a drag is recognized) or the current overlay offset (before that).
     */
    fun calculateRect(start: Offset?, current: Offset?): Rect? {
        if (start == null || current == null) return null
        // Use fixedGlobalDragStart (if available) as the start point.
        val startGlobal = fixedGlobalDragStart ?: (start + (lockedOverlayPos ?: overlayPosInRoot))
        val currentGlobal = current + (lockedOverlayPos ?: overlayPosInRoot)
        return Rect(
            topLeft = Offset(
                x = min(startGlobal.x, currentGlobal.x),
                y = min(startGlobal.y, currentGlobal.y)
            ),
            bottomRight = Offset(
                x = max(startGlobal.x, currentGlobal.x),
                y = max(startGlobal.y, currentGlobal.y)
            )
        )
    }


    // Our pointer logic:
    // 1) Wait for the user to put a finger down
    // 2) If they move more than TOUCH_SLOP, we consider it a "drag"
    // 3) If they never move that far and then lift, it was a simple tap/long-press for the child
    // Pointer logic with origin check
    val pointerModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                (fixedItemBounds as MutableMap).clear()  // NEW: Clear the snapshot for a new drag session
                val down = awaitFirstDown(requireUnconsumed = false)
                val startTime = System.currentTimeMillis() // Track initial touch time
                var pointerId = down.id
                var totalDistance = 0f
                var isDragging = false
                dragStart = null
                dragCurrent = null
                lockedOverlayPos = null

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { !it.pressed }) break

                    val change = event.changes.find { it.id == pointerId } ?: event.changes.first()
                    pointerId = change.id
                    val move = change.positionChange()

                    if (move != Offset.Zero) {
                        totalDistance += move.getDistance()

                        if (!isDragging) {
                            val elapsedTime = System.currentTimeMillis() - startTime

                            // Cancel if moved too quickly (before hold duration)
                            if (elapsedTime < HOLD_DURATION_MS && totalDistance > TOUCH_SLOP) {
                                break
                            }

                            // Only allow drag after hold duration
                            if (elapsedTime >= HOLD_DURATION_MS) {
                                val startRootPos = down.position + overlayPosInRoot
                                val isValidDrag = itemBounds.values.any { it.contains(startRootPos) }
                                if (isValidDrag) {
                                    lockedOverlayPos = overlayPosInRoot
                                    fixedGlobalDragStart = down.position + lockedOverlayPos!!
                                    dragStart = down.position
                                    dragCurrent = down.position
                                    isDragging = true
                                    dragActive.value = true  // NEW: mark drag as active
                                    // Capture a snapshot of the current item bounds
                                    fixedItemBounds.clear()
                                    fixedItemBounds.putAll(itemBounds)
                                } else {
                                    break
                                }
                            }
                        }

                        if (isDragging) {
                            change.consume()
                            dragCurrent = dragCurrent?.plus(move)
                            calculateRect(dragStart, dragCurrent)?.let(onSelectionChange)

                            // Auto-scroll logic
                            val currY = dragCurrent!!.y
                            val containerHeight = overlaySizePx.height.toFloat()
                            when {
                                currY > containerHeight - 200f ->
                                    coroutineScope.launch { gridState.scrollBy(25f) }

                                currY < 200f ->
                                    coroutineScope.launch { gridState.scrollBy(-25f) }
                            }
                        }
                    }
                }
                if (isDragging) onDragEnd()
                (fixedItemBounds as MutableMap).clear()  // NEW: Clear snapshot after drag end
                dragActive.value = false
            }
        }
    }

    // Compose UI: A Box that positions the overlay and applies our pointer logic
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (!dragActive.value) {  // NEW: update only when not dragging
                    overlayPosInRoot = coords.positionInRoot()
                }
                overlaySizePx = coords.size
            }
            // We *don't* pass `PointerEventPass.Initial` or `.Final`:
            // we let the child see events first. If the child doesn't fully consume them
            // (i.e. user starts moving), we step in.
            // If you find that the child *always* consumes them, you can try `PointerEventPass.Final`.
            .then(pointerModifier)
    ) {
        // The grid or child content goes here
        content()

        // If a drag is in progress, draw the bounding rectangle
        val rect = calculateRect(dragStart, dragCurrent)
        if (rect != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                // For drawing, shift the rect by negative the *unlocked* overlayPosInRoot
                val base = lockedOverlayPos ?: overlayPosInRoot
                val rectTopLeft = rect.topLeft - base
                drawRect(
                    color = Color.Blue.copy(alpha = 0.2f),
                    topLeft = rectTopLeft,
                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListScreen(width: Dp) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    // Collect the state from the ViewModel
    val state by viewModel.state.collectAsState()
    // Collect the selection state
    val selectedMedia by viewModel.selectedMedia.collectAsState()
    // Create a state to hold the media list; initially empty.
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    val itemBounds = remember { mutableStateMapOf<Uri, Rect>() }
    val fixedItemBounds = remember { mutableStateMapOf<Uri, Rect>() }
    val gridState = rememberLazyGridState()

    // For column count state and preview handling.
    var columns by remember { mutableIntStateOf(3) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    // --- NEW OR CHANGED: We'll build a flattened list of media items
    // ignoring date headers, so we can find an index for each media item (URI).
    var uriToIndex by remember { mutableStateOf<Map<Uri, Int>>(emptyMap()) }
    var indexToUri by remember { mutableStateOf<Map<Int, Uri>>(emptyMap()) }

    // Load the media items as soon as the composable enters composition.
    LaunchedEffect(Unit) {
        viewModel.loadMediaItems(context)
    }

    Column {
        if (previewUri != null) {
            FullScreenPreview(uri = previewUri!!) {
                // Callback to dismiss the preview
                previewUri = null
            }
        }

        // Header Row inside MediaListScreen (at the top of the Column)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top Left: Display selection count with a clear icon if more than one item is selected.
            if (selectedMedia.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // X icon to clear selection.
                    IconButton(
                        onClick = { viewModel.clearSelection() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear selection",
                            tint = Color.Black // Adjust color as needed.
                        )
                    }
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = "${selectedMedia.size} selected",
                        style = MaterialTheme.typography.subtitle1

                    )
                }
            } else {
                // Reserve space when no selection count is displayed.
                Box(modifier = Modifier)
            }

            // Top Right: Change Column Numbers button remains unchanged.
            Button(
                colors = ButtonColors(
                    containerColor = Color.Cyan,
                    contentColor = Color.Cyan,
                    disabledContainerColor = Color.Cyan,
                    disabledContentColor = Color.Cyan
                ),
                onClick = {
                    columns = if (columns == 3) 2 else 3
                }
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBox,
                    contentDescription = "Change column layout",
                    tint = Color.Black // Adjust color as needed
                )
            }
        }


        // We'll get the list of items from your State.Data, ignoring date headers
        val allMediaItems = remember(state) {
            val dataList = (state as? State.Data)?.list ?: emptyList()
            // Extract only the Image/Video items and ignore the Date items.
            dataList.filter { it is GalleryItem.Image || it is GalleryItem.Video }
        }

        // Build the flatten index maps each time the data changes:
        // E.g. for items [Image(#1), Image(#2), Video(#3), ...] => index 0 -> #1, index 1 -> #2, etc.
        LaunchedEffect(allMediaItems) {
            val tempUriToIndex = mutableMapOf<Uri, Int>()
            val tempIndexToUri = mutableMapOf<Int, Uri>()
            var idx = 0
            allMediaItems.forEach { galleryItem ->
                val uri = when (galleryItem) {
                    is GalleryItem.Image -> galleryItem.mediaItem.uri
                    is GalleryItem.Video -> galleryItem.mediaItem.uri
                    else -> null
                }
                uri?.let {
                    tempUriToIndex[it] = idx
                    tempIndexToUri[idx] = it
                    idx++
                }
            }
            uriToIndex = tempUriToIndex
            indexToUri = tempIndexToUri
        }

        // Draggable overlay that draws the bounding rectangle
        AnimatedContent(columns) { cols ->
            DraggableSelectionOverlay(
                modifier = Modifier.fillMaxSize(),
                gridState = gridState,
                onSelectionChange = { selectionRect ->

                    // 1) Find all items physically overlapped
                    // Combine the fixed snapshot with any new items from live itemBounds
                    val combinedBounds = fixedItemBounds.toMutableMap().apply {
                        itemBounds.forEach { (uri, bounds) ->
                            if (!containsKey(uri)) {
                                put(uri, bounds)
                            }
                        }
                    }
                    val overlappedUris = combinedBounds.filter { selectionRect.overlaps(it.value) }.keys

                    if (overlappedUris.isEmpty()) {
                        // No items physically overlapped
                        viewModel.setSelection(emptySet())
                    } else {
                        // 2) Among them, find min & max index
                        val indexes = overlappedUris.mapNotNull { uriToIndex[it] }
                        val minIndex = indexes.minOrNull() ?: 0
                        val maxIndex = indexes.maxOrNull() ?: 0

                        // 3) Then select ALL items in that range
                        val urisInRange = (minIndex..maxIndex).mapNotNull { indexToUri[it] }
                        viewModel.setSelection(urisInRange.toSet())
                    }
                },
                onDragEnd = {
                    // No special action on drag end
                },
                itemBounds = itemBounds, // Pass itemBounds here
                fixedItemBounds = fixedItemBounds // NEW: pass fixed snapshot containe
            ) {
                // The actual LazyVerticalGrid content
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    state = gridState,
                ) {
                    items(
                        items = (state as? State.Data)?.list ?: emptyList(),
                        span = { item ->
                            when (item) {
                                is GalleryItem.Date -> GridItemSpan(cols)
                                else -> GridItemSpan(1)
                            }
                        }
                    ) { item ->
                        when (item) {
                            is GalleryItem.Date -> {
                                // For date headers, determine if all items under this category are selected.
                                val itemsForCategory =
                                    (state as State.Data).list.filter { galleryItem ->
                                        when (galleryItem) {
                                            is GalleryItem.Image -> viewModel.getDateLabel(
                                                galleryItem.mediaItem.date
                                            ) == item.date

                                            is GalleryItem.Video -> viewModel.getDateLabel(
                                                galleryItem.mediaItem.date
                                            ) == item.date

                                            else -> false
                                        }
                                    }.mapNotNull {
                                        when (it) {
                                            is GalleryItem.Image -> it.mediaItem.uri
                                            is GalleryItem.Video -> it.mediaItem.uri
                                            else -> null
                                        }
                                    }
                                val categorySelected =
                                    itemsForCategory.isNotEmpty() && itemsForCategory.all {
                                        selectedMedia.contains(it)
                                    }

                                // Date header becomes clickable for category selection.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (categorySelected) Color(0xFFBBDEFB) else Color.Transparent)
                                        .clickable { viewModel.toggleCategorySelection(item.date) }
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = item.date,
                                        style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            is GalleryItem.Image -> {
                                SelectableGridItem(
                                    uri = item.mediaItem.uri,
                                    onItemPositioned = { uri, bounds ->
                                        itemBounds[uri] = bounds
                                    },
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.combinedClickable(
                                            onClick = {
                                                if (selectedMedia.isEmpty()) {
                                                    previewUri = item.mediaItem.uri
                                                } else {
                                                    viewModel.toggleMediaSelection(item.mediaItem.uri)
                                                }
                                            },
                                            onLongClick = {
                                                viewModel.toggleMediaSelection(item.mediaItem.uri)
                                            }
                                        )
                                    ) {
                                        ImageThumbnail(width.div(columns), item.mediaItem.uri)
                                        if (selectedMedia.contains(item.mediaItem.uri)) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(Color(0x800000FF))
                                            )
                                        }
                                    }
                                }
                            }


                            is GalleryItem.Video -> {
                                SelectableGridItem(
                                    uri = item.mediaItem.uri,
                                    onItemPositioned = { uri, bounds ->
                                        itemBounds[uri] = bounds
                                    },
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.combinedClickable(
                                            onClick = {
                                                if (selectedMedia.isEmpty()) {
                                                    previewUri = item.mediaItem.uri
                                                } else {
                                                    viewModel.toggleMediaSelection(item.mediaItem.uri)
                                                }
                                            },
                                            onLongClick = {
                                                viewModel.toggleMediaSelection(item.mediaItem.uri)
                                            }
                                        )
                                    ) {
                                        VideoThumbnail(
                                            width = width.div(columns),
                                            uri = item.mediaItem.uri,
                                            duration = item.mediaItem.duration,
                                        )
                                        if (selectedMedia.contains(item.mediaItem.uri)) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(Color(0x800000FF))
                                            )
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenPreview(uri: Uri, onDismiss: () -> Unit) {
    // A simple full-screen dialog-like preview.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Using AsyncImage from Coil to display the image.
        AsyncImage(
            model = uri,
            contentDescription = "Full screen preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        // You can add a close icon/button here if you prefer, for example:
        /*
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        */
    }
}


@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun ImageThumbnail(
    width: Dp,
    uri: Uri,
) {
    val cancellationSignal by remember(uri.toString()) { mutableStateOf(CancellationSignal()) }
    DisposableEffect(uri.toString()) {
        onDispose {
            cancellationSignal.cancel()
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .memoryCacheKey(uri.toString())
            .coroutineContext(Dispatchers.Default)
            .data(
                LocalContext.current.contentResolver.loadThumbnail(
                    uri, android.util.Size(250, 250), cancellationSignal,
                )
            )
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = "",
        modifier = Modifier.size(width),
        contentScale = ContentScale.Crop,
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun VideoThumbnail(
    width: Dp,
    uri: Uri,
    duration: Long,
    modifier: Modifier = Modifier,
) {

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ImageThumbnail(width, uri)
        // If it's a video, display the duration
        Text(
            text = formatDuration(duration),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

    }
}

fun formatDuration(durationMillis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}


@Composable
fun CoilImageExample(state: State, onStartClick: () -> Unit) {
    when (state) {
        is State.Data -> LazyColumn {
            items(state.list) {
//                Text(it, modifier = Modifier.padding(16.dp))
            }
        }

        is State.Error -> Text(state.message)
        State.Idle -> {
            Column {
                Text("Please start")
                Button(
                    onClick = onStartClick
                ) {
                    Text("Start")
                }
            }

        }

        State.Loading -> CircularProgressIndicator()
    }

    LazyVerticalGrid(columns = GridCells.Fixed(3)) { }
    LazyColumn {
        items(count = 100,
            itemContent = {
                val image =
                    rememberImagePainter("https://d3ccuprjuqkp1j.cloudfront.net/Attachments/NewItems/01-Stickers_v2_20201103210707_0.jpg")
                Image(
                    painter = image,
                    contentDescription = null,
                    modifier = Modifier.size(200.dp),
                    contentScale = ContentScale.FillWidth,
                )
            })
    }

}

@Composable
private fun Extracted() {
    val text = remember { mutableStateOf("") }

    Spacer(Modifier.size(56.dp))
    TextField(value = text.value, onValueChange = { str ->
        text.value = str
    })
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
//    CoilImageExample(){}
}

@Composable
fun PhotoPickerScreen() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { isGranted ->
        hasPermission = isGranted
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                imageUri = result.data?.data
            }
        }

    LaunchedEffect(Unit) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)

        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasPermission) {
            Button(onClick = {
                val intent = Intent(Intent.ACTION_PICK).apply {
                    type = "image/*"
                }
                photoPickerLauncher.launch(intent)
            }) {
                Text("Pick a Photo")
            }

            Spacer(modifier = Modifier.height(16.dp))

            imageUri?.let {
                Image(
                    painter = rememberImagePainter(
                        data = it,
//                        builder = {
//                            transformations(CircleCropTransformation())
//                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(400.dp)
                )
            }
        } else {
            Text("Permission is required to access photos.")
        }
    }
}