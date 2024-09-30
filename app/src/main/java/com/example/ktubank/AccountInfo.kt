package com.example.ktubank

import java.math.BigDecimal

class AccountInfo(
    val name: String,
    val surname: String,
    val balance: BigDecimal
) {

    // Override the toString method to return a comma-separated one-liner
    override fun toString(): String {
        return "$name,$surname,$balance"
    }

    // Companion object to provide a method to parse a string into AccountInfo
    companion object {
        fun fromString(input: String): AccountInfo {
            val parts = input.split(",")
            if (parts.size != 3) {
                throw IllegalArgumentException("Input string must contain exactly 3 parts: name, surname, balance")
            }
            val name = parts[0]
            val surname = parts[1]
            val balance = parts[2].toBigDecimalOrNull()
                ?: throw IllegalArgumentException("Balance must be a valid number")
            return AccountInfo(name, surname, balance)
        }
    }
}