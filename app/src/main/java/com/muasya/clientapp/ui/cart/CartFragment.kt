package com.muasya.clientapp.ui.cart

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.Parcelable
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContentProviderCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.braintreepayments.api.dropin.DropInRequest
import com.braintreepayments.api.dropin.DropInResult
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.muasya.clientapp.Adapter.MyCartAdapter
import com.muasya.clientapp.Callback.IMyButtonCallback
import com.muasya.clientapp.Common.Common
import com.muasya.clientapp.Common.MySwipeHelper
import com.muasya.clientapp.Database.CartDataSource
import com.muasya.clientapp.Database.CartDatabase
import com.muasya.clientapp.Database.CartItem
import com.muasya.clientapp.Database.LocalCartDataSource
import com.muasya.clientapp.EventBus.CountCartEvent
import com.muasya.clientapp.EventBus.HideFABCart
import com.muasya.clientapp.EventBus.UpdateItemInCart
import com.muasya.clientapp.Model.Order
import com.muasya.clientapp.R
import com.muasya.clientapp.Remote.ICloudFunctions
import com.muasya.clientapp.Remote.RetrofitCloudClient
import com.muasya.clientapp.databinding.FragmentCartBinding
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.internal.functions.Functions
import io.reactivex.observers.DisposableObserver
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import retrofit2.create
import java.io.IOException
import java.util.*

class CartFragment : Fragment() {

    private val REQUEST_BRAINTREE_CODE: Int=8888
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

    internal var address:String=""
    internal var comment:String=""

    lateinit var cloudFunctions:ICloudFunctions

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

        cloudFunctions = RetrofitCloudClient.getInstance().create(ICloudFunctions::class.java)

        cartDataSource = LocalCartDataSource(CartDatabase.getInstance(requireContext()).cartDAO())

