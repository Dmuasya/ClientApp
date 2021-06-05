package com.muasya.clientapp

import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.muasya.clientapp.Common.Common
import com.muasya.clientapp.Database.CartDataSource
import com.muasya.clientapp.Database.CartDatabase
import com.muasya.clientapp.Database.LocalCartDataSource
import com.muasya.clientapp.EventBus.CategoryClick
import com.muasya.clientapp.EventBus.CountCartEvent
import com.muasya.clientapp.EventBus.FoodItemClick
import com.muasya.clientapp.databinding.ActivityHomeBinding
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class HomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var cartDataSource: CartDataSource


    override fun onResume() {
        super.onResume()
        countCartItem()
    }

    private lateinit var binding: ActivityHomeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cartDataSource = LocalCartDataSource(CartDatabase.getInstance(this).cartDAO())


        setSupportActionBar(binding.appBarHome.toolbar)
        binding.appBarHome.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_home)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_menu, R.id.nav_food_detail
                //R.id.nav_food_list
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        countCartItem()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_home)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true,threadMode =  ThreadMode.MAIN)
    fun onCategorySelected(event: CategoryClick)
    {
        if (event.isSuccess)
        {
            findNavController(R.id.nav_host_fragment_content_home).navigate(R.id.nav_food_list)
        }
    }

    @Subscribe(sticky = true,threadMode =  ThreadMode.MAIN)
    fun onFoodSelected(event: FoodItemClick)
    {
        if (event.isSuccess)
        {
            findNavController(R.id.nav_host_fragment_content_home).navigate(R.id.nav_food_detail)
        }
    }

    @Subscribe(sticky = true,threadMode =  ThreadMode.MAIN)
    fun onCountCartEvent(event: CountCartEvent)
    {
        if (event.isSuccess)
        {
            countCartItem()
        }
    }

    private fun countCartItem() {
        cartDataSource.countItemInCart(Common.currentUser!!.uid!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Int> {
                override fun onSuccess(t: Int) {
                    binding.appBarHome.fab.count = t
                }

                override fun onSubscribe(d: Disposable) {

                }

                override fun onError(e: Throwable) {
                    Toast.makeText(this@HomeActivity, "[COUNT CART]"+e.message,Toast.LENGTH_SHORT).show()
                }

            })
    }

}