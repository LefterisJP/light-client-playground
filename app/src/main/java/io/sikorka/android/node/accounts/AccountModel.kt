package io.sikorka.android.node.accounts

import org.ethereum.geth.Account

data class AccountModel(
    val addressHex: String,
    val ethAccount: Account,
    val ethBalance: Double = NO_BALANCE
) {
  companion object {
    const val NO_BALANCE = -1.0
  }
}