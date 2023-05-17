package com.example.mqtttest

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.*
import androidx.appcompat.app.AppCompatActivity
import com.example.mqtttest.PreferenceUtil.ipAddress
import com.example.mqtttest.PreferenceUtil.topic
import com.example.mqtttest.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity(), OnClickListener {
    private val TAG = "MQTTService"

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var mqttClient: MqttClient? = null
    private val dispatcherMain = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ipEt.setText(PreferenceUtil.ipAddress)
        binding.topicEt.setText(PreferenceUtil.topic)

        binding.connectBtn.setOnClickListener(this@MainActivity)
        binding.sendBtn.setOnClickListener(this@MainActivity)
    }

    suspend fun showDebugLog(data: String) {
        Log.e(TAG, "showDebugLog() : $data")
        val str = "$data \n\n"
        binding.debugTv.append(str)

        binding.scrollview.post {
            binding.scrollview.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun subscribeTopic() {
        CoroutineScope(Dispatchers.IO).launch {
            ipAddress = binding.ipEt.text.toString()
            topic = binding.topicEt.text.toString()

            // mqtt 서버에 연결하려면 우선 서버의 주소(ip주소 혹은 도메인)가 필요합니다. 구조는 아래와 같습니다.
            // ex) "tcp://"+ "서버 주소" + ":1883"

            // tcp:// → 통신 방식을 tcp로 설정합니다.
            // 서버 주소 → 서버의 ip주소 혹은 도메인 주소입니다.
            // :1883 → 통신할 서버의 포트입니다. 기본 설정은 1883번 포트로 통신합니다.
            val serverIp = "tcp://$ipAddress"

            // 통신하고자 하는 토픽을 작성하면 됩니다.
            val topic = topic

            // MqttClient()는 Mqtt서버와 연결하기 위한 정보 설정입니다.
            // 3가지 인자가 있는데, 순서대로
            // 서버 IP, 클라이언트 ID, 메시지 저장(캐시와 비슷한 개념)
            mqttClient = MqttClient(serverIp, MqttClient.generateClientId(), null)
            withContext(Dispatchers.Main) { showDebugLog("try Subscribe() - [IP : $serverIp  |  TOPIC : $topic]") }

            try {
                mqttClient!!.connect()
                //구독 설정입니다.
                //구독을 하고자 하는 토픽을 인자 값으로 넣어주면 됩니다.
                mqttClient!!.subscribe(topic)

                // 콜백 설정입니다.
                // 구독하는 토픽으로부터 오는 콜백을 처리하는 부분입니다.
                // 연결 끊김, 메시지도착, 전송완료 이렇게 세 메소드가 존재합니다.
                mqttClient!!.setCallback(object : MqttCallback {
                    override fun connectionLost(throwable: Throwable?) {
                        //연결이 끊겼을 때
                        dispatcherMain.launch {
                            showDebugLog("connectionLost() - [$throwable]")
                        }
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        //메세지가 도착했을 때
                        //인자 값으로 받아오는 p0는 토픽을 의미하며, p1은 메시지를 의미합니다.
                        dispatcherMain.launch {
                            showDebugLog("messageArrived() - [topic: $topic  |  message: $message]")
                        }
                    }

                    override fun deliveryComplete(deliveryToken: IMqttDeliveryToken?) {
                        //메세지가 도착 하였을 때
                        dispatcherMain.launch {
                            showDebugLog("deliveryComplete() - [$deliveryToken]")
                        }
                    }
                })
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) { showDebugLog("Exception : $exception") }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.connectBtn -> subscribeTopic()
            binding.sendBtn -> {
                if (mqttClient?.isConnected == true) {
                    val sendData = binding.debugEt.text.toString()
                    binding.debugEt.text = null

                    // 메시지 발행(서버로 전송)입니다.
                    // 발행을 하기 위해서는 publish란 메서드를 사용하며 인자는 아래와 같습니다.
                    // publish(토픽, 메시지)
                    // 토픽은 2번에서 설정한 토픽으로 지정하시면 됩니다. 단 여기서 메시지는 String 타입이 아닌 byteArray타입입니다.
                    mqttClient!!.publish(topic, MqttMessage(sendData.toByteArray()))
                    dispatcherMain.launch { showDebugLog("try Publish() - [topic : $topic  |  sendData : $sendData]") }
                } else {
                    dispatcherMain.launch { showDebugLog("Client Is Not Connected") }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient?.disconnect()
        _binding = null
    }
}