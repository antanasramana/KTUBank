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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ktubank.BiometricPromptManager.BiometricResult
import com.example.ktubank.nfc.NdefMessageParser
import com.example.ktubank.ui.theme.KTUBankTheme
import java.math.BigDecimal

class MainActivity : AppCompatActivity() {
    private val promptManager by lazy {
        BiometricPromptManager(this)
    }
    private lateinit var nfcAdapter: NfcAdapter
    private val bankBalanceViewModel by viewModels<BankBalanceViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setContent {
            KTUBankTheme {
                val biometricResult by promptManager.promptResults.collectAsState(initial = null)
                var amountToDeposit by remember { mutableStateOf(BigDecimal.ZERO) }
                var amountToWithdraw by remember { mutableStateOf(BigDecimal.ZERO) }

                // Wrapping the BankBalanceScreen and the new button in a Box layout to position them properly
                Box(modifier = Modifier.fillMaxSize()) {
                    // BankBalanceScreen content
                    BankBalanceScreen(
                        balance = bankBalanceViewModel.accountBalance.toString(),
                        accountName = bankBalanceViewModel.accountName,
                        accountSurname = bankBalanceViewModel.accountSurname,
                        onDeposit = { depositAmount ->
                            promptManager.showBiometricPrompt(
                                "Authenticate to deposit",
                                "Please authenticate using biometrics"
                            )
                            amountToDeposit = depositAmount
                        },
                        onWithdraw = { withdrawAmount ->
                            if (withdrawAmount > bankBalanceViewModel.accountBalance) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Cannot withdraw money, not enough balance!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            promptManager.showBiometricPrompt(
                                "Authenticate to withdraw",
                                "Please authenticate using biometrics"
                            )
                            amountToWithdraw = withdrawAmount
                        }
                    )

                    Button(
                        onClick = {
                            val intent = Intent(this@MainActivity, IssueNewCardActivity::class.java)
                            startActivity(intent)
                        },
                        shape = RoundedCornerShape(50), // Rounded corners like the other buttons
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A237E), // Same color as "Deposit" button
                            contentColor = Color.White // White text
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd) // Align to the top right
                            .padding(30.dp) // Padding for placement
                    ) {
                        Text(text = "Issue Card")
                    }
                }
                biometricResult?.let { result ->
                    if (amountToDeposit != BigDecimal.ZERO) {
                        when (result) {
                            BiometricResult.AuthenticationSuccess -> {
                                Toast.makeText(
                                    this,
                                    "Scan card to deposit $amountToDeposit €",
                                    Toast.LENGTH_LONG
                                ).show()
                                bankBalanceViewModel.changeAmountToChange(amountToDeposit);
                                amountToDeposit = BigDecimal.ZERO;
                                promptManager.resetBiometricResult()
                            }

                            else -> {}
                        }
                    }
                    if (amountToWithdraw != BigDecimal.ZERO) {
                        when (result) {
                            BiometricResult.AuthenticationSuccess -> {
                                Toast.makeText(
                                    this,
                                    "Scan card to withdraw $amountToWithdraw €",
                                    Toast.LENGTH_LONG
                                ).show()
                                bankBalanceViewModel.changeAmountToChange(-amountToWithdraw)
                                amountToWithdraw = BigDecimal.ZERO;
                                promptManager.resetBiometricResult()
                            }

                            else -> {}
                        }
                    }
                }
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
                    val encryptionUtil = EncryptionUtil()

                    val payload = record.payload
                    // Decode payload to string using UTF-8 encoding
                    val encryptedTextWithLanguageCode = String(payload, charset("UTF-8"))
                    val encryptedText = encryptedTextWithLanguageCode.substring(3)
                    val decryptedText = encryptionUtil.decrypt(encryptedText)


                    val parsedAccount = AccountInfo.fromString(decryptedText)

                    bankBalanceViewModel.changeAccountName(parsedAccount.name)
                    bankBalanceViewModel.changeAccountSurname(parsedAccount.surname)
                    bankBalanceViewModel.changeBalance(parsedAccount.balance)

                }

                if (bankBalanceViewModel.amountToChange != BigDecimal.ZERO) {
                    val encryptionUtil = EncryptionUtil()

                    val accountInfo = bankBalanceViewModel.toAccountInfo()
                    val accountInfoString = accountInfo.toString()
                    val encryptedAccountInfo = encryptionUtil.encrypt(accountInfoString)

                    val mRecord = NdefRecord.createTextRecord("en", encryptedAccountInfo)
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