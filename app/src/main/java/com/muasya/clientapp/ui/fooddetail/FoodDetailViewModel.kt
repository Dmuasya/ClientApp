package com.muasya.clientapp.ui.fooddetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.common.internal.service.Common
import com.muasya.clientapp.Common.Common.foodSelected
import com.muasya.clientapp.Model.FoodModel

class FoodDetailViewModel : ViewModel() {

    private var mutableLiveDataFood:MutableLiveData<FoodModel>?=null

    fun getMutableLiveDataFood():MutableLiveData<FoodModel>{
        if (mutableLiveDataFood == null)
            mutableLiveDataFood = MutableLiveData()
        mutableLiveDataFood!!.value = com.muasya.clientapp.Common.Common.foodSelected
        return mutableLiveDataFood!!
    }

}