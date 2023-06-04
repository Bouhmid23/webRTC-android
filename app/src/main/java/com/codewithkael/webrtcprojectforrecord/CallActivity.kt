package com.codewithkael.webrtcprojectforrecord

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.codewithkael.webrtcprojectforrecord.databinding.ActivityCallBinding
import com.codewithkael.webrtcprojectforrecord.models.IceCandidateModel
import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import com.codewithkael.webrtcprojectforrecord.utils.NewMessageInterface
import com.codewithkael.webrtcprojectforrecord.utils.PeerConnectionObserver
import com.codewithkael.webrtcprojectforrecord.utils.RTCAudioManager
import com.google.gson.Gson
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

class CallActivity : AppCompatActivity(), NewMessageInterface {


    lateinit var binding : ActivityCallBinding
    private var userName:String?=null
    private var socketRepository:SocketRepository?=null
    private var rtcClient : RTCClient?=null
    private val TAG = "CallActivity"
    private var target:String = ""
    private val gson = Gson()
    private var isMute = false
    private var isCameraPause = false
    private val rtcAudioManager by lazy { RTCAudioManager.create(this) }
    private var isSpeakerMode = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init(){
        try {
            userName = intent.getStringExtra("username")
            socketRepository = SocketRepository(this)
            userName?.let { socketRepository?.initSocket(it) }
            rtcClient = RTCClient(application,userName!!,socketRepository!!, object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    rtcClient?.addIceCandidate(p0)
                    val candidate = hashMapOf(
                        "sdpMid" to p0?.sdpMid,
                        "sdpMLineIndex" to p0?.sdpMLineIndex,
                        "candidate" to p0?.sdp)

                    socketRepository?.sendMessageToSocket(
                        MessageModel(
                            //"ice_candidate",userName,target,candidate
                        "candidate",target,null,null,null,null,null,candidate))

                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    p0?.videoTracks?.get(0)?.addSink(binding.remoteView)
                    Log.d(TAG, "onAddStream: $p0")

                }
            })
            rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

            binding.apply {
                callBtn.setOnClickListener {
                    target = targetUserNameEt.text.toString()
                    socketRepository?.sendMessageToSocket(MessageModel(
                       //"start_call",userName,target,null
                    "want_to_call",target))
                }

                switchCameraButton.setOnClickListener {
                    rtcClient?.switchCamera()
                }

                micButton.setOnClickListener {
                    if (isMute){
                        isMute = false
                        micButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
                    }else{
                        isMute = true
                        micButton.setImageResource(R.drawable.ic_baseline_mic_24)
                    }
                    rtcClient?.toggleAudio(isMute)
                }

                videoButton.setOnClickListener {
                    if (isCameraPause){
                        isCameraPause = false
                        videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
                    }else{
                        isCameraPause = true
                        videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
                    }
                    rtcClient?.toggleCamera(isCameraPause)
                }

                audioOutputButton.setOnClickListener {
                    if (isSpeakerMode){
                        isSpeakerMode = false
                        audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
                        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
                    }else{
                        isSpeakerMode = true
                        audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
                    }

                }
                endCallButton.setOnClickListener {
                    setCallLayoutGone()
                    setWhoToCallLayoutVisible()
                    setIncomingCallLayoutGone()
                    rtcClient?.endCall()
                    socketRepository?.sendMessageToSocket(
                        MessageModel(
                            "leave",userName,null,null))
                }
            }

        }catch (e:Exception){
            e.printStackTrace()
        }
        }


    @SuppressLint("SetTextI18n")
    override fun onNewMessage(message: MessageModel) {

        val jsonString = gson.toJson(message)
        val jsonObject = JSONObject(jsonString)
        Log.d(TAG, "onNewMessage: $jsonObject")
        when(message.type){
            "server_login"->{
                Log.d(TAG, "server_login ")
            }
            "server_user_list"->{
                println(message.name)
            }
            "server_already_in_room"/*call_response*/->{
                if (message.success == true){
                    //user is not reachable
                    runOnUiThread {
                        Toast.makeText(this,"user is not reachable",
                            Toast.LENGTH_LONG).show()
                    }}
                else{
                    runOnUiThread {
                        setWhoToCallLayoutGone()
                        setCallLayoutVisible()
                        binding.apply {
                            rtcClient?.call(targetUserNameEt.text.toString())
                            rtcClient?.initializeSurfaceView(localView)
                            rtcClient?.initializeSurfaceView(remoteView)
                            rtcClient?.startLocalVideo(localView)
                        }
                    }
                }
            }
            /*"answer_received"*/"server_answer" ->{
                val answer = jsonObject.getString("answer")
                val sdp = JSONObject(answer).getString("sdp")
                Log.d(TAG,"answer received : $answer")
                val session = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    /*message.data*/sdp)
                rtcClient?.onRemoteSessionReceived(userName,session)
                binding.remoteViewLoading.visibility = View.GONE

            }
            /*"offer_received"*/"server_offer" -> {
                Log.d(TAG, "server_offer ")
                runOnUiThread {
                    Log.d(TAG,"run on UI thread")
                    setIncomingCallLayoutVisible()
                    binding.incomingNameTV.text = "${message.name.toString()} is calling you"
                    binding.acceptButton.setOnClickListener {
                        Log.d(TAG,"block accept button")
                        setIncomingCallLayoutGone()
                        setCallLayoutVisible()
                        setWhoToCallLayoutGone()
                        val offer = jsonObject.getString("offer")
                        val sdp = JSONObject(offer).getString("sdp")
                        val session = SessionDescription(
                            SessionDescription.Type.OFFER,
                            /*message.data*/sdp)
                        Log.d(TAG,"remote session received : $sdp")
                        rtcClient?.onRemoteSessionReceived(userName,session)
                        rtcClient?.answer(message.name.toString())
                        target = message.name.toString()
                        binding.apply {
                                rtcClient?.initializeSurfaceView(localView)
                                rtcClient?.initializeSurfaceView(remoteView)
                                rtcClient?.startLocalVideo(localView)
                            }
                        }

                    }
                    binding.rejectButton.setOnClickListener {
                        setIncomingCallLayoutGone()
                        socketRepository?.sendMessageToSocket(
                            MessageModel(
                                "busy", userName, null, null))}
                binding.remoteViewLoading.visibility = View.GONE


                }

            /*ice_candidate*/"server_candidate"->{
                try {
                    Log.d(TAG, "server_candidate ")
                    val receivingCandidate = gson.fromJson(gson.toJson(/*message.data*/message.candidate),
                        IceCandidateModel::class.java)
                    rtcClient?.addIceCandidate(IceCandidate(receivingCandidate.sdpMid,
                        Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),receivingCandidate.candidate))
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
            "server_busy_user"->{
                setCallLayoutGone()
                setWhoToCallLayoutVisible()
                setIncomingCallLayoutGone()
                rtcClient?.endCall()
            }
        }}


    private fun setIncomingCallLayoutGone(){
        binding.incomingCallLayout.visibility = View.GONE
    }
    private fun setIncomingCallLayoutVisible() {
        binding.incomingCallLayout.visibility = View.VISIBLE
    }

    private fun setCallLayoutGone() {
        binding.callLayout.visibility = View.GONE
    }

    private fun setCallLayoutVisible() {
        binding.callLayout.visibility = View.VISIBLE
    }

    private fun setWhoToCallLayoutGone() {
        binding.whoToCallLayout.visibility = View.GONE
    }

    private fun setWhoToCallLayoutVisible() {
        binding.whoToCallLayout.visibility = View.VISIBLE
    }
}