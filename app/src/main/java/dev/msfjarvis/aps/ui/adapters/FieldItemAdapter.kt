package dev.msfjarvis.aps.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.msfjarvis.aps.data.password.FieldItem
import dev.msfjarvis.aps.databinding.ItemFieldBinding

class FieldItemAdapter(private val fieldItemList: List<FieldItem>) :
  RecyclerView.Adapter<FieldItemAdapter.FieldItemViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldItemViewHolder {
    val binding = ItemFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return FieldItemViewHolder(binding.root, binding)
  }

  override fun onBindViewHolder(holder: FieldItemViewHolder, position: Int) {
    holder.bind(fieldItemList[position])
  }

  override fun getItemCount(): Int {
    return fieldItemList.size
  }

  class FieldItemViewHolder(itemView: View, val binding: ItemFieldBinding) :
    RecyclerView.ViewHolder(itemView) {

    fun bind(fieldItem: FieldItem) {
      with(binding) {

      }
    }
  }
}