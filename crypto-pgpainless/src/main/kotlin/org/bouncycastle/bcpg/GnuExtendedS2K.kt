package org.bouncycastle.bcpg

/**
 * Add a constructor for GNU-extended S2K
 *
 * This extension is documented on GnuPG documentation DETAILS file,
 * section "GNU extensions to the S2K algorithm". Its support is
 * already present in S2K class but lack for a constructor.
 *
 * @author Léonard Dallot <leonard.dallot@taztag.com>
 */
public class GnuExtendedS2K(mode: Int) : S2K(SIMPLE) {
  init {
    this.type = GNU_DUMMY_S2K
    this.protectionMode = mode
  }
}
