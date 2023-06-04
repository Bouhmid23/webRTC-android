package com.codewithkael.webrtcprojectforrecord

import android.app.Application
import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import org.webrtc.*
import java.lang.Exception


class RTCClient(
    private val application: Application,
    private val username: String,
    private val socketRepository: SocketRepository,
    private val observer: PeerConnection.Observer
) {

    init {
        initPeerConnectionFactory(application)
    }

    private val eglContext = EglBase.create()
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.1.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:numb.viagenie.ca")
            .setUsername("webrtc@live.com")
            .setPassword("muazkh")
            .createIceServer()
    )
    private val peerConnection by lazy { createPeerConnection(observer) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var videoCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null


    private fun initPeerConnectionFactory(application: Application) {
        val peerConnectionOption = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(peerConnectionOption)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglContext.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServers, observer)
    }

    fun initializeSurfaceView(surface: SurfaceViewRenderer) {
        surface.run {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(eglContext.eglBaseContext, null)
        }
    }

    fun startLocalVideo(surface: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, eglContext.eglBaseContext)
        videoCapturer = getVideoCapturer(application)
        videoCapturer?.initialize(
            surfaceTextureHelper,
            surface.context, localVideoSource.capturerObserver
        )
        videoCapturer?.startCapture(320, 240, 30)
        localVideoTrack = peerConnectionFactory.createVideoTrack("local_track", localVideoSource)
        localVideoTrack?.addSink(surface)
        localAudioTrack =
            peerConnectionFactory.createAudioTrack("local_track_audio", localAudioSource)
        val localStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localStream.addTrack(localAudioTrack)
        localStream.addTrack(localVideoTrack)

        peerConnection?.addStream(localStream)

    }

    private fun getVideoCapturer(application: Application): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw
            IllegalStateException()
        }
    }

    fun call(target: String) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type.toString().lowercase())
                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                //"create_offer", username, target, offer
                            "offer",target,null,null,offer))
                        println("set local description success :$offer")
                    }

                    override fun onCreateFailure(p0: String?) {}

                    override fun onSetFailure(p0: String?) {}

                }, desc)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(userName:String?,session: SessionDescription?) {
        if (session != null) {
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}

                override fun onSetSuccess() {
                    println("set remote description success")
                    socketRepository.sendMessageToSocket(
                        MessageModel(
                            "ready",userName,null,null))}

                override fun onCreateFailure(p0: String?) {}

                override fun onSetFailure(p0: String?) {
                    println("set remote description failure $p0")
                }
            }, session)
        } else {
            println("Received null session description.")
        }
    }
    fun answer(target: String) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        println("onCreateSuccess")
                }
                    override fun onSetSuccess() {

                        val answer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type.toString().lowercase())
                        socketRepository.sendMessageToSocket(
                            MessageModel(
                             //"create_answer", username, target,answer
                            "answer",target,null,null,null,answer))
                        println("set local description success :$answer")
                    }

                    override fun onCreateFailure(p0: String?) {
                        println("onCreateFailure")}

                    override fun onSetFailure(p0: String?) {
                        println("onSetFailure")}


                }, desc)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, constraints)
    }

    fun addIceCandidate(p0: IceCandidate?) {
        try {
            peerConnection?.addIceCandidate(p0)
            println("Ice candidate added successfully")
        }catch (e:Exception){
            println("Add Ice candidate fail")
        }
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(mute)
    }

    fun toggleCamera(cameraPause: Boolean) {
        localVideoTrack?.setEnabled(cameraPause)
    }

    fun endCall() {
        peerConnection?.close()
    }
}