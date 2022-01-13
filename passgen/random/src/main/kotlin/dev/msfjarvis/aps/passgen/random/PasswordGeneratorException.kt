package dev.msfjarvis.aps.passgen.random

public sealed class PasswordGeneratorException(message: String? = null, cause: Throwable? = null) : Throwable(message, cause)

public class MaxIterationsExceededException: PasswordGeneratorException()

public class NoCharactersIncludedException: PasswordGeneratorException()

public class PasswordLengthTooShortException: PasswordGeneratorException()
