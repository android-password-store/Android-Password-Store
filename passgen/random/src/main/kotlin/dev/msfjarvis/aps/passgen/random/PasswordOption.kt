enum class PasswordOption(val key: String) {
  NoDigits("0"),
  NoUppercaseLetters("A"),
  NoAmbiguousCharacters("B"),
  FullyRandom("s"),
  AtLeastOneSymbol("y"),
  NoLowercaseLetters("L")
}