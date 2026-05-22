package com.example.eggbucketretail

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.eggbucketretail.Models.Customer

class DeliveryFormDialog(
    private val customer: Customer,
    private val onActionSelected: (actionType: String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_delivery_form, null)

        val deliverBtn = view.findViewById<Button>(R.id.deliverButton)
        val reachedBtn = view.findViewById<Button>(R.id.reachedButton)
        val cancelBtn = view.findViewById<Button>(R.id.cancelButton)

        deliverBtn.setOnClickListener {
            val intent = Intent(requireContext(), DeliveryDetailsActivity::class.java)
            intent.putExtra("customer", customer)
            startActivity(intent)
            dismiss()
        }

//        reachedBtn.setOnClickListener {
//            onActionSelected("reached")
//            dismiss()
//        }
        reachedBtn.setOnClickListener { view ->
            val popupMenu = PopupMenu(requireContext(), view)

            popupMenu.menu.add("SHOP CLOSED")
            popupMenu.menu.add("STOCK AVAILABLE")
            popupMenu.menu.add("OTHER VENDOR")

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "SHOP CLOSED" -> onActionSelected("shop_closed")
                    "STOCK AVAILABLE" -> onActionSelected("stock_available")
                    "OTHER VENDOR" -> onActionSelected("other_vendor")
                }
                dismiss()
                true
            }

            popupMenu.show()
        }

        cancelBtn.setOnClickListener {
            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }
}
