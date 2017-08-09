package viettel.nfw.social.utils;

import org.jetbrains.annotations.Nullable;

/**
 * First version. Also see guava's Preconditions.
 *
 * @author pchks
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static <T> T checkNotNull(@Nullable T reference, @Nullable String argumentName) {
        if (reference == null) {
            throw new IllegalArgumentException(nullReferenceMessage(argumentName));
        }
        return reference;
    }

    public static void checkIsTrue(boolean expression, @Nullable String errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(assertionFailedMessage(errorMessage));
        }
    }

    public static void checkArgument(boolean expression, @Nullable String errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(assertionFailedMessage(errorMessage));
        }
    }

    public static void checkIsFalse(boolean expression, @Nullable String errorMessage) {
        if (expression) {
            throw new IllegalArgumentException(assertionFailedMessage(errorMessage));
        }
    }

    public static void checkState(boolean expression, @Nullable String errorMessage) {
        if (!expression) {
            throw new IllegalStateException(assertionFailedMessage(errorMessage));
        }
    }

    public static void assertStateNot(boolean expression, @Nullable String errorMessage) {
        if (expression) {
            throw new IllegalStateException(assertionFailedMessage(errorMessage));
        }
    }

    public static String checkStringNotEmpty(@Nullable String s, @Nullable String argumentName) {
        checkNotNull(s, argumentName);
        if (s.isEmpty()) {
            throw new IllegalArgumentException(emptyStringMessage(argumentName));
        }
        return s;
    }

    private static String nullReferenceMessage(@Nullable String argumentName) {
        return argumentName != null ? argumentName + " is null" : "argument is null";
    }

    private static String assertionFailedMessage(@Nullable String errorMessage) {
        return errorMessage != null ? errorMessage : "assertion failed";
    }

    private static String emptyStringMessage(@Nullable String argumentName) {
        return argumentName != null ? argumentName + " is an empty string" : "argument is an empty string";
    }

}
