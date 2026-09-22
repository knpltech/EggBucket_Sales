package com.example.eggbucketsales

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.eggbucketsales.Adapters.CheckReasonAdapter
import com.example.eggbucketsales.Models.CheckReason
import com.example.eggbucketsales.Models.Customer
import com.google.android.material.button.MaterialButton

class CheckReasonBottomSheetDialog(
    private val customer: Customer,
    private val onReasonSelected: (actionType: String) -> Unit
) : DialogFragment() {

    private lateinit var adapter: CheckReasonAdapter

    private val reasons = listOf(
        // Row 1 (Left: 1, Right: 2)
        CheckReason(
            id = "shop_closed",
            title = "Shop Closed",
            subtitle = "Shop is closed today",
            iconResId = R.drawable.ic_reason_shop_closed
        ),
        CheckReason(
            id = "stock_available",
            title = "Stock Available",
            subtitle = "Customer already has eggs",
            iconResId = R.drawable.ic_reason_stock_available
        ),

        // Row 2 (Left: 3, Right: 4)
        CheckReason(
            id = "confirmed_tomorrow",
            title = "Confirmed For Tomorrow",
            subtitle = "Will take delivery tomorrow",
            iconResId = R.drawable.ic_reason_confirmed_tomorrow
        ),
        CheckReason(
            id = "price_issue",
            title = "Price Issue",
            subtitle = "Feels price is high",
            iconResId = R.drawable.ic_reason_price_issue
        ),

        // Row 3 (Left: 5, Right: 6)
        CheckReason(
            id = "other_vendor",
            title = "Other Vendor",
            subtitle = "Buying from another supplier",
            iconResId = R.drawable.ic_reason_other_vendor
        ),
        CheckReason(
            id = "need_credit",
            title = "Need Credit",
            subtitle = "Customer needs credit facility",
            iconResId = R.drawable.ic_reason_need_credit
        ),

        // Row 4 (Left: 7, Right: 8)
        CheckReason(
            id = "quality_issue",
            title = "Quality Issue",
            subtitle = "Egg quality or broken issue",
            iconResId = R.drawable.ic_reason_quality_issue
        ),
        CheckReason(
            id = "not_available",
            title = "Owner Not Available",
            subtitle = "Owner or staff not available",
            iconResId = R.drawable.ic_reason_not_available
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_check_reason_bottom_sheet, container, false)

        val btnBack = view.findViewById<ImageView>(R.id.btnBackCheck)
        val tvCustomerInfo = view.findViewById<TextView>(R.id.tvCustomerInfo)
        val rvReasons = view.findViewById<RecyclerView>(R.id.rvReasons)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitCheck)

        btnBack?.setOnClickListener {
            dismiss()
        }

        if (customer.business.isNotEmpty()) {
            tvCustomerInfo?.text = "${customer.business} (${customer.name})"
            tvCustomerInfo?.visibility = View.VISIBLE
        } else if (customer.name.isNotEmpty()) {
            tvCustomerInfo?.text = customer.name
            tvCustomerInfo?.visibility = View.VISIBLE
        }

        adapter = CheckReasonAdapter(reasons) { _ ->
            // Reason card tapped
        }

        rvReasons.layoutManager = GridLayoutManager(requireContext(), 2)
        rvReasons.adapter = adapter

        btnSubmit.setOnClickListener {
            val selectedReasonId = adapter.getSelectedReasonId()
            if (selectedReasonId.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Please select a reason", Toast.LENGTH_SHORT).show()
            } else {
                onReasonSelected(selectedReasonId)
                dismiss()
            }
        }

        return view
    }
}
