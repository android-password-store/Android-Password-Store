/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: LGPL-3.0-only WITH LGPL-3.0-linking-exception
 */
package com.github.androidpasswordstore.autofillparser

import android.os.Build
import androidx.annotation.RequiresApi
import logcat.LogPriority.WARN
import logcat.logcat

@DslMarker internal annotation class AutofillDsl

@RequiresApi(Build.VERSION_CODES.O)
internal interface FieldMatcher {

  fun match(fields: List<FormField>, alreadyMatched: List<FormField>): List<FormField>?

  @AutofillDsl
  class Builder {

    private var takeSingle: (FormField.(List<FormField>) -> Boolean)? = null
    private val tieBreakersSingle: MutableList<FormField.(List<FormField>) -> Boolean> =
      mutableListOf()

    private var takePair: (Pair<FormField, FormField>.(List<FormField>) -> Boolean)? = null
    private var tieBreakersPair:
      MutableList<Pair<FormField, FormField>.(List<FormField>) -> Boolean> =
      mutableListOf()

    fun takeSingle(block: FormField.(alreadyMatched: List<FormField>) -> Boolean = { true }) {
      check(takeSingle == null && takePair == null) {
        "Every block can only have at most one take{Single,Pair} block"
      }
      takeSingle = block
    }

    fun breakTieOnSingle(block: FormField.(alreadyMatched: List<FormField>) -> Boolean) {
      check(takeSingle != null) {
        "Every block needs a takeSingle block before a breakTieOnSingle block"
      }
      check(takePair == null) { "takePair cannot be mixed with breakTieOnSingle" }
      tieBreakersSingle.add(block)
    }

    fun takePair(
      block: Pair<FormField, FormField>.(alreadyMatched: List<FormField>) -> Boolean = { true }
    ) {
      check(takeSingle == null && takePair == null) {
        "Every block can only have at most one take{Single,Pair} block"
      }
      takePair = block
    }

    fun breakTieOnPair(
      block: Pair<FormField, FormField>.(alreadyMatched: List<FormField>) -> Boolean
    ) {
      check(takePair != null) { "Every block needs a takePair block before a breakTieOnPair block" }
      check(takeSingle == null) { "takeSingle cannot be mixed with breakTieOnPair" }
      tieBreakersPair.add(block)
    }

