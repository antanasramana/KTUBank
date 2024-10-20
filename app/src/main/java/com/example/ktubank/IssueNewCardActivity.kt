package com.example.ktubank

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
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ktubank.ui.theme.KTUBankTheme
import java.math.BigDecimal

typealias OnCardIssued = (String, String, BigDecimal) -> Unit

class IssueNewCardActivity : ComponentActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private val bankBalanceViewModel by viewModels<BankBalanceViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setContent {
            KTUBankTheme {
                IssueNewCardScreen(onCardIssued = { name, surname, balance ->
                    bankBalanceViewModel.changeAccountName(name)
                    bankBalanceViewModel.changeAccountSurname(surname)
                    bankBalanceViewModel.changeBalance(balance)
                },
                    onClose = { finish() })
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
                if (bankBalanceViewModel.accountName != "N/A" && bankBalanceViewModel.accountSurname != "N/A" && bankBalanceViewModel.accountBalance != BigDecimal.ZERO) {
                    val encryptionUtil = EncryptionUtil()

                    val accountInfo = bankBalanceViewModel.toAccountInfo()
                    val accountInfoString = accountInfo.toString()
                    val encryptedAccountInfo = encryptionUtil.encrypt(accountInfoString)

                    val mRecord = NdefRecord.createTextRecord("en", encryptedAccountInfo)
                    val mMsg = NdefMessage(mRecord)

                    mNdef.writeNdefMessage(mMsg)

                    Toast.makeText(
                        this,
                        "Card issued!",
                        Toast.LENGTH_SHORT
                    ).show()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueNewCardScreen(onCardIssued: OnCardIssued, onClose: () -> Unit) {
    // State to hold input values, e.g., name, surname, and balance
    val name = remember { mutableStateOf("") }
    val surname = remember { mutableStateOf("") }
    val balance = remember { mutableStateOf("") }
    val readyToScanTheCard = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFEFEF))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = "Issue a New Card",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color(0xFF1A237E)
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card container for input fields
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)) // Card color set to #465362
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Input field for Name
                // Input field for Name
                TextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.White, // White background for text fields
                        focusedLabelColor = Color.Black, // Black color for label when focused
                        unfocusedLabelColor = Color.Gray // Gray color for label when not focused
                    )
                )

// Input field for Surname
                TextField(
                    value = surname.value,
                    onValueChange = { surname.value = it },
                    label = { Text("Surname") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.White, // White background for text fields
                        focusedLabelColor = Color.Black, // Black color for label when focused
                        unfocusedLabelColor = Color.Gray // Gray color for label when not focused
                    )
                )

// Input field for Initial Balance
                TextField(
                    value = balance.value,
                    onValueChange = { balance.value = it },
                    label = { Text("Initial Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.White, // White background for text fields
                        focusedLabelColor = Color.Black, // Black color for label when focused
                        unfocusedLabelColor = Color.Gray // Gray color for label when not focused
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Row for the "Issue Card" and "Close" buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Issue Card button
            Button(
                onClick = {
                    if (name.value.isNotEmpty() && surname.value.isNotEmpty() && balance.value.isNotEmpty()) {
                        val balanceValue = BigDecimal(balance.value)
                        onCardIssued(name.value, surname.value, balanceValue)
                        readyToScanTheCard.value = true
                    }
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A237E),
                    contentColor = Color.White
                )
            ) {
                Text(text = if (readyToScanTheCard.value) "Scan the card!" else "Issue Card")
            }

            // Close button
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F),
                    contentColor = Color.White
                )
            ) {
                Text(text = "Close")
            }
        }
    }
}