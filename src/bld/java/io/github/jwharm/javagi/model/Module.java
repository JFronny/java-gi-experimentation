package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.Platform;

import java.util.HashMap;
import java.util.Map;

public class Module {
    /**
     * Map to find repositories by their name
     */
    public final Map<String, Repository> repositories = new HashMap<>();

    /**
     * Map to find java packages by namespaces
     */
    public final Map<String, String> nsLookupTable = new HashMap<>();

    /**
     * Map to find elements by their {@code c:identifier} attribute
     */
    public final Map<String, GirElement> cIdentifierLookupTable = new HashMap<>();

    /**
     * Map to find types by their {@code c:type} attribute
     */
    public final Map<String, RegisteredType> cTypeLookupTable = new HashMap<>();

    /**
     * Map to find parent types by a types qualified name
     */
    public final Map<String, String> superLookupTable = new HashMap<>();

    public final Platform platform;

    public Module(Platform platform) {
        this.platform = platform;
    }

    /**
     * Loop through all type references in all repositories, find the
     * actual type instance in the parsed GI tree, and save a reference
     * to that GirElement.
     */
    public void link() {

        // Link all type references to the accompanying types
        for (Repository repository : repositories.values()) {
            GirElement element = repository;
            while (element != null) {

                if ((element instanceof Type t)
                        && (t.name != null)
                        && (! t.isPrimitive)
                        && (! t.name.equals("none"))
                        && (! t.name.equals("utf8"))
                        && (! t.name.equals("gpointer")
                        && (! t.name.equals("gconstpointer")))) {

                    Repository r = repositories.get(t.girNamespace);
                    if (r != null) {
                        t.girElementInstance = r.namespace.registeredTypeMap.get(t.name);
                        if (t.girElementInstance != null) {
                            t.girElementType = t.girElementInstance.getClass().getSimpleName();
                        }
                    }
                    // Redo the initialization, now that all repositories have loaded.
                    t.init(t.qualifiedName);
                }

                // Link length-parameters to the corresponding arrays
                if (element instanceof Array array) {
                    if (array.length != null && array.parent instanceof Parameter p) {
                        Parameter lp = p.getParameterAt(array.length);
                        lp.linkedArray = array;
                    }
                }

                element = element.next;
            }
        }

        // Create lookup tables
        createIdLookupTable();
        createCTypeLookupTable();
    }

    /**
     * Flag methods with a `va_list` argument so they will not be generated.
     * As of JDK 21, va_list arguments will be unsupported.
     */
    public void flagVaListFunctions() {
        for (Repository repository : repositories.values()) {
            // Methods, virtual methods and functions
            for (RegisteredType rt : repository.namespace.registeredTypeMap.values()) {
                for (Method method : rt.methodList) {
                    flagVaListFunction(method);
                }
                for (Method method : rt.virtualMethodList) {
                    flagVaListFunction(method);
                }
                for (Method method : rt.functionList) {
                    flagVaListFunction(method);
                }
            }
            // Global functions
            for (Method method : repository.namespace.functionList) {
                flagVaListFunction(method);
            }
        }
    }

    private void flagVaListFunction(Method method) {
        if (method.parameters != null) {
            for (Parameter parameter : method.parameters.parameterList) {
                if (parameter.type != null && "va_list".equals(parameter.type.cType)) {
                    method.skip = true;
                    break;
                }
            }
        }
    }

    /**
     * Update {@code cIdentifierLookupTable} with current {@code repositoriesLookupTable}
     */
    public void createIdLookupTable() {
        cIdentifierLookupTable.clear();
        for (Repository repository : repositories.values()) {
            GirElement element = repository;
            while (element != null) {
                if (element instanceof Method m) {
                    cIdentifierLookupTable.put(m.cIdentifier, m);
                } else if (element instanceof Member m) {
                    cIdentifierLookupTable.put(m.cIdentifier, m);
                }
                element = element.next;
            }
        }
    }

    /**
     * Update {@code cTypeLookupTable} with current {@code repositoriesLookupTable}
     */
    public void createCTypeLookupTable() {
        cTypeLookupTable.clear();
        for (Repository gir : repositories.values()) {
            for (RegisteredType rt : gir.namespace.registeredTypeMap.values()) {
                cTypeLookupTable.put(rt.cType, rt);
            }
        }
    }
}