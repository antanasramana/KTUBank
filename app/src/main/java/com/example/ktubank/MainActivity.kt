package com.example.ktubank

import BankBalanceScreen
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.ktubank.nfc.NdefMessageParser
import com.example.ktubank.ui.theme.KTUBankTheme
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private val bankBalanceViewModel by viewModels<BankBalanceViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setContent {
            KTUBankTheme {
                BankBalanceScreen(
                    balance = bankBalanceViewModel.accountBalance.toString(),
                    accountName = bankBalanceViewModel.accountName,
                    accountSurname = bankBalanceViewModel.accountSurname,
                    onDeposit = { depositAmount ->
                        Toast.makeText(
                            this,
                            "Scan card to deposit $depositAmount €",
                            Toast.LENGTH_LONG
                        ).show()
                        bankBalanceViewModel.changeAmountToChange(depositAmount);
                    },
                    onWithdraw = { withdrawAmount ->
                        if (withdrawAmount > bankBalanceViewModel.accountBalance) {
                            Toast.makeText(
                                this,
                                "Cannot withdraw money, not enough balance!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Toast.makeText(
                            this,
                            "Scan card to withdraw $withdrawAmount €",
                            Toast.LENGTH_LONG
                        ).show()
                        bankBalanceViewModel.changeAmountToChange(-withdrawAmount)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent == null) {
            Toast.makeText(this, "Received null intent", Toast.LENGTH_SHORT).show()
            return
        }

        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        try {
            val mNdef = Ndef.get(tag)
            mNdef.connect()

            // Check if the tag is NDEF formatted
            if (mNdef.isConnected) {
                val mNdefMessage = mNdef.ndefMessage
                parserNDEFMessage(mNdefMessage)
                // Iterate through NDEF records to extract data
                for (record in mNdefMessage.records) {
                    // Assuming the record contains text
                    val payload = record.payload
                    // Decode payload to string using UTF-8 encoding
                    val text = String(payload, charset("UTF-8"))
                    val parsedAccount = AccountInfo.fromString(text.substring(3))

                    bankBalanceViewModel.changeAccountName(parsedAccount.name)
                    bankBalanceViewModel.changeAccountSurname(parsedAccount.surname)
                    bankBalanceViewModel.changeBalance(parsedAccount.balance)

                }

                if (bankBalanceViewModel.amountToChange != BigDecimal.ZERO) {
                    val accountInfo = bankBalanceViewModel.toAccountInfo()
                    val mRecord = NdefRecord.createTextRecord("en", accountInfo.toString())
                    val mMsg = NdefMessage(mRecord)

                    mNdef.writeNdefMessage(mMsg)
                    // Update with the new balance
                    bankBalanceViewModel.changeBalance(accountInfo.balance)
                }
            }

        } catch (e: Exception) {
            // Display a toast with the exception message
            Toast.makeText(
                this,
                "Error parsing account info: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun parserNDEFMessage(messages: NdefMessage) {
        val builder = StringBuilder()
        val records = NdefMessageParser.parse(messages)
        val size = records.size

        for (i in 0 until size) {
            val record = records[i]
            val str = record.str()
            builder.append(str).append("\n")
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        // Define intent filters for MIFARE
        val intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )

        val techList = arrayOf(
            arrayOf(MifareClassic::class.java.name)
        )

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techList)
    }

    override fun onPause() {
        super.onPause()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcAdapter.disableForegroundDispatch(this)
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KTUBankTheme {
    }
}