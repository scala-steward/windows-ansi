// Initially adapted from https://github.com/jline/jline3/blob/114e9a8f86102245ed9e2e642603f97e11ac962b/terminal-ffm/src/main/java/org/jline/terminal/impl/ffm/CLibrary.java

/*
 * Copyright (c) 2022-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package io.github.alexarchambault.nativeterm.internal;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.foreign.MemoryLayout.PathElement;

import io.github.alexarchambault.nativeterm.TerminalSize;

@SuppressWarnings("restricted")
class CLibrary {

    static VarHandle lookupVarHandle(PathElement... element) {
        VarHandle h = winsize.LAYOUT.varHandle(element);

        // the last parameter of the VarHandle is additional offset, hardcode zero:
        h = MethodHandles.insertCoordinates(h, h.coordinateTypes().size() - 1, 0L);

        return h;
    }

    // Window sizes.
    // @see <a href="http://man7.org/linux/man-pages/man4/tty_ioctl.4.html">IOCTL_TTY(2) man-page</a>
    static class winsize {
        static final GroupLayout LAYOUT;
        private static final VarHandle ws_col;
        private static final VarHandle ws_row;

        static {
            LAYOUT = MemoryLayout.structLayout(
                    ValueLayout.JAVA_SHORT.withName("ws_row"),
                    ValueLayout.JAVA_SHORT.withName("ws_col"),
                    ValueLayout.JAVA_SHORT,
                    ValueLayout.JAVA_SHORT);
            ws_row = lookupVarHandle(MemoryLayout.PathElement.groupElement("ws_row"));
            ws_col = lookupVarHandle(MemoryLayout.PathElement.groupElement("ws_col"));
        }

        private final java.lang.foreign.MemorySegment seg;

        winsize() {
            seg = java.lang.foreign.Arena.ofAuto().allocate(LAYOUT);
        }

        java.lang.foreign.MemorySegment segment() {
            return seg;
        }

        short ws_col() {
            return (short) ws_col.get(seg);
        }

        short ws_row() {
            return (short) ws_row.get(seg);
        }
    }

    static MethodHandle ioctl;
    static MethodHandle isatty;
    static MethodHandle tcsetattr;
    static MethodHandle tcgetattr;
    static MethodHandle ttyname_r;

    static {
        // methods
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.loaderLookup().or(linker.defaultLookup());
        // https://man7.org/linux/man-pages/man2/ioctl.2.html
        ioctl = linker.downcallHandle(
                lookup.find("ioctl").get(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
                Linker.Option.firstVariadicArg(2));
        // https://www.man7.org/linux/man-pages/man3/isatty.3.html
        isatty = linker.downcallHandle(
                lookup.find("isatty").get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        // https://man7.org/linux/man-pages/man3/tcsetattr.3p.html
        tcsetattr = linker.downcallHandle(
                lookup.find("tcsetattr").get(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        // https://man7.org/linux/man-pages/man3/tcgetattr.3p.html
        tcgetattr = linker.downcallHandle(
                lookup.find("tcgetattr").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        // https://man7.org/linux/man-pages/man3/ttyname.3.html
        ttyname_r = linker.downcallHandle(
                lookup.find("ttyname_r").get(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    }

    static TerminalSize getTerminalSize(int fd) {
        try {
            winsize ws = new winsize();
            int res = (int) ioctl.invoke(fd, (long) TIOCGWINSZ, ws.segment());
            return TerminalSize.of(ws.ws_col(), ws.ws_row());
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call ioctl(TIOCGWINSZ)", e);
        }
    }

    // CONSTANTS

    private static final int TIOCGWINSZ;

    static {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Linux")) {
            String arch = System.getProperty("os.arch");
            boolean isMipsPpcOrSparc = arch.equals("mips")
                    || arch.equals("mips64")
                    || arch.equals("mipsel")
                    || arch.equals("mips64el")
                    || arch.startsWith("ppc")
                    || arch.startsWith("sparc");
            TIOCGWINSZ = isMipsPpcOrSparc ? 0x40087468 : 0x00005413;
        } else if (osName.startsWith("Solaris") || osName.startsWith("SunOS")) {
            int _TIOC = ('T' << 8);
            TIOCGWINSZ = (_TIOC | 104);
        } else if (osName.startsWith("Mac") || osName.startsWith("Darwin")) {
            TIOCGWINSZ = 0x40087468;
        } else if (osName.startsWith("FreeBSD")) {
            TIOCGWINSZ = 0x40087468;
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
