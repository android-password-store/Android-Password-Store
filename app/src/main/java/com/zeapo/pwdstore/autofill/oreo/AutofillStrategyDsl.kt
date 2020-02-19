/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.os.Build
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.w

@DslMarker
annotation class AutofillDsl

@RequiresApi(Build.VERSION_CODES.O)
interface FieldMatcher {
    fun match(fields: List<FormField>, alreadyMatched: List<FormField>): List<FormField>?

    @AutofillDsl
    class Builder {
        private var takeSingle: (FormField.(List<FormField>) -> Boolean)? = null
        private val tieBreakersSingle: MutableList<FormField.(List<FormField>) -> Boolean> =
            mutableListOf()

        private var takePair: (Pair<FormField, FormField>.(List<FormField>) -> Boolean)? = null
        private var tieBreakersPair: MutableList<Pair<FormField, FormField>.(List<FormField>) -> Boolean> =
            mutableListOf()

        fun takeSingle(block: FormField.(alreadyMatched: List<FormField>) -> Boolean = { true }) {
            check(takeSingle == null && takePair == null) { "Every block can only have at most one take{Single,Pair} block" }
            takeSingle = block
        }

        fun breakTieOnSingle(block: FormField.(alreadyMatched: List<FormField>) -> Boolean) {
            check(takeSingle != null) { "Every block needs a takeSingle block before a breakTieOnSingle block" }
            check(takePair == null) { "takePair cannot be mixed with breakTieOnSingle" }
            tieBreakersSingle.add(block)
        }

        fun takePair(block: Pair<FormField, FormField>.(alreadyMatched: List<FormField>) -> Boolean = { true }) {
            check(takeSingle == null && takePair == null) { "Every block can only have at most one take{Single,Pair} block" }
            takePair = block
        }

        fun breakTieOnPair(block: Pair<FormField, FormField>.(alreadyMatched: List<FormField>) -> Boolean) {
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
class SingleFieldMatcher(
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
            check(takeSingle != null) { "Every block needs a takeSingle block before a breakTieOnSingle block" }
            tieBreakersSingle.add(block)
        }

        fun build() = SingleFieldMatcher(
            takeSingle
                ?: throw IllegalArgumentException("Every block needs a take{Single,Pair} block"),
            tieBreakersSingle
        )
    }

