package info.thanhtunguet.tvglasses

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoAdapter(
    private val onVideoClick: (VideoFile) -> Unit,
    private val onSelectionToggle: (VideoFile, Boolean) -> Unit,
    private val coroutineScope: CoroutineScope
) : ListAdapter<VideoFile, VideoAdapter.VideoViewHolder>(VideoFileDiffCallback()) {

    private var selectedPaths: Set<String> = emptySet()

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.imageVideoThumbnail)
        val name: TextView = view.findViewById(R.id.textVideoName)
        val size: TextView = view.findViewById(R.id.textVideoSize)
        val date: TextView = view.findViewById(R.id.textVideoDate)
        val path: TextView = view.findViewById(R.id.textVideoPath)
        val duration: TextView = view.findViewById(R.id.textVideoDuration)
        val checkBox: CheckBox = view.findViewById(R.id.checkboxSelectVideo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = getItem(position)
        
        // Set video information
        holder.name.text = video.name
        holder.size.text = video.sizeFormatted
        holder.date.text = video.dateFormatted
        holder.path.text = video.path
        
        // Set duration (show duration if available, otherwise hide the badge)
        if (video.duration > 0) {
            holder.duration.text = video.durationFormatted
            holder.duration.visibility = View.VISIBLE
        } else {
            holder.duration.visibility = View.GONE
        }
        
        // Clear previous thumbnail
        holder.thumbnail.setImageDrawable(null)
        
        // Load thumbnail asynchronously
        loadThumbnail(video, holder.thumbnail)
        
        // Selection checkbox handling
        holder.checkBox.setOnCheckedChangeListener(null)
        val isSelected = selectedPaths.contains(video.path)
        holder.checkBox.isChecked = isSelected
        holder.checkBox.setOnCheckedChangeListener { _, checked ->
            onSelectionToggle(video, checked)
        }

        // Set click listener for playback
        holder.itemView.setOnClickListener {
            onVideoClick(video)
        }

        holder.itemView.setOnLongClickListener {
            val newState = !selectedPaths.contains(video.path)
            onSelectionToggle(video, newState)
            true
        }
    }

    fun updateSelection(selection: Set<String>) {
        selectedPaths = selection.toSet()
        notifyDataSetChanged()
    }
    
    private fun loadThumbnail(video: VideoFile, imageView: ImageView) {
        coroutineScope.launch {
            val thumbnail = generateThumbnail(video.path)
            withContext(Dispatchers.Main) {
                if (thumbnail != null) {
                    imageView.setImageBitmap(thumbnail)
                } else {
                    // Set a default placeholder or keep empty
                    imageView.setImageDrawable(null)
                }
            }
        }
    }
    
    private suspend fun generateThumbnail(videoPath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check if file exists
            val file = File(videoPath)
            if (!file.exists()) return@withContext null
            
            // Try to get thumbnail using ThumbnailUtils
            ThumbnailUtils.createVideoThumbnail(
                videoPath,
                MediaStore.Images.Thumbnails.MINI_KIND
            )
        } catch (e: Exception) {
            // Log error but don't crash the app
            android.util.Log.w("VideoAdapter", "Failed to generate thumbnail for $videoPath", e)
            null
        }
    }
}

class VideoFileDiffCallback : DiffUtil.ItemCallback<VideoFile>() {
    override fun areItemsTheSame(oldItem: VideoFile, newItem: VideoFile): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: VideoFile, newItem: VideoFile): Boolean {
        return oldItem == newItem
    }
}
