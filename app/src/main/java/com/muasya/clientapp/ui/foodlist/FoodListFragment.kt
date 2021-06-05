package com.muasya.clientapp.ui.foodlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.muasya.clientapp.Adapter.MyFoodListAdapter
import com.muasya.clientapp.Common.Common
import com.muasya.clientapp.R
import com.muasya.clientapp.databinding.FragmentFoodListBinding

class FoodListFragment : Fragment() {

    private lateinit var foodListViewModel: FoodListViewModel
    private var _binding: FragmentFoodListBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    var recycler_food_list : RecyclerView?=null
    var layoutAnimationController:LayoutAnimationController?=null

    var adapter : MyFoodListAdapter?=null


    override fun onStop() {
        if (adapter!= null)
        adapter!!.onStop()
        super.onStop()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        foodListViewModel =
            ViewModelProvider(this).get(FoodListViewModel::class.java)

        _binding = FragmentFoodListBinding.inflate(inflater, container, false)
        val root: View = binding.root
//        val textView: TextView = binding.textSlideshow


        initViews(root)
        foodListViewModel.getMutableFoodModelListData().observe(viewLifecycleOwner, Observer {
            adapter = MyFoodListAdapter(requireContext(),it)
            recycler_food_list!!.adapter = adapter
            recycler_food_list!!.layoutAnimation = layoutAnimationController
        })
        return root
    }

    private fun initViews(root: View) {
        recycler_food_list = root!!.findViewById(R.id.recycler_food_list) as RecyclerView
        recycler_food_list!!.setHasFixedSize(true)
        recycler_food_list!!.layoutManager = LinearLayoutManager(context)

        layoutAnimationController = AnimationUtils.loadLayoutAnimation(context,R.anim.layout_item_from_left)

        (activity as AppCompatActivity).supportActionBar!!.title = Common.categorySelected!!.name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}