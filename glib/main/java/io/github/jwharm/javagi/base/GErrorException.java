package io.github.jwharm.javagi.base;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.gnome.glib.GError;
import org.jetbrains.annotations.ApiStatus;

import org.gnome.glib.Quark;

/**
 * A GErrorException is thrown when a GError is returned by native code.
 * See <a href="https://docs.gtk.org/glib/error-reporting.html">the Gtk documentation on 
 * error reporting</a> for details about GError.
 */
public class GErrorException extends Exception {

    private final Quark domain;
    private final int code;
    private final String message;

    // Dereference the GError instance from the pointer
    private static GError dereference(MemorySegment pointer) {
        return new GError(pointer.get(ValueLayout.ADDRESS, 0));
    }
    
    // Get the message from the GError instance (used by the GErrorException constructor)
    private static String readMessage(MemorySegment pointer) {
        return dereference(pointer).readMessage();
    }

    /**
     * Create a GErrorException from a GError memory segment that was
     * returned by a native function.
     * @param gerrorPtr Pointer to a GError in native memory
     */
    @ApiStatus.Internal
    public GErrorException(MemorySegment gerrorPtr) {
        super(readMessage(gerrorPtr));

        GError gerror = dereference(gerrorPtr);
        this.domain = gerror.readDomain();
        this.code = gerror.readCode();
        this.message = gerror.readMessage();
    }

    /**
     * Create a GErrorException that can be used to return a GError from a Java callback function to
     * native code. See <a href="https://docs.gtk.org/glib/error-reporting.html">the Gtk documentation
     * on error reporting</a> for details.
     * @param domain The GError error domain
     * @param code The GError error code
     * @param message The error message, printf-style formatted
     * @param args varargs parameters for message format
     */
    public GErrorException(Quark domain, int code, String message, java.lang.Object... args) {
        super(message);
        this.domain = domain;
        this.code = code;
        this.message = message == null ? null : message.formatted(args);
    }

    /**
     * Check if an error is set.
     * @param gerrorPtr pointer to a GError in native memory
     * @return true when an error was set on this pointer
     */
    public static boolean isErrorSet(MemorySegment gerrorPtr) {
        MemorySegment gerror = gerrorPtr.get(ValueLayout.ADDRESS, 0);
        return (! gerror.equals(MemorySegment.NULL));
    }

    /**
     * Get the error code
     * @return the code of the GError
     */
    public int getCode() {
        return code;
    }

    /**
     * Get the error domain
     * @return The domain of the GError
     */
    public Quark getDomain() {
        return domain;
    }
    
    /**
     * Get a new GError instance with the domain, code and message of this GErrorException
     * @return a new GError instance
     */
    public GError getGError() {
        return new GError(domain, code, message);
    }
}