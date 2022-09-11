package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.BindingsGenerator;
import io.github.jwharm.javagi.generator.Conversions;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class Callback extends RegisteredType implements CallableType {

    public ReturnValue returnValue;
    public Parameters parameters;

    public Callback(GirElement parent, String name) {
        super(parent, name, null);
    }

    public boolean isSafeToBind() {
        return ((parameters != null)
                && parameters.parameterList.stream().anyMatch(Parameter::isUserDataParameter)
        );
    }

    public void generate(Writer writer) throws IOException {

        generateFunctionalInterface(writer);
        generateStaticCallback();
    }

    private void generateFunctionalInterface(Writer writer) throws IOException {
        generatePackageDeclaration(writer);
        generateJavadoc(writer);
        writer.write("@FunctionalInterface\n");
        writer.write("public interface " + javaName + " {\n");
        writer.write("        ");
        if (returnValue.type == null) {
            writer.write("void");
        } else if (returnValue.type.isBitfield()) {
            writer.write("int");
        } else {
            writer.write(returnValue.type.qualifiedJavaType);
        }
        writer.write(" on" + javaName + "(");
        if (parameters != null) {
            int counter = 0;
            for (Parameter p : parameters.parameterList) {
                if (! (p.isUserDataParameter() || p.isDestroyNotify())) {
                    if (counter > 0) {
                        writer.write(", ");
                    }
                    p.generateTypeAndName(writer);
                    counter++;
                }
            }
        }
        writer.write(");\n");
        writer.write("}\n");
    }

    // Generate the static callback method, that will run the handler method.
    private void generateStaticCallback() throws IOException {
        StringWriter sw = new StringWriter();

        sw.write("    public static ");
        if (returnValue.type == null) {
            sw.write("void");
        } else if (returnValue.type.isBitfield()) {
            sw.write("int");
        } else {
            sw.write(returnValue.type.qualifiedJavaType);
        }
        sw.write(" cb" + javaName + "(");

        String dataParamName = "";
        if (parameters != null) {
            int counter = 0;
            for (Parameter p : parameters.parameterList) {
                if (counter > 0) {
                    sw.write(", ");
                }
                sw.write (Conversions.toPanamaJavaType(p.type) + " ");
                sw.write(Conversions.toLowerCaseJavaName(p.name));
                if (p.isUserDataParameter()) {
                    dataParamName = Conversions.toLowerCaseJavaName(p.name);
                }
                counter++;
            }
        }
        sw.write(") {\n");

        sw.write("        int hash = " + dataParamName + ".get(C_INT, 0);\n");
        sw.write("        var handler = (" + javaName + ") Interop.signalRegistry.get(hash);\n");
        sw.write("        ");
        if ((returnValue.type != null) && (! "void".equals(returnValue.type.simpleJavaType))) {
            sw.write("return ");
        }
        sw.write("handler.on" + javaName + "(");

        if (parameters != null) {
            int counter = 0;
            for (Parameter p : parameters.parameterList) {
                if (p.isUserDataParameter() || p.isDestroyNotify()) {
                    continue;
                }
                if (counter > 0) {
                    sw.write(", ");
                }
                p.generateCallbackInterop(sw);
                counter++;
            }
        }
        sw.write(");\n");

        sw.write("    }\n");
        sw.write("    \n");
        BindingsGenerator.signalCallbackFunctions.append(sw);
    }

    @Override
    public Parameters getParameters() {
        return parameters;
    }

    @Override
    public void setParameters(Parameters ps) {
        this.parameters = ps;
    }

    @Override
    public ReturnValue getReturnValue() {
        return returnValue;
    }

    @Override
    public void setReturnValue(ReturnValue rv) {
        this.returnValue = rv;
    }
}
