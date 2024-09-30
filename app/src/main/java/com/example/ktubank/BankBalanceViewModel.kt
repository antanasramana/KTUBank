package com.example.ktubank

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.math.BigDecimal

class BankBalanceViewModel : ViewModel() {

    // Use BigDecimal for the account balance
    var accountName by mutableStateOf("N/A")
        private set
    var accountSurname by mutableStateOf("N/A")
        private set
    var accountBalance by mutableStateOf(BigDecimal.ZERO)
        private set
    var amountToChange by mutableStateOf(BigDecimal.ZERO)
        private set


    // Function to change the account name
    fun changeAccountName(newAccountName: String) {
        accountName = newAccountName
    }

    // Function to change the account surname
    fun changeAccountSurname(newAccountSurname: String) {
        accountSurname = newAccountSurname
    }

    fun changeBalance(newBalance: BigDecimal) {
        accountBalance = newBalance
    }

    fun changeAmountToChange(newAmountToChange: BigDecimal) {
        amountToChange = newAmountToChange
    }

    // Function to convert ViewModel data to AccountInfo object
    fun toAccountInfo(): AccountInfo {
        if (amountToChange != BigDecimal.ZERO) {
            val accountInfo = AccountInfo(
                name = accountName,
                surname = accountSurname,
                balance = accountBalance + amountToChange
            )

            amountToChange = BigDecimal.ZERO
            return accountInfo;
        }

        return AccountInfo(
            name = accountName,
            surname = accountSurname,
            balance = accountBalance
        )
    }
}