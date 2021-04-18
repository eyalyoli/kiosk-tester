package com.example.testdevices

// imports needed for init of cash acceptor
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    var coinAccBtn: Button? = null
    var cashAccBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //connect to ui comps
        coinAccBtn = findViewById(R.id.btnCoinAcceptor)
        cashAccBtn = findViewById(R.id.btnCashAcceptor)

        cashAccBtn!!.setOnClickListener {
            var i = Intent(this, CashAcceptor::class.java)
            startActivity(i)
        }
        coinAccBtn!!.setOnClickListener {
            var i = Intent(this, CoinAcceptor::class.java)
            startActivity(i)
        }
    }


}