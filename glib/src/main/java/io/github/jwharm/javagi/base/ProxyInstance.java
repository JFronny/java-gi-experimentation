package io.github.jwharm.javagi.base;

import java.lang.foreign.Addressable;

/**
 * Base type for a Java proxy object to an instance in native memory.
 */
public class ProxyInstance implements Proxy {

    private final Addressable address;

    /**
     * Create a new {@code ProxyInstance} object for an instance in native memory.
     * @param address the memory address of the instance
     */
    public ProxyInstance(Addressable address) {
        this.address = address;
    }

    /**
     * Get the memory address of the instance
     * @return the memory address of the instance
     */
    @Override
    public Addressable handle() {
        return address;
    }
}