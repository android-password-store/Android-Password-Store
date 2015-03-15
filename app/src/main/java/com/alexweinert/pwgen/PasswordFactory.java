package com.alexweinert.pwgen;

public abstract class PasswordFactory {
    protected enum TriValueBoolean {
        MUST, MAY, MUSTNOT
    }

    /** The random number generator used for picking random characters from a pool */
    protected IRandom randomGenerator;

    /** True if the generated password may include ambiguous characters */
    protected TriValueBoolean includeAmbiguous;

    /** True if the generated password may include vowels */
    protected TriValueBoolean includeVowels;

    /** True if the generated password must include symbols */
    protected TriValueBoolean includeSymbols;
    /** True if the generated password must include digits */
    protected TriValueBoolean includeDigits;
    /** True if the generated password must include uppercase characters */
    protected TriValueBoolean includeUppercase;
    protected TriValueBoolean includeLowercase;

    /** Pool from which to pick digits */
    protected final String pw_digits = "0123456789";
    /** Pool from which to pick uppercase characters */
    protected final String pw_uppers = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** Pool from which to pick lowercase characters */
    protected final String pw_lowers = "abcdefghijklmnopqrstuvwxyz";
    /** Pool from which to pick symbols */
    protected final String pw_symbols = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    /** Pool from which to pick ambiguous characters */
    protected final String pw_ambiguous = "B8G6I1l0OQDS5Z2";
    /** Pool from which to pick vowels */
    protected final String pw_vowels = "01aeiouyAEIOUY";

    protected PasswordFactory(IRandom randomGenerator, TriValueBoolean includeAmbiguous, TriValueBoolean includeVowels,
            TriValueBoolean includeSymbols, TriValueBoolean includeDigits, TriValueBoolean includeUppercase,
            TriValueBoolean includeLowercase) {
        this.randomGenerator = randomGenerator;
        this.includeAmbiguous = includeAmbiguous;
        this.includeVowels = includeVowels;
        this.includeSymbols = includeSymbols;
        this.includeDigits = includeDigits;
        this.includeUppercase = includeUppercase;
        this.includeLowercase = includeLowercase;
    }

    public static class Builder {
        private IRandom randomGenerator;

        private TriValueBoolean includeLowercase;
        private TriValueBoolean includeUppercase;

        private TriValueBoolean includeAmbiguous;
        private TriValueBoolean includeVowels;

        private TriValueBoolean includeSymbols;
        private TriValueBoolean includeDigits;

        private boolean mustBePronouncable = true;

        public Builder(IRandom randomGenerator) {
            this.randomGenerator = randomGenerator;
        }

        public PasswordFactory create() {
            PasswordFactory returnValue;
            if (this.mustBePronouncable) {
                returnValue = new PronouncablePasswordFactory(this.randomGenerator, this.includeAmbiguous,
                        this.includeVowels, this.includeSymbols, this.includeDigits, this.includeUppercase,
                        this.includeLowercase);
            } else {
                returnValue = new RandomPasswordFactory(this.randomGenerator, this.includeAmbiguous,
                        this.includeVowels, this.includeSymbols, this.includeDigits, this.includeUppercase,
                        this.includeLowercase);
            }

            return returnValue;
        }

        public Builder mustIncludeLowercase() {
            this.includeLowercase = TriValueBoolean.MUST;
            return this;
        }

        public Builder mayIncludeLowercase() {
            this.includeLowercase = TriValueBoolean.MAY;
            return this;
        }

        public Builder mustNotIncludeLowercase() {
            this.includeLowercase = TriValueBoolean.MUSTNOT;
            return this;
        }

        public Builder mustIncludeUppercase() {
            this.includeUppercase = TriValueBoolean.MUST;
            return this;
        }

        public Builder mayIncludeUppercase() {
            this.includeUppercase = TriValueBoolean.MAY;
            return this;
        }

        public Builder mustNotIncludeUppercase() {
            this.includeUppercase = TriValueBoolean.MUSTNOT;
            return this;
        }

        public Builder mustIncludeAmbiguous() {
            this.includeAmbiguous = TriValueBoolean.MUST;
            return this;
        }

        public Builder mayIncludeAmbiguous() {
            this.includeAmbiguous = TriValueBoolean.MAY;
            return this;
        }

        public Builder mustNotIncludeAmbiguous() {
            this.includeAmbiguous = TriValueBoolean.MUSTNOT;
            return this;
        }

        public Builder mustIncludeVowels() {
            this.includeVowels = TriValueBoolean.MUST;
            return this;
        }

        public Builder mayIncludeVowels() {
            this.includeVowels = TriValueBoolean.MAY;
            return this;
        }

        public Builder mustNotIncludeVowels() {
            this.includeVowels = TriValueBoolean.MUSTNOT;
            return this;
        }

        public Builder mustIncludeSymbols() {
            this.includeSymbols = TriValueBoolean.MUST;
            return this;
        }

        public Builder mayIncludeSymbols() {
            this.includeSymbols = TriValueBoolean.MAY;
            return this;
        }

        public Builder mustNotIncludeSymbols() {
            this.includeSymbols = TriValueBoolean.MUSTNOT;
            return this;
        }

        public Builder mustIncludeDigits() {
            this.includeDigits = TriValueBoolean.MUST;
            return this;
        }

        public Builder mayIncludeDigits() {
            this.includeDigits = TriValueBoolean.MAY;
            return this;
        }

        public Builder mustNotIncludeDigits() {
            this.includeDigits = TriValueBoolean.MUSTNOT;
            return this;
        }

        public Builder mustBePronouncable() {
            this.mustBePronouncable = true;
            return this;
        }

        public Builder mightNotBePronouncable() {
            this.mustBePronouncable = false;
            return this;
        }
    }

    public abstract String getPassword(int length);
}
