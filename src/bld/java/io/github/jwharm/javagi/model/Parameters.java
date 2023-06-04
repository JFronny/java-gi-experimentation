package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.SourceWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Parameters extends GirElement {

    public final List<Parameter> parameterList = new ArrayList<>();

    public Parameters(GirElement parent) {
        super(parent);
    }

    public void generateJavaParameters(SourceWriter writer, boolean pointerForArray) throws IOException {
        int counter = 0;
        for (Parameter p : parameterList) {
            if (p.isInstanceParameter() || p.isUserDataParameter()
                    || p.isDestroyNotifyParameter() || p.isArrayLengthParameter()) {
                continue;
            }
            if (counter++ > 0) {
                writer.write(", ");
            }
            p.writeTypeAndName(writer, pointerForArray);
        }
    }

    public void generateJavaParameterTypes(SourceWriter writer, boolean pointerForArray, boolean writeAnnotations) throws IOException {
        int counter = 0;
        for (Parameter p : parameterList) {
            if (p.isInstanceParameter() || p.isUserDataParameter()
                    || p.isDestroyNotifyParameter() || p.isArrayLengthParameter()) {
                continue;
            }
            if (counter++ > 0) {
                writer.write(", ");
            }
            p.writeType(writer, pointerForArray, writeAnnotations);
        }
    }

    public void generateJavaParameterNames(SourceWriter writer) throws IOException {
        int counter = 0;
        for (Parameter p : parameterList) {
            if (p.isInstanceParameter() || p.isUserDataParameter()
                    || p.isDestroyNotifyParameter() || p.isArrayLengthParameter()) {
                continue;
            }
            if (counter++ > 0) {
                writer.write(", ");
            }
            writer.write(p.varargs ? "varargs" : p.name);
        }
    }

    public void marshalJavaToNative(SourceWriter writer, String throws_) throws IOException {
        boolean first = true;

        for (Parameter p : parameterList) {
            if (! first) {
                writer.write(",\n");
                writer.write("        ");
            }
            first = false;

            // Generate null-check. But don't null-check parameters that are hidden from the Java API, or primitive values
            if (p.checkNull()) {
                writer.write("(MemorySegment) (" + (p.varargs ? "varargs" : p.name) + " == null ? MemorySegment.NULL : ");
            }

            // this
            if (p.isInstanceParameter()) {
                writer.write("handle()");

            // user_data
            } else if (p.isUserDataParameter() || p.isDestroyNotifyParameter()) {
                writer.write("MemorySegment.NULL");

            // Varargs
            } else if (p.varargs) {
                writer.write("varargs");

            // Preprocessing statement
            } else if (p.isOutParameter() || (p.isAliasForPrimitive() && p.type.isPointer())) {
                writer.write("_" + p.name + "Pointer");

            // Custom interop
            } else {
                p.marshalJavaToNative(writer, p.name, false, false);
            }

            // Closing parentheses for null-check
            if (p.checkNull()) {
                writer.write(")");
            }
        }

        // GError
        if (throws_ != null) {
            writer.write(",\n");
            writer.write("        _gerror");
        }
    }

    public void marshalNativeToJava(SourceWriter writer) throws IOException {
        boolean first = true;

        for (Parameter p : parameterList) {
            if (p.isUserDataParameter() || p.isDestroyNotifyParameter() || p.isArrayLengthParameter()) {
                continue;
            }

            if (! first) {
                writer.write(",\n");
                writer.write("        ");
            }
            first = false;

            if (p.type != null && p.type.isAliasForPrimitive() && p.type.isPointer()) {
                writer.write("_" + p.name + "Alias");
                continue;
            }

            if (p.isOutParameter()) {
                writer.write("_" + p.name + "Out");
                continue;
            }

            p.marshalNativeToJava(writer, p.name, true);
        }
    }

    /**
     * Generate preprocessing statements for all parameters
     * @param writer The source code file writer
     * @throws IOException Thrown when an error occurs while writing
     */
    public void generatePreprocessing(SourceWriter writer) throws IOException {
        // First the regular (non-array) out-parameters. These could include an out-parameter with
        // the length of an array out-parameter, so we have to process these first.
        for (Parameter p : parameterList) {
            if (p.array == null) {
                p.generatePreprocessing(writer);
            }
        }
        // Secondly, process the array out parameters
        for (Parameter p : parameterList) {
            if (p.array != null) {
                p.generatePreprocessing(writer);
            }
        }
    }
    
    /**
     * Generate postprocessing statements for all parameters
     * @param writer The source code file writer
     * @throws IOException Thrown when an error occurs while writing
     */
    public void generatePostprocessing(SourceWriter writer) throws IOException {
        // First the regular (non-array) out-parameters. These could include an out-parameter with 
        // the length of an array out-parameter, so we have to process these first.
        for (Parameter p : parameterList) {
            if (p.array == null) {
                p.generatePostprocessing(writer);
            }
        }
        // Secondly, process the array out parameters
        for (Parameter p : parameterList) {
            if (p.array != null) {
                p.generatePostprocessing(writer);
            }
        }
    }

    /**
     * Generate preprocessing statements for all parameters in an upcall
     * @param writer The source code file writer
     * @throws IOException Thrown when an error occurs while writing
     */
    public void generateUpcallPreprocessing(SourceWriter writer) throws IOException {
        // First the regular (non-array) out-parameters. These could include an out-parameter with
        // the length of an array out-parameter, so we have to process these first.
        for (Parameter p : parameterList) {
            if (p.array == null) {
                p.generateUpcallPreprocessing(writer);
            }
        }
        // Secondly, process the array out parameters
        for (Parameter p : parameterList) {
            if (p.array != null) {
                p.generateUpcallPreprocessing(writer);
            }
        }
    }

    /**
     * Generate postprocessing statements for all parameters in an upcall
     * @param writer The source code file writer
     * @throws IOException Thrown when an error occurs while writing
     */
    public void generateUpcallPostprocessing(SourceWriter writer) throws IOException {
        for (Parameter p : parameterList) {
            p.generateUpcallPostprocessing(writer);
        }
    }
}