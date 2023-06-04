package com.codewithkael.webrtcprojectforrecord.models

data class IceCandidateModel(
    val sdpMid:String,
    val sdpMLineIndex:Double,
    val candidate:String
)

