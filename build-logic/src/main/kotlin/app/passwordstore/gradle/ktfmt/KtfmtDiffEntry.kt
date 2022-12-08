package app.passwordstore.gradle.ktfmt

data class KtfmtDiffEntry(val input: String, val lineNumber: Int, val message: String) {

  override fun toString(): String {
    return "$input:$lineNumber - $message"
  }
}
