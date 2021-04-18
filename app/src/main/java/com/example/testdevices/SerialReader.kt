package com.example.testdevices

import android.widget.TextView
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

fun byteArrayToHex(a: ByteArray): String? {
    val sb = StringBuilder(a.size * 2)
    for (b in a) sb.append(String.format("%02x", b))
    return sb.toString()
}

class SerialReader : Thread() {
    var mInputStream: InputStream? = null
    var mTxtOutput: TextView? = null

    fun setup(inputStream: InputStream, txtOutput: TextView) {
        mInputStream = inputStream
        mTxtOutput = txtOutput
    }

    override fun run() {
        super.run()
        while (!isInterrupted) {
            var size: Int
            try {
                val buffer = ByteArray(64)
                if (mInputStream == null) return
                size = mInputStream!!.read(buffer)
                if (size > 0) {
                    onDataReceived(buffer, size)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return
            }
        }
    }

    private fun onDataReceived(buffer: ByteArray, size: Int) {
        val hexStr = byteArrayToHex(buffer)
        val str = String(buffer, StandardCharsets.UTF_8)

        mTxtOutput!!.text = mTxtOutput!!.text.toString() + "\n$hexStr ($str)"
    }
}