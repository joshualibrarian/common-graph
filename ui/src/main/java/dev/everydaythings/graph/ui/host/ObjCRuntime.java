package dev.everydaythings.graph.ui.host;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA bindings for the Objective-C runtime (libobjc.dylib).
 *
 * <p>This provides the three fundamental operations of Objective-C:
 * <ul>
 *   <li>{@code objc_getClass} — look up a class by name (e.g., "NSStatusBar")</li>
 *   <li>{@code sel_registerName} — look up a selector by name (e.g., "systemStatusBar")</li>
 *   <li>{@code objc_msgSend} — send a message to an object (i.e., call a method)</li>
 * </ul>
 *
 * <p>All of Objective-C boils down to message sends. {@code [NSStatusBar systemStatusBar]}
 * is just {@code objc_msgSend(NSStatusBar_class, sel_systemStatusBar)}.
 *
 * <p><b>ARM64 ABI note:</b> {@code objc_msgSend} is declared variadic in the C header,
 * but on ARM64 it acts as a trampoline with the SAME calling convention as the target
 * method. JNA's interface-based mapping uses libffi variadic convention (doubles on stack),
 * which crashes because the target method expects doubles in floating-point registers (D0).
 * We use JNA <b>direct mapping</b> ({@link Native#register}) for {@code objc_msgSend},
 * which generates JNI code that casts the function pointer to the correct type — putting
 * doubles in D0 where ARM64 expects them.
 *
 * <p>Usage:
 * <pre>{@code
 * Pointer statusBar = ObjCRuntime.send(ObjCRuntime.cls("NSStatusBar"), "systemStatusBar");
 * Pointer statusItem = ObjCRuntime.send(statusBar, "statusItemWithLength:", -1.0);
 * }</pre>
 */
class ObjCRuntime {

    // =========================================================================
    // Direct-mapped objc_msgSend (ARM64-safe)
    // =========================================================================

    /**
     * Direct-mapped {@code objc_msgSend} overloads.
     *
     * <p>JNA direct mapping generates JNI code that calls the native function
     * with the correct ABI for each parameter signature. This avoids the
     * ARM64 variadic calling convention issue where libffi puts doubles on
     * the stack instead of in floating-point registers.
     *
     * <p>Each overload covers a specific parameter pattern used by our Cocoa calls.
     * Add new overloads as needed for new parameter combinations.
     */
    static class MsgSend {
        static { Native.register("objc"); }

        // () — no args
        static native Pointer objc_msgSend(Pointer self, Pointer sel);

        // (id) — one pointer arg
        static native Pointer objc_msgSend(Pointer self, Pointer sel, Pointer arg);

        // (double) — one double arg (e.g., statusItemWithLength:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel, double arg);

        // (BOOL) — one boolean arg (e.g., setVisible:, setTemplate:, setWantsLayer:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel, boolean arg);

        // (long) — one long arg (e.g., setBehavior:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel, long arg);

        // (String) — one string arg (e.g., initWithUTF8String:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel, String arg);

        // (double, double) — two doubles (e.g., setContentSize: takes NSSize)
        static native Pointer objc_msgSend(Pointer self, Pointer sel, double a, double b);

        // (double, double, double, double) — four doubles (e.g., initWithFrame: takes NSRect)
        static native Pointer objc_msgSend(Pointer self, Pointer sel,
                                           double a, double b, double c, double d);

        // (id, id) — two pointer args (e.g., setContentViewController:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel, Pointer a, Pointer b);

        // (id, id, id) — three pointer args (e.g., initWithTitle:action:keyEquivalent:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel,
                                           Pointer a, Pointer b, Pointer c);

        // (double, double, double, double, id, long) — NSRect + id + long
        // (e.g., showRelativeToRect:ofView:preferredEdge:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel,
                                           double a, double b, double c, double d,
                                           Pointer view, long edge);

        // (byte[], long) — bytes + length (e.g., initWithBytes:length:)
        static native Pointer objc_msgSend(Pointer self, Pointer sel, byte[] bytes, long length);
    }

    // =========================================================================
    // Interface-mapped runtime functions (non-msgSend)
    // =========================================================================

    /**
     * The Objective-C runtime C interface (non-msgSend functions).
     */
    interface ObjC extends Library {
        ObjC INSTANCE = Native.load("objc", ObjC.class);

        /** Look up an Objective-C class by name. */
        Pointer objc_getClass(String name);

        /** Look up or register a selector (method name) by name. */
        Pointer sel_registerName(String name);

        /** Allocate a new Objective-C class pair (for creating classes at runtime). */
        Pointer objc_allocateClassPair(Pointer superclass, String name, int extraBytes);

        /** Register a dynamically created class. */
        void objc_registerClassPair(Pointer cls);

        /** Add a method to a class. */
        boolean class_addMethod(Pointer cls, Pointer selector, Callback imp, String types);
    }

    /**
     * The Foundation framework for NSString and other basics.
     */
    interface Foundation extends Library {
        Foundation INSTANCE = Native.load("Foundation", Foundation.class);
    }

    /**
     * The AppKit framework for NSStatusBar, NSMenu, NSImage, etc.
     */
    interface AppKit extends Library {
        AppKit INSTANCE = Native.load("AppKit", AppKit.class);
    }

    // Eagerly load frameworks so their classes are available
    static void ensureLoaded() {
        // Touching INSTANCE causes JNA to dlopen() the framework,
        // making its Objective-C classes available via objc_getClass.
        var f = Foundation.INSTANCE;
        var a = AppKit.INSTANCE;
    }

    // =========================================================================
    // Convenience methods
    // =========================================================================

    /** Look up an Objective-C class by name. */
    static Pointer cls(String name) {
        Pointer result = ObjC.INSTANCE.objc_getClass(name);
        if (result == null) {
            throw new IllegalStateException("Objective-C class not found: " + name);
        }
        return result;
    }

    /** Look up a selector by name. */
    static Pointer sel(String name) {
        return ObjC.INSTANCE.sel_registerName(name);
    }

    /** Send a message with no arguments. */
    static Pointer send(Pointer target, String selector) {
        return MsgSend.objc_msgSend(target, sel(selector));
    }

    /** Send a message with one pointer argument. */
    static Pointer send(Pointer target, String selector, Pointer arg) {
        return MsgSend.objc_msgSend(target, sel(selector), arg);
    }

    /** Send a message with one double argument. */
    static Pointer send(Pointer target, String selector, double arg) {
        return MsgSend.objc_msgSend(target, sel(selector), arg);
    }

    /** Send a message with one boolean argument. */
    static Pointer send(Pointer target, String selector, boolean arg) {
        return MsgSend.objc_msgSend(target, sel(selector), arg);
    }

    /** Send a message with one long argument. */
    static Pointer send(Pointer target, String selector, long arg) {
        return MsgSend.objc_msgSend(target, sel(selector), arg);
    }

    /** Send a message with one string argument. */
    static Pointer send(Pointer target, String selector, String arg) {
        return MsgSend.objc_msgSend(target, sel(selector), arg);
    }

    /** Send a message with two doubles (NSSize). */
    static Pointer send(Pointer target, String selector, double a, double b) {
        return MsgSend.objc_msgSend(target, sel(selector), a, b);
    }

    /** Send a message with two pointer arguments. */
    static Pointer send(Pointer target, String selector, Pointer a, Pointer b) {
        return MsgSend.objc_msgSend(target, sel(selector), a, b);
    }

    /** Send a message with three pointer arguments. */
    static Pointer send(Pointer target, String selector,
                        Pointer a, Pointer b, Pointer c) {
        return MsgSend.objc_msgSend(target, sel(selector), a, b, c);
    }

    /** Send a message with four doubles (NSRect). */
    static Pointer send(Pointer target, String selector,
                        double a, double b, double c, double d) {
        return MsgSend.objc_msgSend(target, sel(selector), a, b, c, d);
    }

    /** Send a message with NSRect + Pointer + long. */
    static Pointer send(Pointer target, String selector,
                        double a, double b, double c, double d,
                        Pointer view, long edge) {
        return MsgSend.objc_msgSend(target, sel(selector), a, b, c, d, view, edge);
    }

    /** Send a message with byte[] + length. */
    static Pointer send(Pointer target, String selector, byte[] bytes, long length) {
        return MsgSend.objc_msgSend(target, sel(selector), bytes, length);
    }

    // =========================================================================
    // Cocoa helpers
    // =========================================================================

    /**
     * Create an NSString from a Java String.
     *
     * <p>Equivalent to: {@code [[NSString alloc] initWithUTF8String:"text"]}
     */
    static Pointer nsString(String text) {
        Pointer nsStringClass = cls("NSString");
        Pointer alloc = send(nsStringClass, "alloc");
        return send(alloc, "initWithUTF8String:", text);
    }

    /**
     * Create an NSImage from a file path.
     */
    static Pointer nsImageFromFile(String path) {
        Pointer nsImageClass = cls("NSImage");
        Pointer alloc = send(nsImageClass, "alloc");
        return send(alloc, "initWithContentsOfFile:", nsString(path));
    }

    /**
     * Create an NSImage as a "template" image (adapts to light/dark menu bar).
     */
    static Pointer nsTemplateImage(String path) {
        Pointer image = nsImageFromFile(path);
        if (image != null && image != Pointer.NULL) {
            send(image, "setTemplate:", true);
        }
        return image;
    }

    // =========================================================================
    // Callback trampolines
    // =========================================================================

    /**
     * Callback interface for Objective-C method trampolines.
     *
     * <p>The signature {@code (id self, SEL _cmd, id sender)} matches
     * standard Objective-C action methods.
     */
    interface ActionCallback extends Callback {
        void invoke(Pointer self, Pointer selector, Pointer sender);
    }

    /**
     * Create a runtime Objective-C class with an action method that calls
     * the given Java callback.
     *
     * @param className Unique class name (must not conflict with existing classes)
     * @param selectorName The selector name (e.g., "menuItemClicked:")
     * @param callback The Java callback to invoke
     * @return An instance of the new class, usable as an action target
     */
    static Pointer createActionTarget(String className, String selectorName,
                                      ActionCallback callback) {
        Pointer nsObject = cls("NSObject");
        Pointer newClass = ObjC.INSTANCE.objc_allocateClassPair(nsObject, className, 0);
        if (newClass == null) {
            // Class already exists (e.g., from a previous call) — get it
            newClass = cls(className);
        } else {
            // "v@:@" = void return, id self, SEL _cmd, id sender
            ObjC.INSTANCE.class_addMethod(newClass, sel(selectorName), callback, "v@:@");
            ObjC.INSTANCE.objc_registerClassPair(newClass);
        }

        // Instantiate: [[ClassName alloc] init]
        Pointer alloc = send(newClass, "alloc");
        return send(alloc, "init");
    }
}
