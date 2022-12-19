@file:Suppress("MagicNumber")

package app.passwordstore.crypto

@JvmInline
public value class DeviceIdentifier(private val aid: ByteArray) {
  init {
    require(aid.size == 16) { "Invalid device application identifier" }
  }

  public val openPgpVersion: String
    get() = "${aid[6]}.${aid[7]}"

  public val manufacturer: Int
    get() = ((aid[8].toInt() and 0xff) shl 8) or (aid[9].toInt() and 0xff)

  public val serialNumber: ByteArray
    get() = aid.sliceArray(10..13)
}

// https://git.gnupg.org/cgi-bin/gitweb.cgi?p=gnupg.git;a=blob;f=scd/app-openpgp.c;hb=HEAD#l292
public val DeviceIdentifier.manufacturerName: String
  get() =
    when (manufacturer) {
      0x0001 -> "PPC Card Systems"
      0x0002 -> "Prism"
      0x0003 -> "OpenFortress"
      0x0004 -> "Wewid"
      0x0005 -> "ZeitControl"
      0x0006 -> "Yubico"
      0x0007 -> "OpenKMS"
      0x0008 -> "LogoEmail"
      0x0009 -> "Fidesmo"
      0x000A -> "VivoKey"
      0x000B -> "Feitian Technologies"
      0x000D -> "Dangerous Things"
      0x000E -> "Excelsecu"
      0x000F -> "Nitrokey"
      0x002A -> "Magrathea"
      0x0042 -> "GnuPG e.V."
      0x1337 -> "Warsaw Hackerspace"
      0x2342 -> "warpzone"
      0x4354 -> "Confidential Technologies"
      0x5343 -> "SSE Carte à puce"
      0x5443 -> "TIF-IT e.V."
      0x63AF -> "Trustica"
      0xBA53 -> "c-base e.V."
      0xBD0E -> "Paranoidlabs"
      0xCA05 -> "Atos CardOS"
      0xF1D0 -> "CanoKeys"
      0xF517 -> "FSIJ"
      0xF5EC -> "F-Secure"
      0x0000,
      0xFFFF -> "test card"
      else -> "unknown"
    }
