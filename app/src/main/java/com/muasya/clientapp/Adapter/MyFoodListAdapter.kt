package com.muasya.clientapp.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.muasya.clientapp.Callback.IRecyclerItemClickListener
import com.muasya.clientapp.Common.Common
import com.muasya.clientapp.Database.CartDataSource
import com.muasya.clientapp.Database.CartDatabase
import com.muasya.clientapp.Database.CartItem
import com.muasya.clientapp.Database.LocalCartDataSource
import com.muasya.clientapp.EventBus.CategoryClick
import com.muasya.clientapp.EventBus.CountCartEvent
import com.muasya.clientapp.EventBus.FoodItemClick
import com.muasya.clientapp.Model.CategoryModel
import com.muasya.clientapp.Model.FoodModel
import com.muasya.clientapp.R
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

class MyFoodListAdapter (internal var context: Context,
                           internal var foodList: List<FoodModel>):
    RecyclerView.Adapter<MyFoodListAdapter.MyViewHolder>(){

    private val compositeDisposable : CompositeDisposable
    private val cartDataSource : CartDataSource

    init {
        compositeDisposable = CompositeDisposable()
        cartDataSource = LocalCartDataSource(CartDatabase.getInstance(context).cartDAO())
    }


    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        Glide.with(context).load(foodList.get(position).image).into(holder.img_food_image!!)
        holder.txt_food_name!!.setText(foodList.get(position).name)
        holder.txt_food_price!!.setText(foodList.get(position).price.toString())

        //Event
        holder.setListener(object:IRecyclerItemClickListener{
            override fun onItemClick(view: View, pos: Int) {
                Common.foodSelected = foodList.get(pos)
                Common.foodSelected!!.key = pos.toString()
                EventBus.getDefault().postSticky(FoodItemClick(true,foodList.get(pos)))
            }

        })

        holder.img_cart!!.setOnClickListener {
            val cartItem = CartItem()
            cartItem.uid = Common.currentUser!!.uid
            cartItem.userPhone = Common.currentUser!!.phone

            cartItem.foodId = foodList.get(position).id!!
            cartItem.foodName = foodList.get(position).name!!
            cartItem.foodImage = foodList.get(position).image!!
            cartItem.foodPrice = foodList.get(position).price!!.toDouble()
            cartItem.foodQuantity=1
            cartItem.foodExtraPrice=0.0
            cartItem.foodAddon = "Default"
            cartItem.foodSize="Default"

            compositeDisposable.add(cartDataSource.insertOrReplaceAll(cartItem)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Toast.makeText(context, "Add to cart success", Toast.LENGTH_SHORT).show()
                    //Here we will send notify to HomeActivity to update CounterFab
                    EventBus.getDefault().postSticky(CountCartEvent(true))
                },{
                    t: Throwable? -> Toast.makeText(context, "[INSERT CART]"+t!!.message, Toast.LENGTH_SHORT).show()
                }))

        }

    }

    fun onStop(){
        if (compositeDisposable != null)
            compositeDisposable.clear();
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyFoodListAdapter.MyViewHolder {
        return MyViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_food_item,parent,false))
    }

    override fun getItemCount(): Int {
        return  foodList.size

    }



    inner class MyViewHolder (itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {

        override fun onClick(view: View?) {
            listener!!.onItemClick(view!!,adapterPosition)

        }

        var txt_food_name: TextView?=null
        var txt_food_price: TextView?=null

        var img_food_image: ImageView?=null
        var img_fav: ImageView?=null
        var img_cart: ImageView?=null

        internal var listener:IRecyclerItemClickListener?=null

        fun setListener(listener:IRecyclerItemClickListener)
        {
            this.listener=listener
        }

        init {
            txt_food_name = itemView.findViewById(R.id.txt_food_name) as TextView
            txt_food_price = itemView.findViewById(R.id.txt_food_price) as TextView
            img_food_image = itemView.findViewById(R.id.img_food_image) as ImageView
            img_fav = itemView.findViewById(R.id.img_fav) as ImageView
            img_cart = itemView.findViewById(R.id.img_quick_cart) as ImageView
            itemView.setOnClickListener(this)

        }

    }

}