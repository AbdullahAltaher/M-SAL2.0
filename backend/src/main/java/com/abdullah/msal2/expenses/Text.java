package com.abdullah.msal2.expenses;

/**
 * Small text helpers that behave like their JavaScript versions in M-SAL.
 */
final class Text {

	private Text() {
	}

	/** Arabic-Indic {@code ٠-٩} and Persian {@code ۰-۹} digits to {@code 0-9} (R-GEN-04). */
	static String latinDigits(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= '٠' && c <= '٩') {
				out.append((char) ('0' + (c - '٠')));
			}
			else if (c >= '۰' && c <= '۹') {
				out.append((char) ('0' + (c - '۰')));
			}
			else {
				out.append(c);
			}
		}
		return out.toString();
	}

	/** JavaScript's {@code trim()}: also removes no-break spaces and the BOM, unlike {@code String.strip()}. */
	static String trim(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && isJsSpace(s.charAt(start))) {
			start++;
		}
		while (end > start && isJsSpace(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(start, end);
	}

	private static boolean isJsSpace(char c) {
		return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿';
	}
}
