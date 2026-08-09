package com.wangzhu.immortalersdelightfix;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Last-resort fallback used only if Log4j-core reflection is unavailable. */
final class StdoutFallbackFilter {
    private static volatile boolean installed;

    private StdoutFallbackFilter() {
    }

    static synchronized void install() {
        if (installed || System.out instanceof FilteringPrintStream) {
            return;
        }
        System.setOut(new FilteringPrintStream(System.out));
        installed = true;
    }

    private static final class FilteringPrintStream extends PrintStream {
        private final PrintStream delegate;

        private FilteringPrintStream(PrintStream delegate) {
            super(delegate, true, StandardCharsets.UTF_8);
            this.delegate = delegate;
        }

        @Override
        public void println(String value) {
            if (!SpamMessages.shouldSuppress(value)) {
                delegate.println(value);
            }
        }

        @Override
        public void println(Object value) {
            String text = String.valueOf(value);
            if (!SpamMessages.shouldSuppress(text)) {
                delegate.println(value);
            }
        }
    }
}
