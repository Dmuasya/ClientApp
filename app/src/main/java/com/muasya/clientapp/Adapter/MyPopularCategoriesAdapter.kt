package com.muasya.clientapp.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import com.bumptech.glide.Glide
import com.muasya.clientapp.Callback.IRecyclerItemClickListener
import com.muasya.clientapp.EventBus.PopularFoodItemClick
import com.muasya.clientapp.Model.PopularCategoryModel
import com.muasya.clientapp.R
import de.hdodenhof.circleimageview.CircleImageView
import org.greenrobot.eventbus.EventBus


class MyPopularCategoriesAdapter (internal var context:Context,
                                  internal var popularCategoryModels: List<PopularCategoryModel>):
RecyclerView.Adapter<MyPopularCategoriesAdapter.MyViewHolder>(){
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_popular_categories_item,parent,false))
    }

    override fun getItemCount(): Int {
        return popularCategoryModels.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        Glide.with(context).load(popularCategoryModels.get(position).image).into(holder.category_image!!)
        holder.category_name!!.setText(popularCategoryModels.get(position).name)

        holder.setListener(object:IRecyclerItemClickListener{
            override fun onItemClick(view: View, pos: Int) {
                EventBus.getDefault()
                    .postSticky(PopularFoodItemClick(popularCategoryModels[pos]))
            }

        })
    }

    inner class MyViewHolder (itemView:View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        override fun onClick(p0: View?) {
            listener!!.onItemClick(p0!!,adapterPosition)
        }

        var category_name:TextView?=null
        var category_image:CircleImageView?=null

        internal var listener: IRecyclerItemClickListener?=null

        fun setListener(listener: IRecyclerItemClickListener)
        {
            this.listener=listener
        }

        init {
            category_name = itemView.findViewById(R.id.txt_category_name) as TextView
            category_image = itemView.findViewById(R.id.category_image) as CircleImageView
            itemView.setOnClickListener(this)
        }

    }

}