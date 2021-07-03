package com.muasya.clientapp.Remote

import com.muasya.clientapp.Model.BraintreeToken
import com.muasya.clientapp.Model.BraintreeTransaction
import io.reactivex.Observable
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface ICloudFunctions {
    @GET("token")
    fun getToken(): Observable<BraintreeToken>

    @POST("checkout")
    @FormUrlEncoded
    fun submitPayment(@Field("amount") amount:Double,
                      @Field("payment_method_nonce") nonce:String): Observable<BraintreeTransaction>
}