    fun build(): FieldMatcher {
      val takeSingle = takeSingle
      val takePair = takePair
      return when {
        takeSingle != null -> SingleFieldMatcher(takeSingle, tieBreakersSingle)
        takePair != null -> PairOfFieldsMatcher(takePair, tieBreakersPair)
        else -> throw IllegalArgumentException("Every block needs a take{Single,Pair} block")
      }
    }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
internal class SingleFieldMatcher(
  private val take: (FormField, List<FormField>) -> Boolean,
  private val tieBreakers: List<(FormField, List<FormField>) -> Boolean>
) : FieldMatcher {

  @AutofillDsl
  class Builder {

    private var takeSingle: (FormField.(List<FormField>) -> Boolean)? = null
    private val tieBreakersSingle: MutableList<FormField.(List<FormField>) -> Boolean> =
      mutableListOf()

    fun takeSingle(block: FormField.(alreadyMatched: List<FormField>) -> Boolean = { true }) {
      check(takeSingle == null) { "Every block can only have at most one takeSingle block" }
      takeSingle = block
    }

    fun breakTieOnSingle(block: FormField.(alreadyMatched: List<FormField>) -> Boolean) {
      check(takeSingle != null) {
        "Every block needs a takeSingle block before a breakTieOnSingle block"
      }
      tieBreakersSingle.add(block)
    }

    fun build() =
      SingleFieldMatcher(
        takeSingle ?: throw IllegalArgumentException("Every block needs a take{Single,Pair} block"),
        tieBreakersSingle
      )
  }

  override fun match(fields: List<FormField>, alreadyMatched: List<FormField>): List<FormField>? {
    return fields.minus(alreadyMatched).filter { take(it, alreadyMatched) }.let { contestants ->
      when (contestants.size) {
        1 -> return@let listOf(contestants.single())
        0 -> return@let null
      }
      var current = contestants
      for ((i, tieBreaker) in tieBreakers.withIndex()) {
        // Successively filter matched fields via tie breakers...
        val new = current.filter { tieBreaker(it, alreadyMatched) }
        // skipping those tie breakers that are not satisfied for any remaining field...
        if (new.isEmpty()) {
          logcat { "Tie breaker #${i + 1}: Didn't match any field; skipping" }
          continue
        }
        // and return if the available options have been narrowed to a single field.
        if (new.size == 1) {
          logcat { "Tie breaker #${i + 1}: Success" }
          current = new
          break
        }
        logcat { "Tie breaker #${i + 1}: Matched ${new.size} fields; continuing" }
        current = new
      }
      listOf(current.singleOrNull() ?: return null)
    }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
private class PairOfFieldsMatcher(
  private val take: (Pair<FormField, FormField>, List<FormField>) -> Boolean,
  private val tieBreakers: List<(Pair<FormField, FormField>, List<FormField>) -> Boolean>
) : FieldMatcher {

  override fun match(fields: List<FormField>, alreadyMatched: List<FormField>): List<FormField>? {
    return fields
      .minus(alreadyMatched)
      .zipWithNext()
      .filter { it.first directlyPrecedes it.second }
      .filter { take(it, alreadyMatched) }
      .let { contestants ->
        when (contestants.size) {
          1 -> return@let contestants.single().toList()
          0 -> return@let null
        }
        var current = contestants
        for ((i, tieBreaker) in tieBreakers.withIndex()) {
          val new = current.filter { tieBreaker(it, alreadyMatched) }
          if (new.isEmpty()) {
            logcat { "Tie breaker #${i + 1}: Didn't match any pair of fields; skipping" }
            continue
          }
          // and return if the available options have been narrowed to a single field.
          if (new.size == 1) {
            logcat { "Tie breaker #${i + 1}: Success" }
            current = new
            break
          }
          logcat { "Tie breaker #${i + 1}: Matched ${new.size} pairs of fields; continuing" }
          current = new
        }
        current.singleOrNull()?.toList()
      }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
internal class AutofillRule
private constructor(
  private val matchers: List<AutofillRuleMatcher>,
  private val applyInSingleOriginMode: Boolean,
  private val applyOnManualRequestOnly: Boolean,
  private val name: String
) {

  data class AutofillRuleMatcher(
    val type: FillableFieldType,
    val matcher: FieldMatcher,
    val optional: Boolean,
    val matchHidden: Boolean
  )

  enum class FillableFieldType {
    Username,
    Otp,
    CurrentPassword,
    NewPassword,
    GenericPassword,
  }

  @AutofillDsl
  class Builder(
    private val applyInSingleOriginMode: Boolean,
    private val applyOnManualRequestOnly: Boolean
  ) {

    companion object {

      private var ruleId = 1
    }

    private val matchers = mutableListOf<AutofillRuleMatcher>()
    var name: String? = null

    fun username(
      optional: Boolean = false,
      matchHidden: Boolean = false,
      block: SingleFieldMatcher.Builder.() -> Unit
    ) {
      require(matchers.none { it.type == FillableFieldType.Username }) {
        "Every rule block can only have at most one username block"
      }
      matchers.add(
        AutofillRuleMatcher(
          type = FillableFieldType.Username,
          matcher = SingleFieldMatcher.Builder().apply(block).build(),
          optional = optional,
          matchHidden = matchHidden
        )
      )
    }

    fun otp(optional: Boolean = false, block: SingleFieldMatcher.Builder.() -> Unit) {
      require(matchers.none { it.type == FillableFieldType.Otp }) {
        "Every rule block can only have at most one otp block"
      }
      matchers.add(
        AutofillRuleMatcher(
          type = FillableFieldType.Otp,
          matcher = SingleFieldMatcher.Builder().apply(block).build(),
          optional = optional,
          matchHidden = false
        )
      )
    }

    fun currentPassword(
      optional: Boolean = false,
      matchHidden: Boolean = false,
      block: FieldMatcher.Builder.() -> Unit
    ) {
      require(matchers.none { it.type == FillableFieldType.GenericPassword }) {
        "Every rule block can only have either genericPassword or {current,new}Password blocks"
      }
      matchers.add(
        AutofillRuleMatcher(
          type = FillableFieldType.CurrentPassword,
          matcher = FieldMatcher.Builder().apply(block).build(),
          optional = optional,
          matchHidden = matchHidden
        )
      )
    }

    fun newPassword(optional: Boolean = false, block: FieldMatcher.Builder.() -> Unit) {
      require(matchers.none { it.type == FillableFieldType.GenericPassword }) {
        "Every rule block can only have either genericPassword or {current,new}Password blocks"
      }
      matchers.add(
        AutofillRuleMatcher(
          type = FillableFieldType.NewPassword,
          matcher = FieldMatcher.Builder().apply(block).build(),
          optional = optional,
          matchHidden = false
        )
      )
    }

    fun genericPassword(optional: Boolean = false, block: FieldMatcher.Builder.() -> Unit) {
      require(
        matchers.none {
          it.type in
            listOf(
              FillableFieldType.CurrentPassword,
              FillableFieldType.NewPassword,
            )
        }
      ) { "Every rule block can only have either genericPassword or {current,new}Password blocks" }
      matchers.add(
        AutofillRuleMatcher(
          type = FillableFieldType.GenericPassword,
          matcher = FieldMatcher.Builder().apply(block).build(),
          optional = optional,
          matchHidden = false
        )
      )
    }

    fun build(): AutofillRule {
      if (applyInSingleOriginMode) {
        require(matchers.none { it.matcher is PairOfFieldsMatcher }) {
          "Rules with applyInSingleOriginMode set to true must only match single fields"
        }
        require(matchers.filter { it.type != FillableFieldType.Username }.size <= 1) {
          "Rules with applyInSingleOriginMode set to true must only match at most one password field"
        }
        require(matchers.none { it.matchHidden }) {
          "Rules with applyInSingleOriginMode set to true must not fill into hidden fields"
        }
      }
      return AutofillRule(
          matchers,
          applyInSingleOriginMode,
          applyOnManualRequestOnly,
          name ?: "Rule #$ruleId"
        )
        .also { ruleId++ }
    }
  }

  fun match(
    allPassword: List<FormField>,
    allUsername: List<FormField>,
    allOtp: List<FormField>,
    singleOriginMode: Boolean,
    isManualRequest: Boolean
  ): AutofillScenario<FormField>? {
    if (singleOriginMode && !applyInSingleOriginMode) {
      logcat { "$name: Skipped in single origin mode" }
      return null
    }
    if (!isManualRequest && applyOnManualRequestOnly) {
      logcat { "$name: Skipped since not a manual request" }
      return null
    }
    logcat { "$name: Applying..." }
    val scenarioBuilder = AutofillScenario.Builder<FormField>()
    val alreadyMatched = mutableListOf<FormField>()
    for ((type, matcher, optional, matchHidden) in matchers) {
      val fieldsToMatchOn =
        when (type) {
          FillableFieldType.Username -> allUsername
          FillableFieldType.Otp -> allOtp
          else -> allPassword
        }.filter { matchHidden || it.isVisible }
      val matchResult =
        matcher.match(fieldsToMatchOn, alreadyMatched)
          ?: if (optional) {
            logcat { "$name: Skipping optional $type matcher" }
            continue
          } else {
            logcat { "$name: Required $type matcher didn't match; passing to next rule" }
            return null
          }
      logcat { "$name: Matched $type" }
      when (type) {
        FillableFieldType.Username -> {
          check(matchResult.size == 1 && scenarioBuilder.username == null)
          scenarioBuilder.username = matchResult.single()
          // Hidden username fields should be saved but not filled.
          scenarioBuilder.fillUsername = scenarioBuilder.username!!.isVisible == true
        }
        FillableFieldType.Otp -> {
          check(matchResult.size == 1 && scenarioBuilder.otp == null)
          scenarioBuilder.otp = matchResult.single()
        }
        FillableFieldType.CurrentPassword -> scenarioBuilder.currentPassword.addAll(matchResult)
        FillableFieldType.NewPassword -> scenarioBuilder.newPassword.addAll(matchResult)
        FillableFieldType.GenericPassword -> scenarioBuilder.genericPassword.addAll(matchResult)
      }
      alreadyMatched.addAll(matchResult)
    }
    return scenarioBuilder.build().takeIf { scenario ->
      scenario.passesOriginCheck(singleOriginMode = singleOriginMode).also { passed ->
        if (passed) {
          logcat { "$name: Detected scenario:\n$scenario" }
        } else {
          logcat(WARN) { "$name: Scenario failed origin check:\n$scenario" }
        }
      }
    }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
internal class AutofillStrategy private constructor(private val rules: List<AutofillRule>) {

  @AutofillDsl
  class Builder {

    private val rules: MutableList<AutofillRule> = mutableListOf()

    fun rule(
      applyInSingleOriginMode: Boolean = false,
      applyOnManualRequestOnly: Boolean = false,
      block: AutofillRule.Builder.() -> Unit
    ) {
      rules.add(
        AutofillRule.Builder(
            applyInSingleOriginMode = applyInSingleOriginMode,
            applyOnManualRequestOnly = applyOnManualRequestOnly
          )
          .apply(block)
          .build()
      )
    }

    fun build() = AutofillStrategy(rules)
  }

  fun match(
    fields: List<FormField>,
    singleOriginMode: Boolean,
    isManualRequest: Boolean
  ): AutofillScenario<FormField>? {
    val possiblePasswordFields = fields.filter { it.passwordCertainty >= CertaintyLevel.Possible }
    logcat { "Possible password fields: ${possiblePasswordFields.size}" }
    val possibleUsernameFields = fields.filter { it.usernameCertainty >= CertaintyLevel.Possible }
    logcat { "Possible username fields: ${possibleUsernameFields.size}" }
    val possibleOtpFields = fields.filter { it.otpCertainty >= CertaintyLevel.Possible }
    logcat { "Possible otp fields: ${possibleOtpFields.size}" }
    // Return the result of the first rule that matches
    logcat { "Rules: ${rules.size}" }
    for (rule in rules) {
      return rule.match(
        possiblePasswordFields,
        possibleUsernameFields,
        possibleOtpFields,
        singleOriginMode = singleOriginMode,
        isManualRequest = isManualRequest
      )
        ?: continue
    }
    return null
  }
}

internal fun strategy(block: AutofillStrategy.Builder.() -> Unit) =
  AutofillStrategy.Builder().apply(block).build()
