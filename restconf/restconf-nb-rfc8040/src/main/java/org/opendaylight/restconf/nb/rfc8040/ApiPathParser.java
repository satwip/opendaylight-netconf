/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ListInstance;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.Step;

/**
 * Parser for a sequence of {@link ApiPath}'s {@link Step}s.
 */
final class ApiPathParser {
    // FIXME: use these from YangNames
    private static final CharMatcher IDENTIFIER_START =
        CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z').or(CharMatcher.is('_'))).precomputed();
    private static final CharMatcher NOT_IDENTIFIER_PART =
        IDENTIFIER_START.or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("-.")).negate().precomputed();

    private final Builder<Step> steps = ImmutableList.builder();

    /*
     * State tracking for creating substrings:
     *
     * Usually we copy spans 'src', in which case subStart captures 'start' argument to String.substring(...).
     * If we encounter a percent escape we need to interpret as part of the string, we start building the string in
     * subBuilder -- in which case subStart is set to -1.
     *
     * Note that StringBuilder is lazily-instantiated, as we have no percents at all
     */
    private int subStart;
    private StringBuilder subBuilder;

    // Lazily-allocated when we need to decode UTF-8. Since we touch this only when we are not expecting
    private Utf8Buffer buf;

    // the offset of the character returned from last peekBasicLatin()
    private int nextOffset;

    private ApiPathParser() {
        // Hidden on purpose
    }

    static @NonNull ImmutableList<Step> parseSteps(final String str) throws ParseException {
        return new ApiPathParser().parse(str);
    }

    // Grammar:
    //   steps : step ("/" step)*
    private @NonNull ImmutableList<Step> parse(final String str) throws ParseException {
        int idx = 0;
        do {
            final int slash = str.indexOf('/', idx);
            final int limit = slash != -1 ? slash : str.length();
            final int next = parseStep(str, idx, limit);
            verify(next == limit, "Unconsumed bytes: %s next %s limit", next, limit);
            idx = next + 1;
        } while (idx < str.length());

        return steps.build();
    }

    // Grammar:
    //   step : identifier (":" identifier)? ("=" key-value ("," key-value)*)?
    private int parseStep(final String str, final int offset, final int limit) throws ParseException {
        int idx = startIdentifier(str, offset, limit);
        while (idx < limit) {
            final char ch = peekBasicLatin(str, idx, limit);
            if (ch == ':') {
                return parseStep(endSub(str, idx), str, nextOffset, limit);
            } else if (ch == '=') {
                return parseStep(null, endSub(str, idx), str, nextOffset, limit);
            }
            idx = continueIdentifer(idx, ch);
        }

        steps.add(new ApiIdentifier(null, endSub(str, idx)));
        return idx;
    }

    // Starting at second identifier
    private int parseStep(final @Nullable String module, final String str, final int offset, final int limit)
            throws ParseException {
        int idx = startIdentifier(str, offset, limit);
        while (idx < limit) {
            final char ch = peekBasicLatin(str, idx, limit);
            if (ch == '=') {
                return parseStep(module, endSub(str, idx), str, nextOffset, limit);
            }
            idx = continueIdentifer(idx, ch);
        }

        steps.add(new ApiIdentifier(module, endSub(str, idx)));
        return idx;
    }

    // Starting at first key-value
    private int parseStep(final @Nullable String module, final @NonNull String identifier,
            final String str, final int offset, final int limit) throws ParseException {
        final var values = ImmutableList.<String>builder();

        startSub(offset);
        int idx = offset;
        while (idx < limit) {
            final char ch = str.charAt(idx);
            if (ch == ',') {
                values.add(endSub(str, idx));
                startSub(++idx);
            } else if (ch != '%') {
                append(ch);
                idx++;
            } else {
                // Save current string content and capture current index for reporting
                final var sb = flushSub(str, idx);
                final int errorOffset = idx;

                var utf = buf;
                if (utf == null) {
                    buf = utf = new Utf8Buffer();
                }

                do {
                    utf.appendByte(parsePercent(str, idx, limit));
                    idx += 3;
                } while (idx < limit && str.charAt(idx) == '%');

                utf.flushTo(sb, errorOffset);
            }
        }

        steps.add(new ListInstance(module, identifier, values.add(endSub(str, idx)).build()));
        return idx;
    }

    private int startIdentifier(final String str, final int offset, final int limit) throws ParseException {
        startSub(offset);
        final char ch = peekBasicLatin(str, offset, limit);
        if (!IDENTIFIER_START.matches(ch)) {
            throw new ParseException("Expecting [a-zA-Z_], not '" + ch + "'", offset);
        }
        append(ch);
        return nextOffset;
    }

    private int continueIdentifer(final int offset, final char ch) throws ParseException {
        if (NOT_IDENTIFIER_PART.matches(ch)) {
            throw new ParseException("Expecting [a-zA-Z_.-], not '" + ch + "'", offset);
        }
        append(ch);
        return nextOffset;
    }

    // Assert current character comes from the Basic Latin block, i.e. 00-7F.
    // Callers are expected to pick up 'nextIdx' to resume parsing at the next character
    private char peekBasicLatin(final String str, final int offset, final int limit) throws ParseException {
        final char ch = str.charAt(offset);
        if (ch == '%') {
            final byte b = parsePercent(str, offset, limit);
            if (b < 0) {
                throw new ParseException("Expecting %00-%7F, not " + str.substring(offset, limit), offset);
            }

            flushSub(str, offset);
            nextOffset = offset + 3;
            return (char) b;
        }

        if (ch < 0 || ch > 127) {
            throw new ParseException("Unexpected character '" + ch + "'", offset);
        }
        nextOffset = offset + 1;
        return ch;
    }

    private void startSub(final int offset) {
        subStart = offset;
    }

    private void append(final char ch) {
        // We are not reusing string, append the char, otherwise
        if (subStart == -1) {
            verifyNotNull(subBuilder).append(ch);
        }
    }

    private @NonNull String endSub(final String str, final int end) {
        return subStart != -1 ? str.substring(subStart, end) : verifyNotNull(subBuilder).toString();
    }

    private @NonNull StringBuilder flushSub(final String str, final int end) {
        var sb = subBuilder;
        if (sb == null) {
            subBuilder = sb = new StringBuilder();
        }
        if (subStart != -1) {
            sb.setLength(0);
            sb.append(str, subStart, end);
            subStart = -1;
        }
        return sb;
    }

    private static byte parsePercent(final String str, final int offset, final int limit) throws ParseException {
        if (limit - offset < 3) {
            throw new ParseException("Incomplete escape '" + str.substring(offset, limit) + "'", offset);
        }
        return (byte) (parseHex(str, offset + 1) << 4 | parseHex(str, offset + 2));
    }

    // FIXME: Replace with HexFormat.fromHexDigit(str.charAt(offset)) when we have JDK17+
    private static int parseHex(final String str, final int offset) throws ParseException {
        final char ch = str.charAt(offset);
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }

        final int zero;
        if (ch >= 'a' && ch <= 'f') {
            zero = 'a';
        } else if (ch >= 'A' && ch <= 'F') {
            zero = 'A';
        } else {
            throw new ParseException("Invalid escape character '" + ch + "'", offset);
        }

        return ch - zero + 10;
    }
}
