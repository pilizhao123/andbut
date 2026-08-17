package com.example.autoclicker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.autoclicker.R
import com.example.autoclicker.data.ClickPoint

/**
 * 点击点列表适配器。
 *
 * 使用 [ListAdapter] + [DiffUtil] 实现高效差异刷新；每项提供删除按钮。
 *
 * @param onDelete 删除某一坐标点的回调。
 */
class ClickPointAdapter(
    private val onDelete: (ClickPoint) -> Unit
) : ListAdapter<ClickPoint, ClickPointAdapter.PointViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_click_point, parent, false)
        return PointViewHolder(view)
    }

    override fun onBindViewHolder(holder: PointViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvInfo: TextView = itemView.findViewById(R.id.tv_point_info)
        private val btnDelete: Button = itemView.findViewById(R.id.btn_delete_point)

        fun bind(point: ClickPoint) {
            val label = if (point.label.isBlank()) {
                itemView.context.getString(R.string.point_default_label, bindingAdapterPosition + 1)
            } else {
                point.label
            }
            tvInfo.text = itemView.context.getString(
                R.string.point_info_format,
                label,
                point.x,
                point.y
            )
            btnDelete.setOnClickListener { onDelete(point) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ClickPoint>() {
            override fun areItemsTheSame(oldItem: ClickPoint, newItem: ClickPoint): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ClickPoint, newItem: ClickPoint): Boolean =
                oldItem == newItem
        }
    }
}
