package ir.sharif.android.photogallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min


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


/**
 * The new pointer-input modifier for drag selection.
 * It works on the LazyVerticalGrid by first mapping visible grid items that have
 * a media-item key (an Int) and then, after a long press, updating the selection
 * (stored as a set of Int indices) by unioning the range between the initial and current item.
 */
fun Modifier.photoGridDragHandler(
    lazyGridState: LazyGridState,
    haptics: HapticFeedback,
    selectedIndices: MutableState<Set<Int>>,
    autoScrollSpeed: MutableState<Float>,
    autoScrollThreshold: Float,
    hadDragMovement: MutableState<Boolean>,
    suppressClick: MutableState<Boolean>,
    resetSuppressTrigger: MutableState<Boolean> // New parameter
) = pointerInput(Unit) {
    fun LazyGridState.gridItemKeyAtPosition(hitPoint: Offset): Int? =
        layoutInfo.visibleItemsInfo.find { itemInfo ->
            // Use the item's size as a rect and check if the hitPoint (adjusted by offset) is within.
            itemInfo.size.toIntRect().contains(hitPoint.round() - itemInfo.offset)
        }?.key as? Int

    var initialKey: Int? = null
    var currentKey: Int? = null

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            lazyGridState.gridItemKeyAtPosition(offset)?.let { key ->
                if (!selectedIndices.value.contains(key)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    initialKey = key
                    currentKey = key
                    selectedIndices.value += key
                }
            }
        },
        onDragCancel = {
            initialKey = null
            autoScrollSpeed.value = 0f
            hadDragMovement.value = false
            resetSuppressTrigger.value = !resetSuppressTrigger.value
        },
        onDragEnd = {
            // If a drag movement happened, then flag to suppress the subsequent click.
            if (hadDragMovement.value) {
                suppressClick.value = true
            }
            initialKey = null
            autoScrollSpeed.value = 0f
            hadDragMovement.value = false
            // Instead of launch, trigger a state change for resetting suppressClick.
            resetSuppressTrigger.value = !resetSuppressTrigger.value
        },
        onDrag = { change, dragAmount ->
            // If the movement exceeds a small threshold, mark that we had a drag.
            if (dragAmount.getDistance() > 4f) {
                hadDragMovement.value = true
            }
            if (initialKey != null) {
                val distFromBottom =
                    lazyGridState.layoutInfo.viewportSize.height - change.position.y
                val distFromTop = change.position.y
                autoScrollSpeed.value = when {
                    distFromBottom < autoScrollThreshold -> autoScrollThreshold - distFromBottom
                    distFromTop < autoScrollThreshold -> -(autoScrollThreshold - distFromTop)
                    else -> 0f
                }
                lazyGridState.gridItemKeyAtPosition(change.position)?.let { key ->
                    if (currentKey != key) {
                        val oldMin = min(initialKey!!, currentKey!!)
                        val oldMax = max(initialKey!!, currentKey!!)
                        val newMin = min(initialKey!!, key)
                        val newMax = max(initialKey!!, key)
                        // Remove the previously selected range and add the new range.
                        selectedIndices.value =
                            (selectedIndices.value - (oldMin..oldMax)) + (newMin..newMax)
                        currentKey = key
                    }
                }
            }
        }
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListScreen(width: Dp) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()

    // Trigger loading of media items when this composable enters composition.
    LaunchedEffect(Unit) {
        viewModel.loadMediaItems(context)
    }

    // Collect the state from the ViewModel
    val state by viewModel.state.collectAsState()
    // We'll use our own selection state (flattened indices) for drag and tap selection.
    val selectedIndices = remember { mutableStateOf<Set<Int>>(emptySet()) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    val gridState = rememberLazyGridState()
    var columns by remember { mutableIntStateOf(3) }
    val autoScrollSpeed = remember { mutableStateOf(0f) }

    // Auto-scroll effect during drag.
    LaunchedEffect(autoScrollSpeed.value) {
        if (autoScrollSpeed.value != 0f) {
            while (isActive) {
                gridState.scrollBy(autoScrollSpeed.value)
                delay(10)
            }
        }
    }

    // Build a flattened mapping (URI ⇄ grid index) for media items.
    val (uriToIndex, indexToUri) = remember(state) {
        val tempUriToIndex = mutableMapOf<Uri, Int>()
        val tempIndexToUri = mutableMapOf<Int, Uri>()
        var idx = 0
        (state as? State.Data)?.list?.forEach { galleryItem ->
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
        tempUriToIndex to tempIndexToUri
    }

    // Sync local selection (indices) to the ViewModel (as URIs).
    LaunchedEffect(selectedIndices.value) {
        val selectedUris = selectedIndices.value.mapNotNull { indexToUri[it] }.toSet()
        viewModel.setSelection(selectedUris)
    }

    val inSelectionMode = selectedIndices.value.isNotEmpty()

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
            if (inSelectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // X icon to clear selection.
                    IconButton(
                        onClick = { selectedIndices.value = emptySet() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear selection",
                            tint = Color.Black // Adjust color as needed.
                        )
                    }
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = "${selectedIndices.value.size} selected",
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

        val hadDragMovement = remember { mutableStateOf(false) }
        val suppressClick = remember { mutableStateOf(false) }

        // In MediaListScreen (or your parent composable)
        val resetSuppressTrigger = remember { mutableStateOf(false) }

        // Use a LaunchedEffect outside the pointerInput to reset the suppressClick flag
        LaunchedEffect(resetSuppressTrigger.value) {
            delay(50)
            suppressClick.value = false
        }

        // The actual LazyVerticalGrid content
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .photoGridDragHandler(
                    lazyGridState = gridState,
                    haptics = LocalHapticFeedback.current,
                    selectedIndices = selectedIndices,
                    autoScrollSpeed = autoScrollSpeed,
                    autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() },
                    hadDragMovement = hadDragMovement,
                    suppressClick = suppressClick,
                    resetSuppressTrigger = resetSuppressTrigger
                )
        ) {
            items(
                items = (state as? State.Data)?.list ?: emptyList(),
                key = { item ->
                    when (item) {
                        is GalleryItem.Date -> "date-${item.date}"
                        is GalleryItem.Image -> uriToIndex[item.mediaItem.uri] ?: -1
                        is GalleryItem.Video -> uriToIndex[item.mediaItem.uri] ?: -1
                    }
                },
                span = { item ->
                    when (item) {
                        is GalleryItem.Date -> GridItemSpan(columns)
                        else -> GridItemSpan(1)
                    }
                }
            ) { item ->
                when (item) {
                    is GalleryItem.Date -> {
                        // For date headers, allow tapping to toggle selection for the whole category.
                        val itemsForCategory = (state as State.Data).list.filter { galleryItem ->
                            when (galleryItem) {
                                is GalleryItem.Image -> viewModel.getDateLabel(galleryItem.mediaItem.date) == item.date
                                is GalleryItem.Video -> viewModel.getDateLabel(galleryItem.mediaItem.date) == item.date
                                else -> false
                            }
                        }.mapNotNull {
                            when (it) {
                                is GalleryItem.Image -> uriToIndex[it.mediaItem.uri]
                                is GalleryItem.Video -> uriToIndex[it.mediaItem.uri]
                                else -> null
                            }
                        }
                        val categorySelected = itemsForCategory.isNotEmpty() &&
                                itemsForCategory.all { selectedIndices.value.contains(it) }

                        // Date header becomes clickable for category selection.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (categorySelected) Color(0xFFBBDEFB) else Color.Transparent)
                                .clickable {
                                    if (categorySelected) {
                                        selectedIndices.value -= itemsForCategory.toSet()
                                    } else {
                                        selectedIndices.value =
                                            selectedIndices.value.union(itemsForCategory.toSet())
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    is GalleryItem.Image -> {
                        val index = uriToIndex[item.mediaItem.uri]
                        Box(modifier = Modifier.padding(4.dp)) {
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (suppressClick.value) {
                                            return@combinedClickable
                                        }
                                        if (!inSelectionMode) {
                                            previewUri = item.mediaItem.uri
                                        } else {
                                            index?.let {
                                                // Toggle selection.
                                                selectedIndices.value =
                                                    if (selectedIndices.value.contains(it))
                                                        selectedIndices.value - it
                                                    else
                                                        selectedIndices.value + it
                                            }
                                        }
                                    },
//                                    onLongClick = {
//                                        index?.let {
//                                            if (!inSelectionMode) {
//                                                selectedIndices.value += it
//                                            } else {
//                                                selectedIndices.value = if (selectedIndices.value.contains(it))
//                                                    selectedIndices.value - it
//                                                else
//                                                    selectedIndices.value + it
//                                            }
//                                        }
//                                    }
                                )
                            ) {
                                ImageThumbnail(width.div(columns), item.mediaItem.uri)
                                if (inSelectionMode) {
                                    // Display selection indicator at top-left:
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (index != null && selectedIndices.value.contains(index)) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
//                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.FavoriteBorder,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }


                    is GalleryItem.Video -> {
                        val index = uriToIndex[item.mediaItem.uri]
                        Box(modifier = Modifier.padding(4.dp)) {
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (suppressClick.value) {
                                            return@combinedClickable
                                        }
                                        if (!inSelectionMode) {
                                            previewUri = item.mediaItem.uri
                                        } else {
                                            index?.let {
                                                // Toggle selection.
                                                selectedIndices.value =
                                                    if (selectedIndices.value.contains(it))
                                                        selectedIndices.value - it
                                                    else
                                                        selectedIndices.value + it
                                            }
                                        }
                                    },
//                                    onLongClick = {
//                                        index?.let {
//                                            if (!inSelectionMode) {
//                                                selectedIndices.value += it
//                                            } else {
//                                                selectedIndices.value =
//                                                    if (selectedIndices.value.contains(it))
//                                                        selectedIndices.value - it
//                                                    else
//                                                        selectedIndices.value + it
//                                            }
//                                        }
//                                    }
                                )
                            ) {
                                VideoThumbnail(
                                    width = width.div(columns),
                                    uri = item.mediaItem.uri,
                                    duration = item.mediaItem.duration
                                )
                                if (inSelectionMode) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (index != null && selectedIndices.value.contains(index)) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
//                                            tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.FavoriteBorder,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
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
                runCatching {
                    LocalContext.current.contentResolver.loadThumbnail(
                        uri,
                        android.util.Size(250, 250),
                        cancellationSignal,
                    )
                }.getOrElse { throwable ->
                    Log.e("ImageThumbnail", "Error loading thumbnail for $uri: ${throwable.message}")
                    // Fallback: return a default bitmap (make sure you have this drawable resource)
//                    BitmapFactory.decodeResource(LocalContext.current.resources, R.drawable.ic_launcher_foreground)
                }
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


//@Composable
//fun SelectableGridItem(
//    uri: Uri,
//    onItemPositioned: (uri: Uri, bounds: Rect) -> Unit,
//    modifier: Modifier = Modifier,
//    content: @Composable () -> Unit
//) {
//    Box(
//        modifier = modifier
//            .onGloballyPositioned { layoutCoordinates ->
//                // Use boundsInRoot() so that coordinates match the overlay's coordinate system.
//                val bounds = layoutCoordinates.boundsInRoot()
//                onItemPositioned(uri, bounds)
//            }
//    ) {
//        content()
//    }
//}
//
//@Composable
//fun DraggableSelectionOverlay(
//    modifier: Modifier = Modifier,
//    onSelectionChange: (Rect) -> Unit,
//    onDragEnd: () -> Unit,
//    gridState: LazyGridState,
//    itemBounds: Map<Uri, Rect>, // New parameter
//    fixedItemBounds: MutableMap<Uri, Rect>, // NEW parameter: snapshot of bounds
//
//    content: @Composable () -> Unit
//) {
//    var fixedGlobalDragStart by remember { mutableStateOf<Offset?>(null) }
//    val dragActive = remember { mutableStateOf(false) }
//
//    // For auto-scroll checks
//    val coroutineScope = rememberCoroutineScope()
//
//    // We'll track the overlay's position in root coords
//    var overlayPosInRoot by remember { mutableStateOf(Offset.Zero) }
//    var overlaySizePx by remember { mutableStateOf(IntSize.Zero) }
//
//    // Once we actually "start" a drag, we lock the overlay's position
//    // so it doesn't shift if the user scrolls the list.
//    var lockedOverlayPos by remember { mutableStateOf<Offset?>(null) }
//
//    // The local pointer offset where the drag started, and current pointer
//    var dragStart by remember { mutableStateOf<Offset?>(null) }
//    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
//
//    // A small threshold so we don't interpret tiny accidental moves as a full drag
//    val TOUCH_SLOP = 20f
//    val HOLD_DURATION_MS = 200L // Adjust timing as needed (200 milliseconds)
//
//    /**
//     * Compute a rectangle in "root" coordinates, using the locked overlay offset
//     * (once a drag is recognized) or the current overlay offset (before that).
//     */
//    fun calculateRect(start: Offset?, current: Offset?): Rect? {
//        if (start == null || current == null) return null
//        // Use fixedGlobalDragStart (if available) as the start point.
//        val startGlobal = fixedGlobalDragStart ?: (start + (lockedOverlayPos ?: overlayPosInRoot))
//        val currentGlobal = current + (lockedOverlayPos ?: overlayPosInRoot)
//        return Rect(
//            topLeft = Offset(
//                x = min(startGlobal.x, currentGlobal.x),
//                y = min(startGlobal.y, currentGlobal.y)
//            ),
//            bottomRight = Offset(
//                x = max(startGlobal.x, currentGlobal.x),
//                y = max(startGlobal.y, currentGlobal.y)
//            )
//        )
//    }
//
//
//    // Our pointer logic:
//    // 1) Wait for the user to put a finger down
//    // 2) If they move more than TOUCH_SLOP, we consider it a "drag"
//    // 3) If they never move that far and then lift, it was a simple tap/long-press for the child
//    // Pointer logic with origin check
//    val pointerModifier = Modifier.pointerInput(Unit) {
//        awaitPointerEventScope {
//            while (true) {
//                fixedItemBounds.clear()  // NEW: Clear the snapshot for a new drag session
//                val down = awaitFirstDown(requireUnconsumed = false)
//                val startTime = System.currentTimeMillis() // Track initial touch time
//                var pointerId = down.id
//                var totalDistance = 0f
//                var isDragging = false
//                dragStart = null
//                dragCurrent = null
//                lockedOverlayPos = null
//
//                while (true) {
//                    val event = awaitPointerEvent(PointerEventPass.Initial)
//                    if (event.changes.any { !it.pressed }) break
//
//                    val change = event.changes.find { it.id == pointerId } ?: event.changes.first()
//                    pointerId = change.id
//                    val move = change.positionChange()
//                    if (move != Offset.Zero) {
//                        totalDistance += move.getDistance()
//
//                        if (!isDragging) {
//                            val elapsedTime = System.currentTimeMillis() - startTime
//
//                            // Cancel if moved too quickly (before hold duration)
//                            if (elapsedTime < HOLD_DURATION_MS && totalDistance > TOUCH_SLOP) {
//                                break
//                            }
//
//                            // Only allow drag after hold duration
//                            if (elapsedTime >= HOLD_DURATION_MS) {
//                                val startRootPos = down.position + overlayPosInRoot
//                                val isValidDrag = itemBounds.values.any { it.contains(startRootPos) }
//                                if (isValidDrag) {
//                                    lockedOverlayPos = overlayPosInRoot
//                                    fixedGlobalDragStart = down.position + lockedOverlayPos!!
//                                    dragStart = down.position
//                                    dragCurrent = down.position
//                                    isDragging = true
//                                    dragActive.value = true  // NEW: mark drag as active
//                                    // Capture a snapshot of the current item bounds
//                                    fixedItemBounds.clear()
//                                    fixedItemBounds.putAll(itemBounds)
//                                } else {
//                                    break
//                                }
//                            }
//                        }
//
//                        if (isDragging) {
//                            change.consume()
//                            dragCurrent = dragCurrent?.plus(move)
//                            calculateRect(dragStart, dragCurrent)?.let(onSelectionChange)
//
//                            // Auto-scroll logic
//                            val currY = dragCurrent!!.y
//                            val containerHeight = overlaySizePx.height.toFloat()
//                            when {
//                                currY > containerHeight - 200f ->
//                                    coroutineScope.launch { gridState.scrollBy(50f) }
//
//                                currY < 200f ->
//                                    coroutineScope.launch { gridState.scrollBy(-50f) }
//                            }
//                        }
//                    }
//                }
//                if (isDragging) onDragEnd()
//                (fixedItemBounds as MutableMap).clear()  // NEW: Clear snapshot after drag end
//                dragActive.value = false
//            }
//        }
//    }
//
//    // Compose UI: A Box that positions the overlay and applies our pointer logic
//    androidx.compose.foundation.layout.Box(
//        modifier = modifier
//            .onGloballyPositioned { coords ->
//                if (!dragActive.value) {  // NEW: update only when not dragging
//                    overlayPosInRoot = coords.positionInRoot()
//                }
//                overlaySizePx = coords.size
//            }
//            // We *don't* pass `PointerEventPass.Initial` or `.Final`:
//            // we let the child see events first. If the child doesn't fully consume them
//            // (i.e. user starts moving), we step in.
//            // If you find that the child *always* consumes them, you can try `PointerEventPass.Final`.
//            .then(pointerModifier)
//    ) {
//        // The grid or child content goes here
//        content()
//
//        // If a drag is in progress, draw the bounding rectangle
//        val rect = calculateRect(dragStart, dragCurrent)
//        if (rect != null) {
//            Canvas(modifier = Modifier.matchParentSize()) {
//                // For drawing, shift the rect by negative the *unlocked* overlayPosInRoot
//                val base = lockedOverlayPos ?: overlayPosInRoot
//                val rectTopLeft = rect.topLeft - base
//                drawRect(
//                    color = Color.Blue.copy(alpha = 0.2f),
//                    topLeft = rectTopLeft,
//                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
//                    style = Stroke(width = 2.dp.toPx())
//                )
//            }
//        }
//    }
//}


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
                    rememberAsyncImagePainter("https://d3ccuprjuqkp1j.cloudfront.net/Attachments/NewItems/01-Stickers_v2_20201103210707_0.jpg")
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
                    painter = rememberAsyncImagePainter(
                        model = it
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