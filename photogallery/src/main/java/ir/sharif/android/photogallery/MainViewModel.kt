package ir.sharif.android.photogallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> get() = _state

    // New state to track selected media items (using their URI as a unique key)
    private val _selectedMedia = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedMedia: StateFlow<Set<Uri>> get() = _selectedMedia

    fun loadMediaItems(context: Context) {
        viewModelScope.launch {
            _state.emit(State.Loading)
            try {
                val list = fetchMediaItems(context)
                val mList = mutableListOf<GalleryItem>()
                val itemsByDate = list.groupBy { getDateLabel(it.date) }
                for ((dateLabel, items) in itemsByDate) {
                    // Add Date header
                    mList.add(GalleryItem.Date(dateLabel))
                    // Add media items
                    items.forEach { mediaItem ->
                        when (mediaItem) {
                            is ImageItem -> mList.add(GalleryItem.Image(mediaItem))
                            is VideoItem -> mList.add(GalleryItem.Video(mediaItem))
                        }
                    }
                }
                _state.emit(State.Data(mList))
            } catch (e: Exception) {
                _state.emit(State.Error(e.message ?: "An error occurred"))
            }
        }
    }

    fun getDateLabel(dateSeconds: Long): String {
        val dateMillis = dateSeconds * 1000 // Convert seconds to milliseconds
        val itemDate = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            // Reset time to midnight for accurate comparison
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val today = Calendar.getInstance().apply {
            // Reset time to midnight
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diff = today.timeInMillis - itemDate.timeInMillis
        val daysDiff = TimeUnit.MILLISECONDS.toDays(diff)

        return when (daysDiff) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> {
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                formatter.format(Date(dateMillis))
            }
        }
    }

    fun setSelection(newSelection: Set<Uri>) {
        _selectedMedia.value = newSelection
    }

//    // Toggle selection for an individual media item
//    fun toggleMediaSelection(uri: Uri) {
//        val current = _selectedMedia.value.toMutableSet()
//        if (current.contains(uri)) {
//            current.remove(uri)
//        } else {
//            current.add(uri)
//        }
//        _selectedMedia.value = current
//    }
//
//    // Toggle selection for a whole category (date label)
//    fun toggleCategorySelection(dateLabel: String) {
//        // Only proceed if state contains data
//        if (_state.value is State.Data) {
//            val currentData = (_state.value as State.Data).list
//            // Filter media items belonging to this category
//            val mediaItemsForCategory = currentData.filter { item ->
//                when (item) {
//                    is GalleryItem.Image -> getDateLabel(item.mediaItem.date) == dateLabel
//                    is GalleryItem.Video -> getDateLabel(item.mediaItem.date) == dateLabel
//                    else -> false
//                }
//            }.map {
//                when (it) {
//                    is GalleryItem.Image -> it.mediaItem.uri
//                    is GalleryItem.Video -> it.mediaItem.uri
//                    else -> null
//                }
//            }.filterNotNull()
//
//            val currentSelected = _selectedMedia.value.toMutableSet()
//            // Determine if all items in the category are already selected
//            val allSelected = mediaItemsForCategory.all { currentSelected.contains(it) }
//            if (allSelected) {
//                // Deselect all items in this category
//                mediaItemsForCategory.forEach { currentSelected.remove(it) }
//            } else {
//                // Select all items not already selected
//                mediaItemsForCategory.forEach { currentSelected.add(it) }
//            }
//            _selectedMedia.value = currentSelected
//        }
//    }
//
//    fun clearSelection() {
//        _selectedMedia.value = emptySet()
//    }

}

sealed interface State {
    data object Idle : State
    data object Loading : State
    data class Data(val list: List<GalleryItem>) : State
    data class Error(val message: String) : State
}

sealed interface GalleryItem {
    data class Image(val mediaItem: ImageItem) : GalleryItem
    data class Video(val mediaItem: VideoItem) : GalleryItem
    data class Date(val date: String) : GalleryItem
}
