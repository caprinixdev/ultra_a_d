package gs.ad.gsadsexample.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import gs.ad.gsadsexample.MainActivity
import gs.ad.gsadsexample.OnBoardActivity
import gs.ad.gsadsexample.R
import gs.ad.utils.ads.AdmManager
import gs.ad.utils.utils.PreferencesManager

class OnboardPagerAdapter(
    private val context: Context,
    private val items: List<Any>,
    val admManager: AdmManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_NATIVE_AD = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is Int) TYPE_IMAGE else TYPE_NATIVE_AD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_IMAGE -> {
                val view = LayoutInflater.from(context).inflate(R.layout.adapter_onboard_page, parent, false)
                ImageViewHolder(view)
            }
            TYPE_NATIVE_AD -> {
                val view = LayoutInflater.from(context).inflate(R.layout.adapter_native_ad_page, parent, false)
                NativeAdViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unsupported view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageViewHolder -> holder.bind(position, items[position] as Int, admManager)
            is NativeAdViewHolder -> holder.bind(position, admManager)
        }
    }

    override fun getItemCount(): Int = items.size

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.adapter_onboard_image)
        private val adContainer: ConstraintLayout = view.findViewById(R.id.adapter_onboard_image_native_ad_frame)
        private val dot1: CardView = view.findViewById(R.id.onboard_indicator_dot1)
        private val dot2: CardView = view.findViewById(R.id.onboard_indicator_dot2)
        private val dot3: CardView = view.findViewById(R.id.onboard_indicator_dot3)
        private val next: TextView = view.findViewById(R.id.onboard_next)

        fun bind(position: Int, imageResId: Int, admManager: AdmManager) {
            imageView.setImageResource(imageResId)

            if (!PreferencesManager.getInstance().isSUB()){
                admManager.applyNativeAdView("NativeAd_ScOnBoard_" + (position + 1), adContainer, R.layout.layout_native_ad)
            }


            next.setOnClickListener {
                if (imageResId == R.drawable.onboard3) {
                    PreferencesManager.getInstance().saveShowOnBoard(true)
                    val intent = Intent(itemView.context, MainActivity::class.java)
                    itemView.context.startActivity(intent)
                    (itemView.context as? OnBoardActivity)?.removeListener()
                    (itemView.context as? OnBoardActivity)?.finish()
                } else {
                    val viewPager = itemView.parent as? RecyclerView
                    viewPager?.smoothScrollToPosition(adapterPosition + 1)
                }
            }

            when (imageResId) {
                R.drawable.onboard1 -> {
                    dot1.setCardBackgroundColor(itemView.resources.getColor(R.color.blue_x))
                    dot2.setCardBackgroundColor(itemView.resources.getColor(R.color.commonBackground))
                    dot3.setCardBackgroundColor(itemView.resources.getColor(R.color.commonBackground))
                    next.setText(R.string.next)
                }
                R.drawable.onboard2 -> {
                    dot1.setCardBackgroundColor(itemView.resources.getColor(R.color.commonBackground))
                    dot2.setCardBackgroundColor(itemView.resources.getColor(R.color.blue_x))
                    dot3.setCardBackgroundColor(itemView.resources.getColor(R.color.commonBackground))
                    next.setText(R.string.next)
                }
                R.drawable.onboard3 -> {
                    dot1.setCardBackgroundColor(itemView.resources.getColor(R.color.commonBackground))
                    dot2.setCardBackgroundColor(itemView.resources.getColor(R.color.commonBackground))
                    dot3.setCardBackgroundColor(itemView.resources.getColor(R.color.blue_x))
                    next.setText(R.string.get_started)
                }
            }
        }
    }

    class NativeAdViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val adContainer: ConstraintLayout = view.findViewById(R.id.adapter_onboard_native_ad_frame)

        fun bind(position: Int, admManager: AdmManager) {
            if (!PreferencesManager.getInstance().isSUB()){
                admManager.applyNativeAdView("NativeAd_ScOnBoard_" + (position + 1), adContainer, R.layout.layout_native_ad_full)
            }
        }
    }

}