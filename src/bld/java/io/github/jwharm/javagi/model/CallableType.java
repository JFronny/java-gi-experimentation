package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.Conversions;
import io.github.jwharm.javagi.generator.SourceWriter;

import java.io.IOException;

public interface CallableType {

    Parameters getParameters();
    void setParameters(Parameters ps);

    ReturnValue getReturnValue();
    void setReturnValue(ReturnValue rv);

    Doc getDoc();

    String getThrows();

    /**
     * This function is used to determine if memory is allocated to marshal
     * the call parameters or return value.
     * @return true when memory is allocated (for an array, a native string,
     *         an out parameter, or a pointer to a primitive value)
     */
    default boolean allocatesMemory() {
        if (getParameters() != null) {
            if (getParameters().parameterList.stream().anyMatch(
                    p -> (p.array != null)
                            || (p.type != null && "java.lang.String".equals(p.type.qualifiedJavaType))
                            || (p.isOutParameter())
                            || (p.isAliasForPrimitive() && p.type.isPointer())
            )) {
                return true;
            }
        }
        ReturnValue rv = getReturnValue();
        return getThrows() != null
                || rv.array != null
                || (this instanceof Closure && rv.type != null && "java.lang.String".equals(rv.type.qualifiedJavaType));
    }

    default boolean generateFunctionDescriptor(SourceWriter writer) throws IOException {
        ReturnValue returnValue = getReturnValue();
        Parameters parameters = getParameters();
        boolean isVoid = returnValue.type == null || "void".equals(returnValue.type.simpleJavaType);

        boolean first = true;
        boolean varargs = false;
        writer.write("FunctionDescriptor.");

        // Return value
        if (isVoid) {
            writer.write("ofVoid(");
        } else {
            writer.write("of(");
            writer.write(Conversions.getValueLayout(returnValue.type));

            // Unbounded valuelayout for Strings, otherwise we cannot read an utf8 string from the returned pointer
            if (returnValue.type != null && "java.lang.String".equals(returnValue.type.qualifiedJavaType)) {
                writer.write(".asUnbounded()");
            }

            if (parameters != null || this instanceof Signal) {
                writer.write(", ");
            }
        }

        // For signals, add the pointer to the source
        if (this instanceof Signal) {
            writer.write("ValueLayout.ADDRESS");
            if (parameters != null) {
                writer.write(", ");
            }
        }

        // Parameters
        if (parameters != null) {
            for (Parameter p : parameters.parameterList) {
                if (p.varargs) {
                    varargs = true;
                    break;
                }
                if (! first) {
                    writer.write(", ");
                }
                first = false;
                writer.write(Conversions.getValueLayout(p.type));

                // Unbounded valuelayout for String callback/out parameters,
                // otherwise we cannot read the utf8 string to create a Java String
                if ((this instanceof Closure || p.isOutParameter())
                        && p.type != null
                        && "java.lang.String".equals(p.type.qualifiedJavaType)) {
                    writer.write(".asUnbounded()");
                }
            }
        }

        // **GError parameter
        if (getThrows() != null) {
            if (! first) {
                writer.write(", ");
            }
            writer.write("ValueLayout.ADDRESS");
        }

        writer.write(")");
        return varargs;
    }
}