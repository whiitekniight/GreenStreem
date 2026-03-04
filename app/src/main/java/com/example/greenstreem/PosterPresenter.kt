package com.example.greenstreem

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide

/**
 * A Presenter for Movie and Series posters (Vertical aspect ratio).
 */
class PosterPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(POSTER_WIDTH, POSTER_HEIGHT)
            isFocusable = true
            isFocusableInTouchMode = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.selector_button_bg)
        }
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as? Channel ?: return // Reusing Channel model for VOD for now
        val imageView = viewHolder.view as ImageView

        Glide.with(viewHolder.view.context)
            .load(movie.logoUrl)
            .centerCrop()
            .into(imageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val imageView = viewHolder.view as ImageView
        imageView.setImageDrawable(null)
    }

    companion object {
        private const val POSTER_WIDTH = 180
        private const val POSTER_HEIGHT = 270
    }
}
