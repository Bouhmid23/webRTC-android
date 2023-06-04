package com.codewithkael.webrtcprojectforrecord.models

data class MessageModel(
        /*
        val data:Any?=null,
        val success:Boolean?=null,
        val name: String? = null,
        val type: String,*/
        val type: String,
        val name: Any? = null,
        val target: String? = null,
        val success:Boolean?=null,
        val offer:Any?=null,
        val answer:Any?=null,
        val peer_name: String? = null,
        val candidate:Any?=null
)

