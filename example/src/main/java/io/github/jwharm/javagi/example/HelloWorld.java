package io.github.jwharm.javagi.example;

import org.gtk.gtk.*;
import org.gtk.gio.ApplicationFlags;

public class HelloWorld {

    public void activate(org.gtk.gio.Application g_application) {
        var window = new ApplicationWindow(Application.castFrom(g_application));
        window.setTitle("Window");
        window.setDefaultSize(300, 200);
        var box = new Box(Orientation.VERTICAL, 0);
        box.setHalign(Align.CENTER);
        box.setValign(Align.CENTER);
        var button = Button.newWithLabel("Hello world!");
        button.onClicked((btn) -> window.close());
        box.append(button);
        window.setChild(box);
        window.show();
    }

    public HelloWorld(String[] args) {
        var app = new Application("org.gtk.example", ApplicationFlags.FLAGS_NONE);
        app.onActivate(this::activate);
        app.run(args.length, args);
    }

    public static void main(String[] args) {
        new HelloWorld(args);
    }
}
