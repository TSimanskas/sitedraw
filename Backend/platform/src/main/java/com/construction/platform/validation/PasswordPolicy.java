package com.construction.platform.validation;

public final class PasswordPolicy {

    public static final String MESSAGE =
            "must be at least 8 characters and include an uppercase letter, a number, and a symbol";

    private PasswordPolicy() {
    }

    public static boolean isAcceptable(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean uppercase = false;
        boolean number = false;
        boolean symbol = false;
        for (int offset = 0; offset < password.length(); ) {
            int codePoint = password.codePointAt(offset);
            if (Character.isUpperCase(codePoint)) {
                uppercase = true;
            } else if (Character.isDigit(codePoint)) {
                number = true;
            } else if (!Character.isLetter(codePoint)) {
                symbol = true;
            }
            offset += Character.charCount(codePoint);
        }
        return uppercase && number && symbol;
    }
}
