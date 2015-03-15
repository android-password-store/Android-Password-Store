package com.alexweinert.pwgen;

public class PronouncablePasswordFactory extends PasswordFactory {

    private class pwElement {
        String str;
        boolean isConsonant;
        boolean isDiphtong;
        boolean notFirst;

        private pwElement(String str, boolean isConsonant, boolean isDiphtong, boolean notFirst) {
            this.str = str;
            this.isConsonant = isConsonant;
            this.isDiphtong = isDiphtong;
            this.notFirst = notFirst;
        }
    }

    private final pwElement[] elements = { new pwElement("a", false, false, false),// { "a", VOWEL },
            new pwElement("ae", false, true, false), // { "ae", VOWEL | DIPTHONG },
            new pwElement("ah", false, true, false), // { "ah", VOWEL | DIPTHONG },
            new pwElement("ai", false, true, false), // { "ai", VOWEL | DIPTHONG },
            new pwElement("b", true, false, false), // { "b", CONSONANT },
            new pwElement("c", true, false, false), // { "c", CONSONANT },
            new pwElement("ch", true, true, false), // { "ch", CONSONANT | DIPTHONG },
            new pwElement("d", true, false, false), // { "d", CONSONANT },
            new pwElement("e", false, false, false), // { "e", VOWEL },
            new pwElement("ee", false, true, false), // { "ee", VOWEL | DIPTHONG },
            new pwElement("ei", false, true, false), // { "ei", VOWEL | DIPTHONG },
            new pwElement("f", true, false, false), // { "f", CONSONANT },
            new pwElement("g", true, false, false), // { "g", CONSONANT },
            new pwElement("gh", true, true, true), // { "gh", CONSONANT | DIPTHONG | NOT_FIRST },
            new pwElement("h", true, false, false), // { "h", CONSONANT },
            new pwElement("i", false, false, false), // { "i", VOWEL },
            new pwElement("ie", false, true, false), // { "ie", VOWEL | DIPTHONG },
            new pwElement("j", true, false, false), // { "j", CONSONANT },
            new pwElement("k", true, false, false), // { "k", CONSONANT },
            new pwElement("l", true, false, false), // { "l", CONSONANT },
            new pwElement("m", true, false, false), // { "m", CONSONANT },
            new pwElement("n", true, false, false), // { "n", CONSONANT },
            new pwElement("ng", true, true, true), // { "ng", CONSONANT | DIPTHONG | NOT_FIRST },
            new pwElement("o", false, false, false), // { "o", VOWEL },
            new pwElement("oh", false, true, false), // { "oh", VOWEL | DIPTHONG },
            new pwElement("oo", false, true, false), // { "oo", VOWEL | DIPTHONG},
            new pwElement("p", true, false, false), // { "p", CONSONANT },
            new pwElement("ph", true, true, false), // { "ph", CONSONANT | DIPTHONG },
            new pwElement("qu", true, true, false), // { "qu", CONSONANT | DIPTHONG},
            new pwElement("r", true, false, false), // { "r", CONSONANT },
            new pwElement("s", true, false, false), // { "s", CONSONANT },
            new pwElement("sh", true, true, false), // { "sh", CONSONANT | DIPTHONG},
            new pwElement("t", true, false, false), // { "t", CONSONANT },
            new pwElement("th", true, true, false), // { "th", CONSONANT | DIPTHONG},
            new pwElement("u", false, false, false), // { "u", VOWEL },
            new pwElement("v", true, false, false), // { "v", CONSONANT },
            new pwElement("w", true, false, false), // { "w", CONSONANT },
            new pwElement("x", true, false, false), // { "x", CONSONANT },
            new pwElement("y", true, false, false), // { "y", CONSONANT },
            new pwElement("z", true, false, false), // { "z", CONSONANT }
    };

    protected PronouncablePasswordFactory(IRandom randomGenerator, TriValueBoolean mayIncludeAmbiguous,
            TriValueBoolean mayIncludeVowels, TriValueBoolean mustIncludeSymbols, TriValueBoolean mustIncludeDigits,
            TriValueBoolean mustIncludeUppercase, TriValueBoolean includeLowercase) {
        super(randomGenerator, mayIncludeAmbiguous, mayIncludeVowels, mustIncludeSymbols, mustIncludeDigits,
                mustIncludeUppercase, includeLowercase);
    }