        recycler_cart = root.findViewById(R.id.recycler_cart) as RecyclerView
        recycler_cart!!.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(context)
        recycler_cart!!.layoutManager = layoutManager
        recycler_cart!!.addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))

        val swipe = object:MySwipeHelper(requireContext(), recycler_cart!!, 200){
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
                                        Toast.makeText(context!!, "Delete item success", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onSubscribe(d: Disposable) {

                                    }

                                    override fun onError(e: Throwable) {
                                        Toast.makeText(context!!, ""+e.message, Toast.LENGTH_SHORT).show()
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
                if (b){
                    edt_address.setText(Common.currentUser!!.address!!)
                    txt_address.visibility = View.GONE
                }
            }
            rdi_other_address.setOnCheckedChangeListener { compoundButton, b ->
                if (b){
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

                            val singleAddress = Single.just(getAddressFromLatLng(task.result!!.latitude,
                                task.result!!.longitude))

                            val disposable = singleAddress.subscribeWith(object:DisposableSingleObserver<String>(){
                                override fun onSuccess(t: String) {
                                    edt_address.setText(coordinates)
                                    txt_address.visibility = View.VISIBLE
                                    txt_address.setText(t)
                                }

                                override fun onError(e: Throwable) {
                                    edt_address.setText(coordinates)
                                    txt_address.visibility = View.VISIBLE
                                    txt_address.setText(e.message!!)
                                }

                            })
                        }
                }
            }

            builder.setView(view)
            builder.setNegativeButton("NO", {dialogInterface, _ -> dialogInterface.dismiss() })
                .setPositiveButton("YES",
                    {dialogInterface, _ ->
                        if(rdi_cod.isChecked)
                            paymentCOD(edt_address.text.toString(), edt_comment.text.toString())
                        else if(rdi_braintree.isChecked)
                        {
                            address = edt_address.text.toString()
                            comment = edt_comment.text.toString()
                            if (!TextUtils.isEmpty(Common.currentToken))
                            {
                                val dropInRequest = DropInRequest().clientToken(Common.currentToken)
                                startActivityForResult(dropInRequest.getIntent(context),REQUEST_BRAINTREE_CODE)

                            }
                        }
                    })

            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun paymentCOD(address: String, comment: String) {
        compositeDisposable.addAll(cartDataSource!!.getAllCart(Common.currentUser!!.uid!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe ({ cartItemList ->

                //When we have all cartItems, we will get total price
                cartDataSource!!.sumPrice(Common.currentUser!!.uid!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object: SingleObserver<Double>{
                        override fun onSuccess(totalPrice: Double) {
                            val finalPrice = totalPrice
                            val order = Order()
                            order.userId = Common.currentUser!!.uid!!
                            order.userName = Common.currentUser!!.name!!
                            order.userPhone = Common.currentUser!!.phone!!
                            order.shippingAddress = address
                            order.comment = comment

                            if(currentLocation != null)
                            {
                                order.lat = currentLocation!!.latitude
                                order.lng = currentLocation!!.longitude
                            }

                            order.cartItemList = cartItemList
                            order.totalPayment = totalPrice
                            order.finalPayment = finalPrice
                            order.discount = 0
                            order.isCod = true
                            order.transactionId = "Cash On Delivery"

                            //Submit to Firebase
                            writeOrderToFirebase(order)

                        }

                        override fun onSubscribe(d: Disposable) {

                        }

                        override fun onError(e: Throwable) {
                            Toast.makeText(requireContext(), ""+e.message,Toast.LENGTH_SHORT).show()
                        }

                    })

            },{ throwable -> Toast.makeText(requireContext(), ""+throwable.message,Toast.LENGTH_SHORT).show() }))
    }

    private fun writeOrderToFirebase(order: Order) {
        FirebaseDatabase.getInstance()
            .getReference(Common.ORDER_REF)
            .child(Common.createOrderNumber())
            .setValue(order)
            .addOnFailureListener { e-> Toast.makeText(requireContext(), ""+e.message,Toast.LENGTH_SHORT).show() }
            .addOnCompleteListener { task ->
                //Clean cart
                if(task.isSuccessful)
                {
                    cartDataSource!!.cleanCart(Common.currentUser!!.uid!!)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object:SingleObserver<Int>{
                            override fun onSuccess(t: Int) {
                                Toast.makeText(requireContext(), "Order placed successfully",Toast.LENGTH_SHORT).show()
                            }

                            override fun onSubscribe(d: Disposable) {

                            }

                            override fun onError(e: Throwable) {
                                Toast.makeText(requireContext(), ""+e.message,Toast.LENGTH_SHORT).show()
                            }
                        })
                }
            }
    }

    private fun getAddressFromLatLng(latitude: Double, longitude: Double): String {

        val geoCoder = Geocoder(requireContext(), Locale.getDefault())
        var result: String? = null
        try {
            val addressList = geoCoder.getFromLocation(latitude, longitude, 1)
            if (addressList != null && addressList.size > 0) {
                val address = addressList[0]
                val sb = StringBuilder(address.getAddressLine(0))
                result = sb.toString()
            } else
                result = "Address not found!"
            return result
        } catch (e: IOException) {
            return e.message!!
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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_BRAINTREE_CODE)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                val result = data!!.getParcelableExtra<DropInResult>(DropInResult.EXTRA_DROP_IN_RESULT)
                val nonce = result!!.paymentMethodNonce

                //calculate sum cart
                cartDataSource!!.sumPrice(Common.currentUser!!.uid!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object:SingleObserver<Double>{
                        override fun onSuccess(totalPrice: Double) {
                            //Get all item to create Cart
                            compositeDisposable.add(
                                cartDataSource!!.getAllCart(Common.currentUser!!.uid!!)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({cartItems: List<CartItem>? ->

                                        //After have all cart item, we will submit payment
                                        compositeDisposable.add(cloudFunctions.submitPayment(totalPrice,
                                        nonce!!.nonce)
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({ braintreeTransaction ->

                                                if (braintreeTransaction.success)
                                                {
                                                    //Create Order
                                                    val finalPrice = totalPrice
                                                    val order = Order()
                                                    order.userId = Common.currentUser!!.uid!!
                                                    order.userName = Common.currentUser!!.name!!
                                                    order.userPhone = Common.currentUser!!.phone!!
                                                    order.shippingAddress = address
                                                    order.comment = comment

                                                    if(currentLocation != null)
                                                    {
                                                        order.lat = currentLocation!!.latitude
                                                        order.lng = currentLocation!!.longitude
                                                    }

                                                    order.cartItemList = cartItems
                                                    order.totalPayment = totalPrice
                                                    order.finalPayment = finalPrice
                                                    order.discount = 0
                                                    order.isCod = false
                                                    order.transactionId = braintreeTransaction.transaction!!.id

                                                    //Submit to Firebase
                                                    writeOrderToFirebase(order)

                                                }

                                            },
                                                {t: Throwable? ->
                                                    Toast.makeText(context,""+t!!.message,Toast.LENGTH_SHORT).show()
                                                })
                                        )
                                    },
                                        {t: Throwable? ->

                                            Toast.makeText(context,""+t!!.message,Toast.LENGTH_SHORT).show()

                                        })
                            )
                        }

                        override fun onSubscribe(d: Disposable) {
                            TODO("Not yet implemented")
                        }

                        override fun onError(e: Throwable) {
                            Toast.makeText(context,""+e.message,Toast.LENGTH_SHORT).show()
                        }

                    })
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
  }




