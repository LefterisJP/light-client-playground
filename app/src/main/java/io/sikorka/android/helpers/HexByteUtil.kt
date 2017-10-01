package io.sikorka.android.helpers

//todo: it almost worked but it inserted an ff (selfdestruct opcode at the beginning)
fun String.hexToByteArray(): ByteArray {
  val len = length
  val data = ByteArray(len / 2)
  var i = 0
  while (i < len) {
    data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
    i += 2
  }
  return data
}