    @Override
    public String getPassword(int length) {
        String password = null;
        do {
            StringBuilder passwordBuilder = new StringBuilder();
            boolean isFirst = true;
            boolean shouldBeConsonant = (this.randomGenerator.getRandomInt(2) == 0);
            pwElement previous = null;

            while (passwordBuilder.length() < length) {
                pwElement candidateElement = this.getAdmissableElement(shouldBeConsonant);
                // Make sure that we do not pick an inadmissable element as first element
                if (isFirst && candidateElement.notFirst) {
                    continue;
                }
                // Don't allow a vowel followed by a vowel/diphtong
                if (previous != null && !previous.isConsonant && !candidateElement.isConsonant
                        && candidateElement.isDiphtong) {
                    continue;
                }
                // Don't allow us to overflow the buffer
                if (candidateElement.str.length() > (length - passwordBuilder.length())) {
                    continue;
                }

                String toAdd = candidateElement.str;
                if (this.includeUppercase == TriValueBoolean.MUST && (isFirst || candidateElement.isConsonant)
                        && this.randomGenerator.getRandomInt(10) < 2) {
                    char[] toAddCharArray = candidateElement.str.toCharArray();
                    toAddCharArray[0] = Character.toUpperCase(toAddCharArray[0]);
                    toAdd = String.valueOf(toAddCharArray);
                }

                // Ok, we found an element which matches our criteria, let's do it!
                passwordBuilder.append(toAdd);

                // If we are at the correct length, do not continue
                if (passwordBuilder.length() >= length) {
                    break;
                }

                if (this.includeDigits == TriValueBoolean.MUST) {
                    if (!isFirst && this.randomGenerator.getRandomInt(10) < 3) {
                        char digit = this.getDigit();
                        passwordBuilder.append(digit);
                        // Restart the generation
                        isFirst = true;
                        previous = null;
                        shouldBeConsonant = (this.randomGenerator.getRandomInt(2) == 0);
                        continue;
                    }
                }

                if (this.includeSymbols == TriValueBoolean.MUST) {
                    if (!isFirst && this.randomGenerator.getRandomInt(10) < 2) {
                        char symbol = this.getSymbol();
                        passwordBuilder.append(symbol);
                    }
                }

                if (shouldBeConsonant) {
                    shouldBeConsonant = false;
                } else {
                    if ((previous != null && !previous.isConsonant) || candidateElement.isDiphtong
                            || this.randomGenerator.getRandomInt(10) < 3) {
                        shouldBeConsonant = true;
                    } else {
                        shouldBeConsonant = false;
                    }
                }

                previous = candidateElement;
                isFirst = false;
            }

            password = passwordBuilder.toString();
        } while (!this.isAdmissablePassword(password));

        return password;
    }

    private char getDigit() {
        char returnValue;
        do {
            returnValue = this.pw_digits.charAt(this.randomGenerator.getRandomInt(this.pw_digits.length()));
            // If this may include ambiguous characters, one iteration is enough
        } while (this.includeAmbiguous != TriValueBoolean.MUSTNOT ? false : this.pw_ambiguous.contains(String
                .valueOf(returnValue)));
        return returnValue;
    }

    private char getSymbol() {
        char returnValue;
        do {
            returnValue = this.pw_symbols.charAt(this.randomGenerator.getRandomInt(this.pw_symbols.length()));
            // If this may include ambiguous characters, one iteration is enough
        } while (this.includeAmbiguous != TriValueBoolean.MUSTNOT ? false : this.pw_ambiguous.contains(String
                .valueOf(returnValue)));
        return returnValue;
    }

    private pwElement getAdmissableElement(boolean shouldBeConsonant) {
        pwElement current = null;
        do {
            current = this.elements[this.randomGenerator.getRandomInt(this.elements.length)];
        } while (shouldBeConsonant ? !current.isConsonant : current.isConsonant);
        return current;
    }

    private boolean isAdmissablePassword(String password) {
        boolean includesUppercase = false, includesDigits = false, includesSymbols = false;
        for (char character : password.toCharArray()) {
            CharSequence currentCharSequence = String.valueOf(character);
            if (this.pw_uppers.contains(currentCharSequence)) {
                includesUppercase = true;
            }
            if (this.pw_digits.contains(currentCharSequence)) {
                includesDigits = true;
            }
            if (this.pw_symbols.contains(currentCharSequence)) {
                includesSymbols = true;
            }
        }

        if (this.includeUppercase == TriValueBoolean.MUST && !includesUppercase) {
            return false;
        }
        if (this.includeDigits == TriValueBoolean.MUST && !includesDigits) {
            return false;
        }
        if (this.includeSymbols == TriValueBoolean.MUST && !includesSymbols) {
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        PasswordFactory factory = (new Builder(new RandomGenerator())).mustIncludeUppercase().mustIncludeDigits()
                .mustBePronouncable().create();
        for (int i = 0; i < 20; ++i) {
            System.out.println(factory.getPassword(8));
        }
    }
}
