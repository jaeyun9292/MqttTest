package com.example.mqtttest

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.View.*
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.mqtttest.PreferenceUtil.ipAddress
import com.example.mqtttest.PreferenceUtil.publishTopic
import com.example.mqtttest.PreferenceUtil.subscribeTopic
import com.example.mqtttest.databinding.ActivityMainBinding
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class MainActivity : AppCompatActivity(), OnClickListener {
    private val TAG = "MQTTService"

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var mqttClient: MqttClient? = null
    private val dispatcherMain = CoroutineScope(Dispatchers.Main)

    val dateFormat = SimpleDateFormat("yyyy-MM-dd | HH:mm:ss", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.brokerIpEt.setText(PreferenceUtil.ipAddress)
        binding.subscribeTopicEt.setText(PreferenceUtil.subscribeTopic)
        binding.publishTopicEt.setText(PreferenceUtil.publishTopic)

        binding.connectBtn.setOnClickListener(this@MainActivity)
        binding.subscribeBtn.setOnClickListener(this@MainActivity)
        binding.publishBtn.setOnClickListener(this@MainActivity)

        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
//        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient?.disconnect()
        _binding = null
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.connectBtn -> connectBrokerServer()
            binding.subscribeBtn -> subscribeTopic()
            binding.publishBtn -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withContext(Dispatchers.Main) {
                            val isSelected = !binding.publishBtn.isSelected
                            binding.publishBtn.isSelected = isSelected
                            val publishBtnText = if (isSelected) "멈춤" else "발행"
                            binding.publishBtn.text = publishBtnText
                        }
                        val sendData = binding.publishMessageEt.text.toString()
//                        binding.publishMessageEt.text = null
                        while (binding.publishBtn.isSelected) {
                            publishMqttMessage(sendData)
                            delay(1000)
                        }
                    } catch (exception: Exception) {
                        withContext(Dispatchers.Main) { showDebugLog("에러 : $exception") }
                    }
                }
            }
        }
    }

    suspend fun showDebugLog(data: String) {
        val calendar = Calendar.getInstance()
        val currentDateTime = dateFormat.format(calendar.time)

        val text = binding.debugTv.text.toString()
        if (binding.debugTv.text.length > 3000)
            binding.debugTv.text = text.substring(300, text.length)

        val str = "현재 시간 : $currentDateTime\n$data \n\n"
        binding.debugTv.append(str)

//        binding.scrollview.post {
//            binding.scrollview.fullScroll(View.FOCUS_DOWN)
//        }
    }

    private fun connectBrokerServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
//                mqtt 서버에 연결하려면 우선 서버의 주소(ip주소 혹은 도메인)가 필요합니다. 구조는 아래와 같습니다.
//                ex) "tcp://"+ "서버 주소" + ":1883"
//                => 통신 방식: tcp | 서버의 ip주소 또는 도메인 주소 | 통신할 서버의 포트 번호
                ipAddress = binding.brokerIpEt.text.toString()
                val serverIp = "tcp://$ipAddress"

//                MqttClient()는 Mqtt서버와 연결하기 위한 정보 설정입니다.
//                => MqttClient(서버 IP, 클라이언트 ID, 메시지 저장(캐시와 비슷한 개념))
                mqttClient = MqttClient(serverIp, MqttClient.generateClientId(), null)

                withContext(Dispatchers.Main) { showDebugLog("try connectBrokerServer() - [ip : $serverIp]") }
                mqttClient!!.connect()
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) { showDebugLog("Exception : $exception") }
            }
        }
    }

    private fun subscribeTopic() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                subscribeTopic = binding.subscribeTopicEt.text.toString()
                val topic = subscribeTopic
                withContext(Dispatchers.Main) { showDebugLog("try subscribeTopic() - [topic : $topic]") }

                // 구독 => subscribe(토픽)
                mqttClient!!.subscribe(topic)
                mqttClient!!.setCallback(object : MqttCallback {
                    override fun connectionLost(throwable: Throwable?) {
                        //연결 끊김
                        dispatcherMain.launch {
                            showDebugLog("connectionLost() - [$throwable]")
                        }
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        // 메시지 수신
//                        val receivedMessage = if (isJsonString(message.toString())) {
//                            val receivedJsonElement = JsonParser.parseString(message.toString())
//                            val receivedJsonString = receivedJsonElement.asJsonObject.get("name").asString
//                            Log.e(TAG, "messageArrived  -  Message Is Json")
//                            receivedJsonString
//                        } else {
//                            Log.e(TAG, "messageArrived  -  Message Is NOT Json")
//                            message.toString()
//                        }
                        val receivedMessage = message.toString()

                        dispatcherMain.launch {
                            showDebugLog("메시지 수신 - [ 토픽 : $topic | 메시지 : $receivedMessage ]")
                        }
                    }

                    override fun deliveryComplete(deliveryToken: IMqttDeliveryToken?) {
                        // 메시지 전송 완료
                        dispatcherMain.launch {
                            showDebugLog("메시지 전송 완료 - [$deliveryToken]")
                        }
                    }
                })
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) { showDebugLog("Exception : $exception") }
            }
        }
    }

    private suspend fun publishMqttMessage(sendData: String) {
        try {
            if (mqttClient?.isConnected == true) {
                publishTopic = binding.publishTopicEt.text.toString()

                val jsonObject = JsonObject()
                jsonObject.addProperty("name", sendData)
                val sendJsonStr = gson.toJson(jsonObject)
                // 발행 => publish(토픽, 메시지)
                // 메시지는 String 타입이 아닌 byteArray타입입니다.
                mqttClient!!.publish(publishTopic, MqttMessage(sendJsonStr.toByteArray()))
                dispatcherMain.launch { showDebugLog("try publishMqttMessage() - [topic : $publishTopic]\nsendData :\n$sendJsonStr") }
            } else {
                dispatcherMain.launch { showDebugLog("Client Is Not Connected") }
            }
        } catch (exception: Exception) {
            throw exception
        }

    }


    fun isJsonString(input: String): Boolean {
        val isJson = try {
            val jsonObject = JSONObject(input)
            true
        } catch (e: JSONException) {
            try {
                val jsonArray = JSONArray(input)
                true
            } catch (e2: JSONException) {
                false
            }
        }
        return isJson
    }

}