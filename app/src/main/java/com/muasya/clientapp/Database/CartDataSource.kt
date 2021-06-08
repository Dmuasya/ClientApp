package com.muasya.clientapp.Database

import androidx.room.*
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single

interface CartDataSource {

    fun getAllCart(uid: String): Flowable<List<CartItem>>

    fun countItemInCart(uid: String): Single<Int>

    fun sumPrice(uid: String): Single<Double>

    fun getItemInCart(foodId: String, uid: String): Single<CartItem>

    fun insertOrReplaceAll(vararg cartItems: CartItem): Completable

    fun updateCart(cart: CartItem): Single<Int>

    fun deleteCart(cart: CartItem): Single<Int>

    fun cleanCart(uid: String): Single<Int>

    fun getItemWithAllOptionsInCart(uid:String,foodId:String,foodSize:String,foodAddon:String): Single<CartItem>

}