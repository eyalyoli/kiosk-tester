package com.example.testdevices

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.serialport.SerialPort
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.io.InputStream
import java.lang.Exception

class CoinAcceptor : AppCompatActivity() {
    var btnConnect: Button? = null
    var btnSendCmd: Button? = null
    var txtOutput: TextView? = null
    var txtCommand: EditText? = null
    var txtPath: EditText? = null
    var txtBaud: EditText? = null

    var serialPort: SerialPort? = null
    var serialReader: SerialReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coin_acceptor)

        //bind views
        btnConnect = findViewById(R.id.btnConnectCoin)
        btnSendCmd = findViewById(R.id.btnSendCmd)
        txtBaud = findViewById(R.id.txtBaud)
        txtPath = findViewById(R.id.txtPath)
        txtCommand = findViewById(R.id.txtActionCommand)
        txtOutput = findViewById(R.id.txtOutput)

        // handle connection
        btnConnect!!.setOnClickListener {
            try {
                val path = txtPath!!.text.toString()
                val baud = txtBaud!!.text.toString().toInt()
                Log.i("SerialReader", "path:" + path + " baud:" + baud)

//                serialPort = SerialPort.newBuilder(
//                    File(path),
//                    baud
//                ).build()
                val inS: InputStream = File(path).inputStream()

                // bind output to ui - setup new thread
                //val inS: InputStream = serialPort!!.inputStream
                serialReader = SerialReader()
                serialReader!!.setup(inS, txtOutput!!)
                serialReader!!.start()
            } catch (err: Exception) {
                Log.e("SerialReader", "can't open serial port connection:$err")
            }
        }
    }

    // close connections to serial port
    fun closeSerial() {
        if (serialReader != null) serialReader!!.interrupt()
        serialReader = null

        if (serialPort != null) serialPort!!.close()
        serialPort = null
    }

}