    override fun match(fields: List<FormField>, alreadyMatched: List<FormField>): List<FormField>? {
        return fields.minus(alreadyMatched).filter { take(it, alreadyMatched) }.let { contestants ->
            var current = contestants
            for (tieBreaker in tieBreakers) {
                // Successively filter matched fields via tie breakers...
                val new = current.filter { tieBreaker(it, alreadyMatched) }
                // skipping those tie breakers that are not satisfied for any remaining field...
                if (new.isEmpty()) continue
                // and return if the available options have been narrowed to a single field.
                if (new.size == 1) break
                current = new
            }
            listOf(current.singleOrNull() ?: return null)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class PairOfFieldsMatcher(
    private val take: (Pair<FormField, FormField>, List<FormField>) -> Boolean,
    private val tieBreakers: List<(Pair<FormField, FormField>, List<FormField>) -> Boolean>
) : FieldMatcher {

    override fun match(fields: List<FormField>, alreadyMatched: List<FormField>): List<FormField>? {
        return fields.minus(alreadyMatched).zipWithNext()
            .filter { it.first directlyPrecedes it.second }.filter { take(it, alreadyMatched) }
            .let { contestants ->
                var current = contestants
                for (tieBreaker in tieBreakers) {
                    val new = current.filter { tieBreaker(it, alreadyMatched) }
                    if (new.isEmpty()) continue
                    if (new.size == 1) break
                    current = new
                }
                current.singleOrNull()?.toList()
            }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class AutofillRule private constructor(
    private val matchers: List<AutofillRuleMatcher>,
    private val applyInSingleOriginMode: Boolean,
    private val name: String
) {

    data class AutofillRuleMatcher(
        val type: FillableFieldType,
        val matcher: FieldMatcher,
        val optional: Boolean
    )

    enum class FillableFieldType {
        Username, CurrentPassword, NewPassword, GenericPassword,
    }

    @AutofillDsl
    class Builder(private val applyInSingleOriginMode: Boolean) {
        companion object {
            private var ruleId = 1
        }

        private val matchers = mutableListOf<AutofillRuleMatcher>()
        var name: String? = null

        fun username(optional: Boolean = false, block: SingleFieldMatcher.Builder.() -> Unit) {
            require(matchers.none { it.type == FillableFieldType.Username }) { "Every rule block can only have at most one username block" }
            matchers.add(
                AutofillRuleMatcher(
                    type = FillableFieldType.Username,
                    matcher = SingleFieldMatcher.Builder().apply(block).build(),
                    optional = optional
                )
            )
        }

        fun currentPassword(optional: Boolean = false, block: FieldMatcher.Builder.() -> Unit) {
            require(matchers.none { it.type == FillableFieldType.GenericPassword }) { "Every rule block can only have either genericPassword or {current,new}Password blocks" }
            matchers.add(
                AutofillRuleMatcher(
                    type = FillableFieldType.CurrentPassword,
                    matcher = FieldMatcher.Builder().apply(block).build(),
                    optional = optional
                )
            )
        }

        fun newPassword(optional: Boolean = false, block: FieldMatcher.Builder.() -> Unit) {
            require(matchers.none { it.type == FillableFieldType.GenericPassword }) { "Every rule block can only have either genericPassword or {current,new}Password blocks" }
            matchers.add(
                AutofillRuleMatcher(
                    type = FillableFieldType.NewPassword,
                    matcher = FieldMatcher.Builder().apply(block).build(),
                    optional = optional
                )
            )
        }

        fun genericPassword(optional: Boolean = false, block: FieldMatcher.Builder.() -> Unit) {
            require(matchers.none {
                it.type in listOf(
                    FillableFieldType.CurrentPassword, FillableFieldType.NewPassword
                )
            }) { "Every rule block can only have either genericPassword or {current,new}Password blocks" }
            matchers.add(
                AutofillRuleMatcher(
                    type = FillableFieldType.GenericPassword,
                    matcher = FieldMatcher.Builder().apply(block).build(),
                    optional = optional
                )
            )
        }

        fun build(): AutofillRule {
            if (applyInSingleOriginMode) {
                require(matchers.none { it.matcher is PairOfFieldsMatcher }) { "Rules with applyInSingleOriginMode set to true must only match single fields" }
                require(matchers.filter { it.type != FillableFieldType.Username }.size <= 1) { "Rules with applyInSingleOriginMode set to true must only match at most one password field" }
            }
            return AutofillRule(
                matchers, applyInSingleOriginMode, name ?: "Rule #$ruleId"
            ).also { ruleId++ }
        }
    }

    fun apply(
        allPassword: List<FormField>,
        allUsername: List<FormField>,
        singleOriginMode: Boolean
    ): AutofillScenario<FormField>? {
        if (singleOriginMode && !applyInSingleOriginMode) {
            d { "$name: Skipped in single origin mode" }
            return null
        }
        val scenarioBuilder = AutofillScenario.Builder<FormField>()
        val alreadyMatched = mutableListOf<FormField>()
        for ((type, matcher, optional) in matchers) {
            val matchResult = when (type) {
                FillableFieldType.Username -> matcher.match(allUsername, alreadyMatched)
                else -> matcher.match(allPassword, alreadyMatched)
            } ?: if (optional) {
                d { "$name: Skipping optional $type matcher" }
                continue
            } else {
                d { "$name: Required $type matcher didn't match; passing to next rule" }
                return null
            }
            d { "$name: Matched $type" }
            when (type) {
                FillableFieldType.Username -> {
                    check(matchResult.size == 1 && scenarioBuilder.username == null)
                    scenarioBuilder.username = matchResult.single()
                    // E.g. hidden username fields can be saved but not filled.
                    scenarioBuilder.fillUsername = scenarioBuilder.username?.isFillable == true
                }
                FillableFieldType.CurrentPassword -> scenarioBuilder.currentPassword.addAll(
                    matchResult
                )
                FillableFieldType.NewPassword -> scenarioBuilder.newPassword.addAll(matchResult)
                FillableFieldType.GenericPassword -> scenarioBuilder.genericPassword.addAll(
                    matchResult
                )
            }
            alreadyMatched.addAll(matchResult)
        }
        return scenarioBuilder.build().takeIf { scenario ->
            scenario.passesOriginCheck(singleOriginMode = singleOriginMode).also { passed ->
                if (passed) {
                    d { "$name: Detected scenario:\n$scenario" }
                } else {
                    w { "$name: Scenario failed origin check:\n$scenario" }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class AutofillStrategy(private val rules: List<AutofillRule>) {

    @AutofillDsl
    class Builder {
        private val rules: MutableList<AutofillRule> = mutableListOf()

        fun rule(
            applyInSingleOriginMode: Boolean = false,
            block: AutofillRule.Builder.() -> Unit
        ) {
            rules.add(AutofillRule.Builder(applyInSingleOriginMode).apply(block).build())
        }

        fun build() = AutofillStrategy(rules)
    }

    fun apply(fields: List<FormField>, multiOriginSupport: Boolean): AutofillScenario<FormField>? {
        val possiblePasswordFields =
            fields.filter { it.passwordCertainty >= CertaintyLevel.Possible }
        d { "Possible password fields: ${possiblePasswordFields.size}" }
        val possibleUsernameFields =
            fields.filter { it.usernameCertainty >= CertaintyLevel.Possible }
        d { "Possible username fields: ${possibleUsernameFields.size}" }
        // Return the result of the first rule that matches
        d { "Rules: ${rules.size}" }
        for (rule in rules) {
            return rule.apply(possiblePasswordFields, possibleUsernameFields, multiOriginSupport)
                ?: continue
        }
        return null
    }
}

fun strategy(block: AutofillStrategy.Builder.() -> Unit) =
    AutofillStrategy.Builder().apply(block).build()
