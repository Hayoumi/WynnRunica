package com.WynnRunica;

public class Epstein {

    public static int levenshtein(String a, String b) {

        int dlinaA = a.length();
        int dlinaB = b.length();

        int[] prev = new int[dlinaB + 1];
        int[] curr = new int[dlinaB + 1];

        for (int j = 0; j <= dlinaB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= dlinaA; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);

            for (int j = 1; j <= dlinaB; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int insert = curr[j - 1] + 1;
                int delete = prev[j] + 1;
                int replace = prev[j - 1] + cost;
                curr[j] = Math.min(Math.min(insert, delete), replace);
            }

            int[] tmp = prev;
            prev = curr;
            curr = tmp;

        }
        return prev[dlinaB];
    }

    public static double similarity(String a, String b) {
        return 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
    }

}
