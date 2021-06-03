package com.muasya.clientapp.Callback

import com.muasya.clientapp.Model.CategoryModel
import com.muasya.clientapp.Model.CommentModel


interface ICommentCallBack {
    fun onCommentLoadSuccess(commentList: List<CommentModel>)
    fun onCommentLoadFailed(message:String)
}