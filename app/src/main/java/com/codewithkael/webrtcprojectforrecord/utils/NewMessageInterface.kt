package com.codewithkael.webrtcprojectforrecord.utils

import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import org.json.JSONObject

interface NewMessageInterface {
    fun onNewMessage(message: MessageModel)
}