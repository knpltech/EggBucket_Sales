package com.example.eggbucketsales.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.eggbucketsales.Models.CheckReason
import com.example.eggbucketsales.R

class CheckReasonAdapter(
    private val reasons: List<CheckReason>,
    private var selectedReasonId: String? = null,
    private val onReasonClicked: (CheckReason) -> Unit
) : RecyclerView.Adapter<CheckReasonAdapter.ViewHolder>() {

    fun getSelectedReasonId(): String? = selectedReasonId

    fun setSelectedReasonId(id: String?) {
        selectedReasonId = id
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContainer: LinearLayout = view.findViewById(R.id.cardContainer)
        val ivIcon: ImageView = view.findViewById(R.id.ivReasonIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvReasonTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvReasonSubtitle)
        val ivRadioSelected: ImageView = view.findViewById(R.id.ivRadioSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_check_reason_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = reasons.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reason = reasons[position]
        val isSelected = reason.id == selectedReasonId

        holder.tvTitle.text = reason.title
        holder.tvSubtitle.text = reason.subtitle
        holder.ivIcon.setImageResource(reason.iconResId)

        if (isSelected) {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_reason_card_selected)
            holder.ivRadioSelected.setImageResource(R.drawable.ic_radio_selected)
        } else {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_reason_card_unselected)
            holder.ivRadioSelected.setImageResource(R.drawable.ic_radio_unselected)
        }

        holder.itemView.setOnClickListener {
            val previousId = selectedReasonId
            selectedReasonId = reason.id
            if (previousId != selectedReasonId) {
                notifyDataSetChanged()
            }
            onReasonClicked(reason)
        }
    }
}
