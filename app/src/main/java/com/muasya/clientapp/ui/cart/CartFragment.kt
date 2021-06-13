package com.muasya.clientapp.ui.cart

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.Parcelable
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.muasya.clientapp.Adapter.MyCartAdapter
import com.muasya.clientapp.Callback.IMyButtonCallback
import com.muasya.clientapp.Common.Common
import com.muasya.clientapp.Common.MySwipeHelper
import com.muasya.clientapp.Database.CartDataSource
import com.muasya.clientapp.Database.CartDatabase
import com.muasya.clientapp.Database.LocalCartDataSource
import com.muasya.clientapp.EventBus.CountCartEvent
import com.muasya.clientapp.EventBus.HideFABCart
import com.muasya.clientapp.EventBus.UpdateItemInCart
import com.muasya.clientapp.R
import com.muasya.clientapp.databinding.FragmentCartBinding
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class CartFragment : Fragment() {

    private var cartDataSource:CartDataSource?=null
    private var compositeDisposable:CompositeDisposable = CompositeDisposable()
    private var recyclerViewState: Parcelable?=null
    private lateinit var cartViewModel: CartViewModel
    private var _binding: FragmentCartBinding? = null
    private lateinit var btn_place_order:Button


    var txt_empty_cart:TextView?=null
    var txt_total_price:TextView?=null
    var group_place_holder:CardView?=null
    var recycler_cart:RecyclerView?=null
    var adapter:MyCartAdapter?=null

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var currentLocation: Location

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        calculateTotalPrice()
        if (fusedLocationProviderClient != null)
            fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback,
            Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        EventBus.getDefault().postSticky(HideFABCart(true))

        cartViewModel =
            ViewModelProvider(this).get(CartViewModel::class.java)
        //After create cartViewModel, init data source
        cartViewModel.initCartDataSource(requireContext())

        _binding = FragmentCartBinding.inflate(inflater, container, false)
        val root: View = binding.root
        initViews(root)
        initLocation()
        cartViewModel.getMutableLiveDataCartItem().observe(viewLifecycleOwner, Observer {
            if (it == null || it.isEmpty())
            {
                recycler_cart!!.visibility = View.GONE
                group_place_holder!!.visibility = View.GONE
                txt_empty_cart!!.visibility = View.VISIBLE

            }
            else
            {
                recycler_cart!!.visibility = View.VISIBLE
                group_place_holder!!.visibility = View.VISIBLE
                txt_empty_cart!!.visibility = View.GONE

                adapter = MyCartAdapter(requireContext(),it)
                recycler_cart!!.adapter = adapter
            }
        } )
        return root
    }

    @SuppressLint("MissingPermission")
    private fun initLocation() {
        buildLocationRequest()
        buildLocationCallback()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationProviderClient!!.requestLocationUpdates(locationRequest,locationCallback,
            Looper.getMainLooper())
    }

    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                currentLocation = p0!!.lastLocation
            }
        }
    }

    private fun buildLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setInterval(5000)
        locationRequest.setFastestInterval(3000)
        locationRequest.setSmallestDisplacement(10f)
    }

    @SuppressLint("MissingPermission")
    private fun initViews(root:View) {

        setHasOptionsMenu(true) //Important, menu will neve inflate if not added

        cartDataSource = LocalCartDataSource(CartDatabase.getInstance(requireContext()).cartDAO())

        recycler_cart = root.findViewById(R.id.recycler_cart) as RecyclerView
        recycler_cart!!.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(context)
        recycler_cart!!.layoutManager = layoutManager
        recycler_cart!!.addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))

        val swipe = object:MySwipeHelper(requireContext(), recycler_cart!!, 200)
        {
            override fun instantiateMyButton(
                viewHolder: RecyclerView.ViewHolder,
                buffer: MutableList<MyButton>
            ) {
                buffer.add(MyButton(context!!,
                "Delete",
                30,
                0,
                Color.parseColor("#FF3C30"),
                    object : IMyButtonCallback{
                        override fun onClick(pos: Int) {
                            val deleteItem = adapter!!.getItemAtPosition(pos)
                            cartDataSource!!.deleteCart(deleteItem)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(object:SingleObserver<Int>{
                                    override fun onSuccess(t: Int) {
                                        adapter!!.notifyItemRemoved(pos)
                                        sumCart()
                                        EventBus.getDefault().postSticky(CountCartEvent(true))
                                        Toast.makeText(context, "Delete item success", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onSubscribe(d: Disposable) {

                                    }

                                    override fun onError(e: Throwable) {
                                        Toast.makeText(context, ""+e.message, Toast.LENGTH_SHORT).show()
                                    }

                                })
                        }
                    }
                ))
            }

        }

        txt_empty_cart = root.findViewById(R.id.txt_empty_cart) as TextView
        txt_total_price = root.findViewById(R.id.txt_total_price) as TextView
        group_place_holder = root.findViewById(R.id.group_place_holder) as CardView

        btn_place_order = root.findViewById(R.id.btn_place_order) as Button

        //Event
        btn_place_order!!.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("One more step!")

            val view = LayoutInflater.from(context).inflate(R.layout.layout_place_order, null)

            val edt_address = view.findViewById<View>(R.id.edt_address) as EditText
            val edt_comment = view.findViewById<View>(R.id.edt_comment) as EditText
            val txt_address = view.findViewById<View>(R.id.txt_address_detail) as TextView
            val rdi_home = view.findViewById<View>(R.id.rdi_home_address) as RadioButton
            val rdi_other_address = view.findViewById<View>(R.id.rdi_other_address) as RadioButton
            val rdi_ship_to_this_address = view.findViewById<View>(R.id.rdi_ship_this_address) as RadioButton
            val rdi_cod = view.findViewById<View>(R.id.rdi_cod) as RadioButton
            val rdi_braintree = view.findViewById<View>(R.id.rdi_braintree) as RadioButton

            //Data
            edt_address.setText(Common.currentUser!!.address!!) //By default we checked rdi_home, so we'll display user address

            //Event
            rdi_home.setOnCheckedChangeListener { compoundButton, b ->
                if (b)
                {
                    edt_address.setText(Common.currentUser!!.address!!)
                    txt_address.visibility = View.GONE

                }
            }
            rdi_other_address.setOnCheckedChangeListener { compoundButton, b ->
                if (b)
                {
                    edt_address.setText("")
                    edt_address.setHint("Enter your address")
                    txt_address.visibility = View.GONE
                }
            }
            rdi_ship_to_this_address.setOnCheckedChangeListener { compoundButton, b ->
                if (b)
                {
                    fusedLocationProviderClient!!.lastLocation
                        .addOnFailureListener { e->
                            txt_address.visibility = View.GONE
                            Toast.makeText(requireContext(),""+e.message,Toast.LENGTH_SHORT).show()
                        }
                        .addOnCompleteListener {
                            task ->
                            val coordinates = StringBuilder()
                                .append(task.result!!.latitude)
                                .append("/")
                                .append(task.result!!.longitude)
                                .toString()

                            edt_address.setText(coordinates)
                            txt_address.visibility = View.VISIBLE
                            txt_address.setText("Implement late with Google API")
                        }
                }
            }

            builder.setView(view)
            builder.setNegativeButton("NO", {dialogInterface, _ -> dialogInterface.dismiss() })
                .setPositiveButton("YES",{dialogInterface, _ -> Toast.makeText(requireContext(), "Implement late",Toast.LENGTH_SHORT).show()})

            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun sumCart() {
        cartDataSource!!.sumPrice(Common.currentUser!!.uid!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object:SingleObserver<Double>{
                override fun onSuccess(t: Double) {
                    txt_total_price!!.text = StringBuilder("Total: Ksh")
                        .append(t)
                }

                override fun onSubscribe(d: Disposable) {

                }

                override fun onError(e: Throwable) {
                    if (!e.message!!.contains("Query returned empty"))
                        Toast.makeText(context, ""+e.message, Toast.LENGTH_SHORT).show()

                }

            });
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    override fun onStop() {
        cartViewModel!!.onStop()
        compositeDisposable.clear()
        EventBus.getDefault().postSticky(HideFABCart(false))
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        if(fusedLocationProviderClient != null)
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onStop()
    }



    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateItemInCart(event:UpdateItemInCart){
        if (event.cartItem != null)
        {
            recyclerViewState = recycler_cart!!.layoutManager!!.onSaveInstanceState()
            cartDataSource!!.updateCart(event.cartItem)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object:SingleObserver<Int>{

                    override fun onSuccess(t: Int) {
                        calculateTotalPrice();
                        recycler_cart!!.layoutManager!!.onRestoreInstanceState(recyclerViewState)
                    }

                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onError(e: Throwable) {
                        Toast.makeText(context, "[UPDATE CART]"+e.message, Toast.LENGTH_SHORT).show()
                    }

                })
        }
    }

    private fun calculateTotalPrice() {
        cartDataSource!!.sumPrice(Common.currentUser!!.uid!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object:SingleObserver<Double>{
                override fun onSuccess(price: Double) {
                    txt_total_price!!.text = StringBuilder("Total: Ksh")
                        .append(Common.formatPrice(price))
                }

                override fun onSubscribe(d: Disposable) {
                }

                override fun onError(e: Throwable) {
                    if (!e.message!!.contains("Query returned empty"))
                        Toast.makeText(context, "[SUM CART]"+e.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu!!.findItem(R.id.action_settings).isVisible = false //Hide settings menu while in cart
        super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater!!.inflate(R.menu.cart_menu,menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item!!.itemId == R.id.action_clear_cart)
        {
            cartDataSource!!.cleanCart(Common.currentUser!!.uid!!)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object:SingleObserver<Int>{
                    override fun onSuccess(t: Int) {
                        Toast.makeText(context, "Clear Cart Success",Toast.LENGTH_SHORT)
                        EventBus.getDefault().postSticky(CountCartEvent(true))

                    }
                    override fun onSubscribe(d: Disposable) {

                    }
                    override fun onError(e: Throwable) {
                        Toast.makeText(context, ""+e.message,Toast.LENGTH_SHORT)
                    }

                })
            return  true
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}