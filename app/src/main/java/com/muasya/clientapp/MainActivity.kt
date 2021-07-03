package com.muasya.clientapp

import android.app.Activity
import android.content.Intent
import android.database.DatabaseErrorHandler
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.database.FirebaseDatabase.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.muasya.clientapp.Common.Common
import com.muasya.clientapp.Model.UserModel
import com.muasya.clientapp.Remote.ICloudFunctions
import com.muasya.clientapp.Remote.RetrofitCloudClient
import com.muasya.clientapp.Remote.RetrofitCloudClient.getInstance
import dmax.dialog.SpotsDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.internal.functions.Functions
import io.reactivex.schedulers.Schedulers
import retrofit2.create
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth:FirebaseAuth
    private lateinit var listener:FirebaseAuth.AuthStateListener
    private lateinit var dialog: android.app.AlertDialog
    private  var compositeDisposable = CompositeDisposable()
    private lateinit var cloudFunctions: ICloudFunctions

    private lateinit  var userRef: DatabaseReference
    private var providers:List<AuthUI.IdpConfig>? = null

    companion object{
        private  val APP_REQUEST_CODE =7171
    }

    override fun onStart() {
        super.onStart()
        firebaseAuth.addAuthStateListener(listener)
    }
    override fun onStop() {
        if(listener != null)
            firebaseAuth.removeAuthStateListener(listener)
        compositeDisposable.clear()
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        init()
    }
    private fun init() {
        providers = Arrays.asList<AuthUI.IdpConfig>(AuthUI.IdpConfig.PhoneBuilder().build())
        userRef = FirebaseDatabase.getInstance().getReference(Common.USER_REFERENCE)
        firebaseAuth = FirebaseAuth.getInstance()
        dialog = SpotsDialog.Builder().setContext(this).setCancelable(false).build()
        cloudFunctions = getInstance().create(ICloudFunctions::class.java)
        listener = FirebaseAuth.AuthStateListener { firebaseAuth ->

            Dexter.withActivity(this@MainActivity)
                .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(object:PermissionListener{
                    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                        val user = firebaseAuth.currentUser
                        if(user != null)
                        {
                            checkUserFromFirebase(user!!)
                        }
                        else
                        {
                            phoneLogin()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permission: PermissionRequest?,
                        token: PermissionToken?
                    ) {
                        //enter some
                    }

                    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                        Toast.makeText(this@MainActivity, "You must accept this permission to use app",
                            Toast.LENGTH_SHORT).show()
                    }


                }).check()
        }
    }
    private fun checkUserFromFirebase(user: FirebaseUser) {
        dialog!!.show()
        userRef!!.child(user!!.uid)
                .addListenerForSingleValueEvent(object:ValueEventListener{
                    override fun onCancelled(p0: DatabaseError) {
                        Toast.makeText(this@MainActivity,""+p0.message,Toast.LENGTH_SHORT).show()
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        if(p0.exists()) {
                            compositeDisposable.add(cloudFunctions!!.getToken()
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe ({ braintreeToken ->


                                    dialog!!.dismiss()
                                    val userModel = p0.getValue(UserModel::class.java)
                                    goToHomeActivity(userModel,braintreeToken.token)

                                }, { throwable ->
                                    dialog!!.dismiss()
                                    Toast.makeText(this@MainActivity,""+throwable.message,Toast.LENGTH_SHORT).show()
                                }));
                        } else {
                            dialog!!.dismiss()
                            showRegisterDialog(user!!)
                        }
                    }
                })
    }

    private fun showRegisterDialog(user:FirebaseUser) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("REGISTER")
        builder.setMessage("Please fill information")

        val itemView = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.layout_register, null)

        val edt_name = itemView.findViewById<EditText>(R.id.edt_name)
        val edt_address = itemView.findViewById<EditText>(R.id.edt_address)
        val edt_phone = itemView.findViewById<EditText>(R.id.edt_phone)

        //Set
        edt_phone.setText(user!!.phoneNumber)

        builder.setView(itemView)
        builder.setNegativeButton("CANCEL"){dialogInterface, i -> dialogInterface.dismiss() }
        builder.setPositiveButton("REGISTER") {dialogInterface, i ->
            if(TextUtils.isDigitsOnly(edt_name.text.toString()))
            {
                Toast.makeText(this@MainActivity,"Please enter your name",Toast.LENGTH_SHORT)
                    .show()
                return@setPositiveButton
            }else if(TextUtils.isDigitsOnly(edt_address.text.toString())) {
                Toast.makeText(this@MainActivity,"Please enter your address",Toast.LENGTH_SHORT)
                    .show()
                return@setPositiveButton
            }


        val userModel = UserModel()
        userModel.uid = user!!.uid
        userModel.name = edt_name.text.toString()
        userModel.address = edt_address.text.toString()
        userModel.phone = edt_phone.text.toString()

        userRef!!.child(user!!.uid)
                .setValue(userModel)
                .addOnCompleteListener{task ->
                    if (task.isSuccessful){
                        compositeDisposable.add(cloudFunctions.getToken()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ braintreeToken ->

                                dialogInterface.dismiss()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Congratulation ! Register success!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                goToHomeActivity(userModel,braintreeToken.token)

                            },{t: Throwable? ->

                                dialogInterface.dismiss()
                                Toast.makeText(
                                    this@MainActivity,
                                    ""+t!!.message,
                                    Toast.LENGTH_SHORT
                                ).show()

                            }))
                    }
                }
        }

        //Important ! Please show dialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun goToHomeActivity(userModel: UserModel?,token:String?) {
        Common.currentUser = userModel!!
        Common.currentToken = token!!
        startActivity(Intent (this@MainActivity, HomeActivity::class.java))
        finish()

    }

    private fun phoneLogin() {
        startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder().setAvailableProviders(providers!!).build(), APP_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == APP_REQUEST_CODE)
        {
            val response = IdpResponse.fromResultIntent(data)
            if(resultCode == Activity.RESULT_OK)
            {
                val user = FirebaseAuth.getInstance().currentUser
            }
            else
            {
                Toast.makeText(this, "Failed to Sign in",Toast.LENGTH_SHORT).show()
            }
        }
    }

}