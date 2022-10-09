# java-gi

**java-gi** is a very experimental, **work-in-progress** tool for generating GObject-Introspection bindings for Java. The generated bindings use the Panama foreign function & memory access API (JEP 424) and `jextract` for accessing native resources, and add wrapper classes based on GObject-Introspection to offer an easy API to use with Java. The included build scripts generate bindings for GTK4 and LibAdwaita.

Java-gi bindings are automatically generated from both the C header files (using `jextract` from project Panama) and GI data (gir files). Panama allows for relatively easy interoperability with native code, but the jextract-generated binding classes are very difficult to use directly. C functions like `gtk_button_set_icon_name(GtkButton* button, const char* icon_name)` are mapped to a static Java method `gtk_button_set_icon_name(MemoryAddress button, MemoryAddress icon_name)`. Using GObject-Introspection, it is possible to generate a wrapper API that includes "proxy" classes that call the jextract-generated functions with automatically marshalled parameters and return values, for example `button.setIconName(iconName)`. Java-gi tries to achieve this.

## Prerequisites

- First, download and install [JDK 19](https://jdk.java.net/19/) and [JExtract](https://jdk.java.net/jextract/). There are no dependencies on any other Java libraries.
- Make sure that `javac` and `java` from JDK 19 and `jextract` are in your `PATH`; this is expected by the build script.
- Install development header files and GObject-introspection (gir) files of the library you want to generate bindings for. For example, in Fedora, to generate bindings for GTK4 and LibAdwaita, execute: `sudo dnf install gtk4-devel glib-devel libadwaita-devel gobject-introspection-devel`

## How to create bindings

The included shell scripts generate bindings for GTK4 and LibAdwaita:
- First run `java-gi.sh` to generate a jar with jextract classes for GTK4 and LibAdwaita. The script uses `pkg-config` to set the correct include and library paths, and a file `gtk.h` which contains the necessary includes.
- The file `input.xml` contains the paths to the GIR input files (on my system they are in `/usr/share/gir-1.0`) and the preferred Java package names. Check that all GIR files listed in `input.xml` actually exist on your system. Install the missing `-devel` packages or update the XML file where necessary.
- Edit `java-gi-gtk4.sh` and update the output folder parameter.
- Next, run `java-gi-gtk4.sh` to parse gir files and generate the bindings (java source files) for GTK4, LibAdwaita and their dependencies.
- Use the generated sources however you like.
- The script `clean.sh` removes the generated bindings that `java-gi-gtk4.sh` creates.

## What the bindings look like

A "Hello world" example can be found in [`example`](https://github.com/jwharm/java-gi/blob/main/example/src/main/java/io/github/jwharm/javagi/example/HelloWorld.java)

Because the Panama foreign function API is still in preview status, to run the above application, be sure to add the `--enable-preview` command-line parameter when running `javac` and `java`.
