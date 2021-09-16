/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ListInstance;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.Step;

/**
 * Parser for a sequence of {@link ApiPath}'s {@link Step}s.
 */
@NonNullByDefault
abstract class ApiPathParser {
    private static final class Fast extends ApiPathParser {
        private final String str;

        Fast(final String str) {
            this.str = requireNonNull(str);
        }

        @Override
        ImmutableList<Step> parse() throws ParseException {
            final var steps = ImmutableList.<Step>builder();
            int idx = 0;
            do {
                final int slash = str.indexOf('/', idx);
                if (slash == idx) {
                    throw new ParseException("Unexpected '/'", idx);
                }

                final int limit = slash != -1 ? slash : str.length();
                steps.add(parseStep(str, idx, limit));
                idx = limit + 1;
            } while (idx < str.length());

            return steps.build();
        }

        @Override
        String decodeValue(final int offset, final int limit) {
            return str.substring(offset, limit);
        }
    }

    private static final class Slow extends ApiPathParser {
        private final String str;

        Slow(final String str) {
            this.str = requireNonNull(str);
        }

        @Override
        // FIXME: deal with percents, somehow. Note we want to share code with the Fast version, if possible
        ImmutableList<Step> parse() throws ParseException {
            final var steps = ImmutableList.<Step>builder();
            int idx = 0;
            do {
                final int slash = str.indexOf('/', idx);
                if (slash == idx) {
                    throw new ParseException("Unexpected '/'", idx);
                }

                final int limit = slash != -1 ? slash : str.length();
                steps.add(parseStep(str, idx, limit));
                idx = limit + 1;
            } while (idx < str.length());

            return steps.build();
        }

        @Override
        String decodeValue(final int offset, final int limit) throws ParseException {
            int percent = str.indexOf('%', offset);
            if (percent == -1 || percent > limit) {
                return str.substring(offset, limit);
            }

            final StringBuilder sb = new StringBuilder(limit - offset);
            int idx = offset;
            while (true) {
                sb.append(str, idx, percent);
                idx = percent;

                // We have taken care of almost all escapes except for those we need for framing at this point
                final int b = parsePercent(str, idx, limit);
                if (isLateUnescape(b)) {
                    sb.append((char) b);
                } else {
                    throw new ParseException("Unexpected byte '" + b + "'", idx);
                }

                idx += 3;
                percent = str.indexOf('%', idx);
                if (percent == -1 || percent >= limit) {
                    return sb.append(str, idx, limit).toString();
                }
            }
        }
    }

    // FIXME: use these from YangNames
    private static final CharMatcher IDENTIFIER_START =
        CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z').or(CharMatcher.is('_'))).precomputed();
    private static final CharMatcher NOT_IDENTIFIER_PART =
        IDENTIFIER_START.or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("-.")).negate().precomputed();

    private ApiPathParser() {
        // Hidden on purpose
    }

    static ApiPathParser of(final String str) throws ParseException {
        // We are dealing with a raw request URI, which may contain percent-encoded spans. Dealing with them during
        // structural parsing is rather annoying, especially since they can be multi-byte sequences. Let's make a quick
        // check if there are any percent-encoded bytes and take the appropriate fast path.
        final int firstPercent = str.indexOf('%');
        if (firstPercent == -1) {
            return new Fast(str);
        }

        // Since UTF-8 does not allow 0x00-0x7F to occur anywhere but the first byte, we can safely percent-decode
        // everything except '%' (%25), ',' (%2C) and '/' (%47) and ',' (%2F) and . That peels all the multi-byte
        // decoding from the problem at hand.
        final int limit = str.length();
        final StringBuilder sb = new StringBuilder(limit);
        final Utf8Buffer buf = new Utf8Buffer();

        boolean noPercent = true;
        int nextPercent = firstPercent;
        int idx = 0;
        do {
            sb.append(str, idx, nextPercent);
            idx = nextPercent;

            final int b = parsePercent(str, idx, limit);
            final int nextIdx = idx + 3;
            if (isLateUnescape(b)) {
                buf.flushTo(sb, idx);
                sb.append(str, idx, nextIdx);
                noPercent = false;
            } else {
                buf.appendByte(b);
                // Lookahead: if the next is not an escape, we need to flush the buffer in preparation
                //            for the bulk append at the top of the loop.
                if (nextIdx != limit && str.charAt(nextIdx)  != '%') {
                    buf.flushTo(sb, idx);
                }
            }

            nextPercent = str.indexOf('%', nextIdx);
            idx = nextIdx;
        } while (nextPercent != -1);

        buf.flushTo(sb, idx);
        final String unescaped = sb.append(str, idx, limit).toString();
        return noPercent ? new Fast(unescaped) : new Slow(unescaped);
    }

    /**
     * Parse an {@link ApiPath}'s steps from a raw Request URI.
     *
     * @param str Request URI, with leading {@code {+restconf}} stripped.
     * @return A list of steps
     * @throws ParseException if the string cannot be parsed
     * @throws NullPointerException if {@code str} is {@code null}
     */
    abstract ImmutableList<Step> parse() throws ParseException;

    abstract String decodeValue(int offset, int limit) throws ParseException;

    final Step parseStep(final String str, final int offset, final int limit) throws ParseException {
        // Mandatory first identifier
        final String first = parseIdentifier(str, offset, limit);
        int idx = offset + first.length();
        if (idx == limit) {
            return new ApiIdentifier(null, first);
        }

        // Optional second identifier
        final String second;
        if (str.charAt(idx) == ':') {
            second = parseIdentifier(str, ++idx, limit);
            idx += second.length();

            if (idx == limit) {
                return new ApiIdentifier(first, second);
            }
        } else {
            second = null;
        }

        // Key values
        if (str.charAt(idx) != '=') {
            throw new ParseException("Expecting '='", idx);
        }
        final var keyValues = parseKeyValues(str, idx + 1, limit);
        return second != null ? new ListInstance(first, second, keyValues) : new ListInstance(null, first, keyValues);
    }

    private static String parseIdentifier(final String str, final int offset, final int limit)
            throws ParseException {
        if (!IDENTIFIER_START.matches(str.charAt(offset))) {
            throw new ParseException("Expecting [a-zA-Z_]", offset);
        }

        final int nonMatch = NOT_IDENTIFIER_PART.indexIn(str, offset + 1);
        return str.substring(offset, nonMatch != -1 && nonMatch < limit ? nonMatch : limit);
    }

    private ImmutableList<String> parseKeyValues(final String str, final int offset, final int limit)
            throws ParseException {
        final var builder = ImmutableList.<String>builder();
        int idx = offset;
        while (true) {
            // FIXME: deal with percents
            final int comma = str.indexOf(',', idx);
            if (comma == -1 || comma > limit) {
                builder.add(decodeValue(idx, limit));
                return builder.build();
            }

            builder.add(decodeValue(idx, comma));
            idx = comma + 1;
        }
    }

    private static int parsePercent(final String str, final int offset, final int limit) throws ParseException {
        if (limit - offset < 3) {
            throw new ParseException("Incomplete escape '" + str.substring(offset) + "'", offset);
        }
        return (byte) (parseHex(str, offset + 1) << 4 | parseHex(str, offset + 2));
    }

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

    private static boolean isLateUnescape(final int ch) {
        return ch == '%' || ch == ',' || ch == '/';
    }
}
