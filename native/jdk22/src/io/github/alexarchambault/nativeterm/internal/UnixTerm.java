package io.github.alexarchambault.nativeterm.internal;

import io.github.alexarchambault.nativeterm.TerminalSize;

import static io.github.alexarchambault.nativeterm.internal.CLibrary.*;

/**
 * Utilities to get the terminal size on Linux / Mac
 */
public final class UnixTerm {

    private UnixTerm() {}

    /**
     * Gets the terminal size on Linux / Mac
     * @param useStdout whether to use stdout or stderr
     * @return the terminal size
     */
    public static TerminalSize getSize(boolean useStdout) {
        int fd = useStdout ? 1 : 2;
        return getTerminalSize(fd);
    }

}
