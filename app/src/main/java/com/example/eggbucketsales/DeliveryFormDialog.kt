package com.example.eggbucketsales

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.eggbucketsales.Models.Customer

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
            if (customer.status?.lowercase() == "delivered" || customer.status?.lowercase() == "reached") {
                Toast.makeText(requireContext(), "Customer status is already updated for today", Toast.LENGTH_SHORT).show()
                dismiss()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), DeliveryDetailsActivity::class.java)
            intent.putExtra("customer", customer)
            startActivity(intent)
            dismiss()
        }

        reachedBtn.setOnClickListener {
            if (customer.status?.lowercase() == "delivered" || customer.status?.lowercase() == "reached") {
                Toast.makeText(requireContext(), "Customer status is already updated for today", Toast.LENGTH_SHORT).show()
                dismiss()
                return@setOnClickListener
            }
            dismiss()
            val reasonDialog = CheckReasonBottomSheetDialog(customer) { reasonId ->
                onActionSelected(reasonId)
            }
            reasonDialog.show(parentFragmentManager, "CheckReasonBottomSheetDialog")
        }

        cancelBtn.setOnClickListener {
            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }
}
