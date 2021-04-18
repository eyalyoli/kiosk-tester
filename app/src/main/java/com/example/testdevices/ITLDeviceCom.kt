package com.example.testdevices

import android.util.Log
import android.widget.TextView
import com.ftdi.j2xx.FT_Device
import device.itl.sspcoms.*
import device.itl.sspcoms.SSPSystem.BillAction

const val READBUF_SIZE = 256
const val WRITEBUF_SIZE = 4096

class ITLDeviceCom : Thread(), DeviceSetupListener,
    DeviceEventListener, DeviceFileUpdateListener {
    private var ftDev: FT_Device? = null
    var rbuf = ByteArray(READBUF_SIZE)
    var wbuf = ByteArray(WRITEBUF_SIZE)
    var mReadSize = 0
    private var sspDevice: SSPDevice? = null

    private var isRunning = false
    private var ssp: SSPSystem? = null

    private var statusText:TextView? = null

    // a simple thread initializer
    fun setup(
        ftdev: FT_Device?,
        address: Int,
        escrow: Boolean,
        essp: Boolean,
        key: Long,
        statusText:TextView?
    ) {
        this.statusText = statusText

        ssp = SSPSystem()
        // register listeners of SSP to here
        ssp!!.setOnDeviceSetupListener(this)
        ssp!!.setOnEventUpdateListener(this)
        ssp!!.setOnDeviceFileUpdateListener(this) // maybe not needed - it is what

        ftDev = ftdev
        ssp!!.SetAddress(address)
        ssp!!.EscrowMode(escrow)
        ssp!!.SetESSPMode(essp, key)
    }

    // this starts running when you call `thread.start()`
    override fun run() {
        var readSize: Int
        ssp!!.Run()
        isRunning = true

        while (isRunning) { // this is how an infinite thread is run
            // poll for transmit data - this basically read from SSP device and writes to FT device
            synchronized(ftDev!!) {
                val newDataLen = ssp!!.GetNewData(wbuf)
                if (newDataLen > 0) { // check if there is something to read
                    if (ssp!!.GetDownloadState() != SSPSystem.DownloadSetupState.active) {
                        ftDev!!.purge(1.toByte())
                    }
                    ftDev!!.write(wbuf, newDataLen)
                    ssp!!.SetComsBufferWritten(true)
                }
            }

            // poll for received - this basically read from FT device and writes to SSP device
            synchronized(ftDev!!) {
                readSize = ftDev!!.queueStatus
                if (readSize > 0) { // check if there is something to read
                    mReadSize = readSize
                    if (mReadSize > READBUF_SIZE) {
                        mReadSize = READBUF_SIZE
                    }
                    readSize = ftDev!!.read(rbuf, mReadSize)
                    ssp!!.ProcessResponse(rbuf, readSize)
                }
            }


            // coms config changes
            val cfg = ssp!!.GetComsConfig()
            if (cfg.configUpdate == SSPComsConfig.ComsConfigChangeState.ccNewConfig) {
                cfg.configUpdate = SSPComsConfig.ComsConfigChangeState.ccUpdating
                setFTDeviceConfig(
                    ftDev!!, cfg.baud,
                    cfg.dataBits,
                    cfg.stopBits,
                    cfg.parity,
                    cfg.flowControl
                )
                cfg.configUpdate = SSPComsConfig.ComsConfigChangeState.ccUpdated
            }
            try {
                sleep(100) // sleeps the infinite loop to wait for more data
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    override fun OnNewDeviceSetup(dev: SSPDevice) {
        Log.i("ITLDeviceCom", "New device setup event")
        // call to Main UI
        //MainActivity.mainActivity.runOnUiThread(Runnable { MainActivity.DisplaySetUp(dev) }
    }

    override fun OnDeviceDisconnect(dev: SSPDevice) {
        Log.i("ITLDeviceCom", "Device disconnect event")
        //MainActivity.mainActivity.runOnUiThread(Runnable { MainActivity.DeviceDisconnected(dev) })
    }

    // This is called when an event occurs on the cash acceptor - e.g. change in ready state, insert cash...
    override fun OnDeviceEvent(ev: DeviceEvent) {
        Log.i("ITLDeviceCom", "Device new event")
        statusText!!.setText("Type: "+ev.event +"\nCurrency: "+ev.currency+"\nValue: "+ev.value+"\nValueEx: "+ev.valueEx)
        //MainActivity.mainActivity.runOnUiThread(Runnable { MainActivity.DisplayEvents(ev) })
    }

    // Don't think it is relevant for now
    override fun OnFileUpdateStatus(sspUpdate: SSPUpdate) {
        Log.i("ITLDeviceCom", "File update status event")
        //MainActivity.mainActivity.runOnUiThread(Runnable { MainActivity.UpdateFileDownload(sspUpdate) })
    }

    fun close() {
        ssp!!.Close()
        isRunning = false
    }

    // ############# Not used - currently not relevant #############

    fun SetSSPDownload(update: SSPUpdate?): Boolean {
        return ssp!!.SetDownload(update)
    }

    fun SetEscrowMode(mode: Boolean) {
        ssp!!.EscrowMode(mode)
    }

    fun SetDeviceEnable(en: Boolean) {
        if (en) {
            ssp!!.EnableDevice()
        } else {
            ssp!!.DisableDevice()
        }
    }

    fun SetEscrowAction(action: BillAction?) {
        ssp!!.SetBillEscrowAction(action)
    }

    fun SetBarcocdeConfig(cfg: BarCodeReader?) {
        ssp!!.SetBarCodeConfiguration(cfg)
    }

    fun GetDeviceCode(): Int {
        return if (ssp != null) {
            sspDevice!!.headerType.value
        } else {
            -1
        }
    }
}