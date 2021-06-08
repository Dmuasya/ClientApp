package com.muasya.clientapp.ui.cart

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.muasya.clientapp.Model.CommentModel

class CommentViewModel : ViewModel() {

    val mutableLiveDataCommentList:MutableLiveData<List<CommentModel>>

    init {
        mutableLiveDataCommentList = MutableLiveData()
    }

    fun setCommentList(commentList: List<CommentModel>)
    {
        mutableLiveDataCommentList.value = commentList
    }
}