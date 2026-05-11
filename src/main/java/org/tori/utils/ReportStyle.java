package org.tori.utils;

public class ReportStyle {
    
    public static final String BOLD_WHITE = "\033[1m\033[37m";

    public static final String BOLD_GREEN_SUCCESS = "\033[1m\033[32m";
    public static final String BOLD_RED_FAILURE = "\033[1m\033[31m";

    public static String boldWhite(String text) {
        return BOLD_WHITE + text + "\033[0m";
    }

    public static String boldGreenSuccess(String text) {
        return BOLD_GREEN_SUCCESS + text + "\033[0m";
    }

    public static String boldRedFailure(String text) {
        return BOLD_RED_FAILURE + text + "\033[0m";
    }

}