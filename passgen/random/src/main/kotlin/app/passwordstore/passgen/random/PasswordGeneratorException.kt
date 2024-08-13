/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.passgen.random

public sealed class PasswordGeneratorException(message: String? = null, cause: Throwable? = null) :
  Throwable(message, cause)

public class MaxIterationsExceededException : PasswordGeneratorException()

public class NoCharactersIncludedException : PasswordGeneratorException()

public class PasswordLengthTooShortException : PasswordGeneratorException()
