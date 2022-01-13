package dev.msfjarvis.aps.passgen.random.util

/** Clears the given [flag] from the value of this [Int] */
internal infix fun Int.clearFlag(flag: Int): Int {
  return this and flag.inv()
}

/** Checks if this [Int] contains the given [flag] */
internal infix fun Int.hasFlag(flag: Int): Boolean {
  return this and flag == flag
}
