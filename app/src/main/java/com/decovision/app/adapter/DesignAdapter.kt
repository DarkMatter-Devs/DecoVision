package com.decovision.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.decovision.app.data.model.Design
import com.decovision.app.databinding.ItemDesignBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class DesignAdapter(
    private val onItemClick: (Design) -> Unit,
    private val onLongClick: (Design) -> Unit
) : ListAdapter<Design, DesignAdapter.ViewHolder>(DiffCallback()) {

    private val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemDesignBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemDesignBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(design: Design) {
            b.tvDesignName.text = design.name
            b.tvDesignDate.text = fmt.format(design.createdAt)
            b.ivThumbnail.load(File(design.thumbnailPath)) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }
            val click = { onItemClick(design) }
            b.root.setOnClickListener { click() }
            b.ivThumbnail.setOnClickListener { click() }
            b.tvDesignName.setOnClickListener { click() }
            b.tvDesignDate.setOnClickListener { click() }
            b.root.setOnLongClickListener { onLongClick(design); true }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Design>() {
        override fun areItemsTheSame(a: Design, b: Design) = a.id == b.id
        override fun areContentsTheSame(a: Design, b: Design) = a == b
    }
}
