package com.muasya.clientapp.ui.fooddetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.andremion.counterfab.CounterFab
import com.bumptech.glide.Glide
import com.cepheuen.elegantnumberbutton.view.ElegantNumberButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import com.muasya.clientapp.Common.Common
import com.muasya.clientapp.Model.CommentModel
import com.muasya.clientapp.Model.FoodModel
import com.muasya.clientapp.R
import com.muasya.clientapp.databinding.FragmentFoodDetailBinding
import dmax.dialog.SpotsDialog

class FoodDetailFragment : Fragment() {

    private lateinit var foodDetailViewModel: FoodDetailViewModel
    private var _binding: FragmentFoodDetailBinding? = null

    private var img_food:ImageView?=null
    private var btnCart:CounterFab?=null
    private var btnRating:FloatingActionButton?=null
    private var food_name:TextView?=null
    private var food_description:TextView?=null
    private var food_price:TextView?=null
    private var number_button:ElegantNumberButton?=null
    private var ratingBar:RatingBar?=null
    private var btnShowComment:Button?=null


    private var waitingDialog:android.app.AlertDialog?=null


    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        foodDetailViewModel =
            ViewModelProvider(this).get(FoodDetailViewModel::class.java)

        _binding = FragmentFoodDetailBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initViews(root)

        foodDetailViewModel.getMutableLiveDataFood().observe(viewLifecycleOwner, Observer {
            displayInfo(it)
        })

        foodDetailViewModel.getMutableLiveDataComment().observe(viewLifecycleOwner, Observer {
            submitRatingToFirebase(it)
        })

        return root
    }

    private fun submitRatingToFirebase(commentModel: CommentModel?) {
        waitingDialog!!.show()

        //First, we will submit to Comment Ref
        FirebaseDatabase.getInstance()
            .getReference(Common.COMMENT_REF)
            .child(Common.foodSelected!!.id!!)
            .push()
            .setValue(commentModel)
            .addOnCompleteListener {task ->
                if (task.isSuccessful)
                {
                    addRatingToFood(commentModel!!.ratingValue.toDouble())
                }
                waitingDialog!!.dismiss()
            }

    }

    private fun addRatingToFood(ratingValue: Double) {
        FirebaseDatabase.getInstance()
            .getReference(Common.CATEGORY_REF) //Select category
            .child(Common.categorySelected!!.menu_id!!) //Select menu in category
            .child("foods") // Select foods array
            .child(Common.foodSelected!!.key!!) //Select key
            .addListenerForSingleValueEvent(object:ValueEventListener{
                override fun onCancelled(p0: DatabaseError) {
                    waitingDialog!!.dismiss()
                    Toast.makeText(context!!,""+p0.message,Toast.LENGTH_SHORT).show()
                }

                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists())
                    {
                        val foodModel = dataSnapshot.getValue(FoodModel::class.java)
                        foodModel!!.key = Common.foodSelected!!.key
                        //Apply rating
                        val sumRating = foodModel.ratingValue!!.toDouble() + (ratingValue)
                        val ratingCount = foodModel.ratingCount+1
                        val result = sumRating/ratingCount

                        val updateData = HashMap<String,Any>()
                        updateData["ratingValue"] = result
                        updateData["ratingCount"] = ratingCount

                        //Update data in variable
                        foodModel.ratingCount = ratingCount
                        foodModel.ratingValue = result

                        dataSnapshot.ref
                            .updateChildren(updateData)
                            .addOnCompleteListener{ task ->
                                waitingDialog!!.dismiss()
                                if (task.isSuccessful)
                                {
                                    Common.foodSelected = foodModel
                                    foodDetailViewModel!!.setFoodModel(foodModel)
                                    Toast.makeText(context!!,"Thank you",Toast.LENGTH_SHORT).show()
                                }
                            }

                    }

                }

            })
    }

    private fun displayInfo(it: FoodModel?) {
        Glide.with(requireContext()).load(it!!.image).into(img_food!!)
        food_name!!.text= StringBuilder(it!!.name!!)
        food_description!!.text= StringBuilder(it!!.description!!)
        food_price!!.text= StringBuilder(it!!.price!!.toString())

        ratingBar!!.rating = it!!.ratingValue.toFloat()

    }

    private fun initViews(root: View?) {

        waitingDialog = SpotsDialog.Builder().setContext(requireContext()).setCancelable(false).build()

        btnCart = root!!.findViewById(R.id.btnCart) as CounterFab
        img_food = root!!.findViewById(R.id.img_food) as ImageView
        btnRating = root!!.findViewById(R.id.btn_rating) as FloatingActionButton
        food_name = root!!.findViewById(R.id.food_name) as TextView
        food_description = root!!.findViewById(R.id.food_description) as TextView
        food_price = root!!.findViewById(R.id.food_price) as TextView
        number_button = root!!.findViewById(R.id.number_button) as ElegantNumberButton
        ratingBar = root!!.findViewById(R.id.ratingBar) as RatingBar
        btnShowComment = root!!.findViewById(R.id.btnShowComment) as Button

        //Event
        btnRating!!.setOnClickListener{
            showDialogRating()
        }

    }

    private fun showDialogRating() {
        var builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Rating Food")
        builder.setMessage("Please fill information")

        val itemView = LayoutInflater.from(context).inflate(R.layout.layout_rating_comment, null)

        val ratingBar = itemView.findViewById<RatingBar>(R.id.rating_bar)
        val edt_comment = itemView.findViewById<EditText>(R.id.edt_comment)

        builder.setView(itemView)

        builder.setNegativeButton("CANCEL"){ dialogInterface, i -> dialogInterface.dismiss()}

        builder.setPositiveButton("OK"){ dialogInterface, i->
            val commentModel = CommentModel()
            commentModel.name = Common.currentUser!!.name
            commentModel.uid = Common.currentUser!!.uid
            commentModel.comment = edt_comment.text.toString()
            commentModel.ratingValue = ratingBar.rating
            val serverTimeStamp = HashMap<String, Any>()
            serverTimeStamp["timeStamp"] = ServerValue.TIMESTAMP
            commentModel.commentTimestamp = (serverTimeStamp)

            foodDetailViewModel!!.setCommentModel(commentModel)

        }

        val dialog = builder.create()
        dialog.show()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}