package com.example.testdevices

// imports needed for init of cash acceptor
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// imports for cash acceptor
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.D2xxException
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import com.ftdi.j2xx.FT_Device

/**
 * I converted the minimal code of their project ot kotlin
 * It has a lot of refactors + bug fixes
 * Mainly follow my comments
 *
 * I think what they mean:
 *  FT - the computer itself - the motherboard chipset/controller
 *  2xx - is the series of the board's model
 *  SSP - the cash acceptor hardware
 *  ITL - is the brand of the cash acceptor
 */
class CashAcceptor : AppCompatActivity() {
    // fields for cash acceptor
    private var sspDeviceCom: ITLDeviceCom? = null // responsible for cash acceptor coms
    var ftDeviceManager: D2xxManager? = null // manages the computer
    var ftDevice: FT_Device? = null // responsible for computer coms

    var connectButton: Button? = null
    var statusText:TextView?=null
    var devicesText:TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cash_acceptor)

        // init device
        Log.i("SSPFTmanager", "init device")
        try {
            ftDeviceManager = D2xxManager.getInstance(this)
            Log.i("SSPFTmanager", "got device "+ (ftDeviceManager!=null).toString())
        } catch (ex: D2xxException) {
            Log.e("SSPFTmanager", ex.toString())
        }

        // register for usb de/attach events - optional!
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.priority = 500
        this.registerReceiver(mUsbReceiver, filter)

        //init cash acceptor thread
        sspDeviceCom = ITLDeviceCom()

        //connect to ui comps
        connectButton = findViewById(R.id.btnConnect)
        statusText = findViewById(R.id.txtStatus)
        devicesText = findViewById(R.id.txtDevices)

        connectButton!!.setOnClickListener(View.OnClickListener {
            // call this on some button click
            connectToCashAcceptor()
        })


    }

    private fun connectToCashAcceptor() {
        if (openFTDevice()) {
            sspDeviceCom!!.setup(
                ftDevice,
                0,
                escrow = false,
                essp = false,
                key = 0,
                statusText
            ) // configs cash acceptor
            sspDeviceCom!!.start() // this start the thread in background
            // show UI stuff
        } else {
            // no usb - failed to open device
            Log.w("CashAcceptor", "No USB connection detected!")
        }
    }

    // broadcast receiver for usb de/attach events
    var mUsbReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                openFTDevice() // reconnect to FT controller
                Log.e("mUsbReceiver", "usb attached")
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                closeSSPnFTDevice()
                // device detached
                Log.e("mUsbReceiver", "usb detached")
            }
        }
    }

    // ############## FT device management ################
    private fun runFTDeviceThread(ftDevice: FT_Device) {
        setFTDeviceConfig(
            ftDevice,
            9600,
            D2xxManager.FT_DATA_BITS_8,
            D2xxManager.FT_STOP_BITS_2,
            0.toByte(),
            0.toByte()
        )
        ftDevice.purge((D2xxManager.FT_PURGE_TX.toInt() or D2xxManager.FT_PURGE_RX.toInt()).toByte())
        ftDevice.restartInTask()
    }

    private fun openFTDevice():Boolean {
        if (ftDevice != null && ftDevice!!.isOpen) {
            // if FT is open and run thread is stopped => start thread
            runFTDeviceThread(ftDevice!!)
            return true
        } else {
            // if FT is not open
            if (ftDeviceManager != null) {
                // Get the connected USB FTDI devices
                var devCount: Int =
                    ftDeviceManager!!.createDeviceInfoList(this) // returns num of usb devices
                val deviceList =
                    arrayOfNulls<FtDeviceInfoListNode>(devCount) // init arr of nulls - seems redundant
                ftDeviceManager!!.getDeviceInfoList(devCount, deviceList) // fills usb devices list

                devicesText!!.text = "Devices Num: "+devCount+"\nDevices list:"+deviceList.map { it!!.description }

                if (devCount >= 1) {
                    // there are connected usb devices
                    if (ftDevice == null) {
                        ftDevice = ftDeviceManager!!.openByIndex(this, 0) // get usb device at index 0
                    } else {
                        // don't know what this is for
                        synchronized(ftDevice!!) { ftDevice = ftDeviceManager!!.openByIndex(this, 0) }
                    }

                    // run thread for device
                    if (ftDevice!!.isOpen) {
                        runFTDeviceThread(ftDevice!!)
                    }
                }
            }
        }
        return false // if you get here then something didn't go right
    }

    private fun closeSSPnFTDevice() {
        sspDeviceCom!!.close()
        ftDevice!!.close()
    }
}

// public static function to change FT device config
fun setFTDeviceConfig(ftDevice:FT_Device,
                      baud: Int,
                      pDataBits: Byte,
                      pStopBits: Byte,
                      pParity: Byte,
                      flowControl: Byte
) {
    if (!ftDevice.isOpen) {
        return
    }

    var dataBits = pDataBits
    var stopBits = pStopBits
    var parity = pParity

    // configure our port
    // reset to UART mode for 232 devices
    ftDevice.setBitMode(0.toByte(), D2xxManager.FT_BITMODE_RESET)
    ftDevice.setBaudRate(baud)

    // verify that ranges are right!
    if (dataBits.toInt() !in 7..8) dataBits = D2xxManager.FT_DATA_BITS_8
    if (stopBits.toInt() !in 1..2) stopBits = D2xxManager.FT_STOP_BITS_1
    if (parity.toInt() !in 0..4) parity = D2xxManager.FT_PARITY_NONE
    ftDevice.setDataCharacteristics(dataBits, stopBits, parity)

//         this is the original code - I think it has a bug
//        val flowCtrlSetting: Short = when (flowControl.toInt()) {
//            0 -> D2xxManager.FT_FLOW_NONE
//            1 -> D2xxManager.FT_FLOW_RTS_CTS
//            2 -> D2xxManager.FT_FLOW_DTR_DSR
//            3 -> D2xxManager.FT_FLOW_XON_XOFF
//            else -> D2xxManager.FT_FLOW_NONE
//        }
    // verify that ranges are right!
    val flowCtrlSetting: Short = if (shortArrayOf(
            D2xxManager.FT_FLOW_NONE,
            D2xxManager.FT_FLOW_RTS_CTS,
            D2xxManager.FT_FLOW_DTR_DSR,
            D2xxManager.FT_FLOW_XON_XOFF
        ).contains(flowControl.toShort())
    ) flowControl.toShort() else D2xxManager.FT_FLOW_NONE
    ftDevice.setFlowControl(flowCtrlSetting, 0x0b.toByte(), 0x0d.toByte())
}