/* Java-GI - Java language bindings for GObject-Introspection-based libraries
 * Copyright (C) 2022-2024 the Java-GI developers
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package io.github.jwharm.javagi.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.github.jwharm.javagi.configuration.ClassNames;
import io.github.jwharm.javagi.gir.*;
import io.github.jwharm.javagi.util.Conversions;
import io.github.jwharm.javagi.util.PartialStatement;

import javax.lang.model.element.Modifier;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.List;

import static io.github.jwharm.javagi.util.Conversions.*;

public class ClosureGenerator {

    private final Callable closure;
    private final CallableGenerator generator;

    public ClosureGenerator(Callable closure) {
        this.closure = closure;
        this.generator = new CallableGenerator(closure);
    }

    TypeSpec generateFunctionalInterface() {
        String name = getName();
        TypeSpec.Builder builder = TypeSpec.interfaceBuilder(name)
                .addJavadoc("Functional interface declaration of the {@code $L} callback.\n", name)
                .addAnnotation(FunctionalInterface.class)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(generateRunMethod())
                .addMethod(generateUpcallMethod(name, "upcall", "run"))
                .addMethod(generateToCallbackMethod(name));
        if (closure.deprecated()) builder.addAnnotation(Deprecated.class);
        return builder.build();
    }

    private String getName() {
        String name = Conversions.toJavaSimpleType(closure.name(), closure.namespace());
        if (closure instanceof Callback cb && cb.parent() instanceof Field)
            name += "Callback";
        return name;
    }

    MethodSpec generateRunMethod() {
        MethodSpec.Builder run = MethodSpec.methodBuilder("run")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(new TypedValueGenerator(closure.returnValue()).getType());

        if (closure.deprecated())
            run.addAnnotation(Deprecated.class);
        if (closure.infoElements().doc() != null)
            run.addJavadoc(new DocGenerator(closure.infoElements().doc()).generate());
        if (closure.throws_())
            run.addException(ClassName.get("org.gnome.glib", "GError"));

        generator.generateMethodParameters(run);
        return run.build();
    }

    MethodSpec generateUpcallMethod(String methodName, String name, String methodToInvoke) {
        boolean returnsVoid = closure.returnValue().anyType().isVoid();

        MethodSpec.Builder upcall = MethodSpec.methodBuilder(name)
                .returns(returnsVoid ? TypeName.VOID : getCarrierTypeName(closure.returnValue().anyType()));

        if (methodToInvoke.equals("run"))
            upcall.addJavadoc("The {@code upcall} method is called from native code. The parameters \n")
                    .addJavadoc("are marshaled and {@link #run} is executed.");

        if (methodToInvoke.equals("run"))
            upcall.addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
        else
            upcall.addModifiers(Modifier.PRIVATE);

        if (closure instanceof Signal signal) {
            String paramName = "source" + toCamelCase(signal.parent().name(), true);
            upcall.addParameter(TypeName.get(MemorySegment.class), paramName);
        }

        if (closure.parameters() != null)
            for (Parameter p : closure.parameters().parameters())
                upcall.addParameter(getCarrierTypeName(p.anyType()), toJavaIdentifier(p.name()));

        if (closure.throws_())
            upcall.addParameter(MemorySegment.class, "_gerrorPointer");

        // Generate try-catch block for reflection calls
        if (methodToInvoke.endsWith("invoke"))
            upcall.beginControlFlow("try");

        if (closure.allocatesMemory())
            upcall.beginControlFlow("try (var _arena = $T.ofConfined())", Arena.class);

        if (closure.parameters() != null)
            closure.parameters().parameters().stream()
                    // Array parameters may refer to other parameters for their length, so they must be processed last.
                    .sorted((Comparator.comparing(p -> p.anyType() instanceof Array)))
                    .forEach(p -> new PreprocessingGenerator(p).generateUpcall(upcall));

        if (closure.throws_())
            upcall.beginControlFlow("try");

        PartialStatement invoke = new PartialStatement();
        if (!returnsVoid) {
            invoke.add("var _result = ");
            if (methodToInvoke.endsWith("invoke"))
                invoke.add("(" + new TypedValueGenerator(closure.returnValue()).getType() + ") ");
        }
        invoke.add(methodToInvoke).add("(").add(marshalParameters(methodToInvoke)).add(");\n");
        upcall.addNamedCode(invoke.format(), invoke.arguments());

        if (closure.parameters() != null)
            for (var p : closure.parameters().parameters())
                new PostprocessingGenerator(p).generateUpcall(upcall);

        if ((!returnsVoid)
                && "java.lang.foreign.MemorySegment".equals(getCarrierTypeString(closure.returnValue().anyType()))
                && (!closure.returnValue().notNull()))
            upcall.addStatement("if (_result == null) return $T.NULL", MemorySegment.class);

        if (closure.returnValue().anyType() instanceof Type t && t.isGObject()
                && closure.returnValue().transferOwnership() == TransferOwnership.FULL)
            upcall.addStatement("_result.ref()");

        if (!returnsVoid) {
            var stmt = new TypedValueGenerator(closure.returnValue()).marshalJavaToNative("_result");
            upcall.addNamedCode("return " + stmt.format(), stmt.arguments());
        }

        if (closure.throws_()) {
            if (methodToInvoke.endsWith("invoke")) {
                upcall.nextControlFlow("catch ($T _ite)", InvocationTargetException.class);
                upcall.beginControlFlow("if (_ite.getCause() instanceof $T _ge)", ClassNames.GERROR_EXCEPTION);
            } else {
                upcall.nextControlFlow("catch ($T _ge)", ClassNames.GERROR_EXCEPTION);
            }
            upcall.addStatement("$1T _gerror = new $1T(_ge.getDomain(), _ge.getCode(), _ge.getMessage())",
                    ClassName.get("org.gnome.glib", "GError"));
            upcall.addStatement("_gerrorPointer.set($T.ADDRESS, 0, _gerror.handle())", ValueLayout.class);
            if (!returnsVoid)
                returnNull(upcall);
            if (methodToInvoke.endsWith("invoke")) {
                upcall.nextControlFlow("else");
                upcall.addStatement("throw _ite");
                upcall.endControlFlow();
            }
            upcall.endControlFlow();
        }

        if (closure.allocatesMemory())
            upcall.endControlFlow();

        // Close try-catch block for reflection calls
        if (methodToInvoke.endsWith("invoke")) {
            upcall.nextControlFlow("catch ($T ite)", InvocationTargetException.class);
            upcall.addStatement("$T.log($T.LOG_DOMAIN, $T.LEVEL_WARNING, ite.getCause().toString() + $S + $L)",
                    ClassName.get("org.gnome.glib", "GLib"), ClassNames.CONSTANTS,
                    ClassName.get("org.gnome.glib", "LogLevelFlags"), " in ", methodName);
            if (!returnsVoid)
                returnNull(upcall);
            upcall.nextControlFlow("catch (Exception e)");
            upcall.addStatement("throw new RuntimeException(e)");
            upcall.endControlFlow();
        }

        return upcall.build();
    }

    private PartialStatement marshalParameters(String methodToInvoke) {
        PartialStatement stmt = new PartialStatement();

        if (closure.parameters() == null)
            return stmt;

        List<Parameter> parameters = closure.parameters().parameters();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter p = parameters.get(i);
            boolean last = i == parameters.size() - 1;

            if (p.isUserDataParameter() || p.isDestroyNotifyParameter() || p.isArrayLengthParameter())
                continue;

            if (i > 0) stmt.add(", ");

            if (p.anyType() instanceof Type t && t.get() instanceof Alias a && a.type().isPrimitive()) {
                stmt.add("_" + toJavaIdentifier(p.name()) + "Alias");
                continue;
            }

            if (p.isOutParameter()) {
                stmt.add("_" + toJavaIdentifier(p.name()) + "Out");
                continue;
            }

            // Invoking a method using reflection calls Method.invoke() which is variadic.
            // If the last parameter is an array, that will trigger a compiler warning, because
            // it is unsure if the array should be treated as varargs or not
            if (last && methodToInvoke.endsWith("invoke"))
                if (p.anyType() instanceof Array || (p.anyType() instanceof Type t && t.isActuallyAnArray()))
                    stmt.add("(Object) ");

            stmt.add(new TypedValueGenerator(p).marshalNativeToJava(toJavaIdentifier(p.name()), true));
        }
        return stmt;
    }

    private void returnNull(MethodSpec.Builder upcall) {
        if (closure.returnValue().anyType() instanceof Type type) {
            var target = type.get();
            if ((type.isPrimitive() || target instanceof FlaggedType || (target instanceof Alias a && a.type().isPrimitive()))
                    && (! type.isPointer())) {
                upcall.addStatement("return 0");
                return;
            }
        }
        upcall.addStatement("return $T.NULL", MemorySegment.class);
    }

    MethodSpec generateToCallbackMethod(String className) {
        MethodSpec.Builder toCallback = MethodSpec.methodBuilder("toCallback")
                .addJavadoc("""
                        Creates a native function pointer to the {@link #upcall} method.
                        @return the native function pointer
                        """)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addParameter(Arena.class, "arena")
                .returns(MemorySegment.class);
        generator.generateFunctionDescriptor(toCallback);
        toCallback.addStatement("$T _handle = $T.upcallHandle($T.lookup(), $L.class, _fdesc)",
                MethodHandle.class, ClassNames.INTEROP, MethodHandles.class, className);
        toCallback.addStatement("return $T.nativeLinker().upcallStub(_handle.bindTo(this), _fdesc, arena)", Linker.class);
        return toCallback.build();
    }
}
