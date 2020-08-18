package com.zeapo.pwdstore.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zeapo.pwdstore.utils.MRU

class MRUPasswordItemRecyclerAdapter : RecyclerView.Adapter<MRUPasswordItemRecyclerAdapter.ViewHolder>() {
    private val mruPasswords = MRU.mRU
    var onItemClick: ((String) -> Unit)? = null



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItems(mruPasswords[position])
    }

    override fun getItemCount(): Int {
        return mruPasswords.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener{
                onItemClick?.invoke(mruPasswords[absoluteAdapterPosition])
            }
        }
        fun bindItems(path : String) {
            val textPath = itemView.findViewById<TextView>(android.R.id.text1)
            val relativePath = path.split("store/")
            textPath.text = relativePath[1]
        }
    }
}