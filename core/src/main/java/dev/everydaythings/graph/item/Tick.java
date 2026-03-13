package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a periodic tick method on a component.
 *
 * <p>Tick methods are invoked periodically by the runtime to update
 * time-varying component state (clocks, countdowns, live feeds, etc.).
 *
 * <p>Usage:
 * <pre>{@code
 * @Tick(interval = 1000)
 * public void tick() {
 *     this.second = LocalTime.now().getSecond();
 * }
 * }</pre>
 *
 * <p>The method must be public and take no parameters. The interval
 * specifies the minimum time between invocations in milliseconds.
 *
 * @see ComponentScanner
 * @see TickSpec
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tick {
    /** Interval in milliseconds between invocations. Default 1s. */
    long interval() default 1000;
}
