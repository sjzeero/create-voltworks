package io.github.sjzeero.eecircuits.pgsim.solver;

/**
 * Multi-tick hooks, called only when multi ticks are > 1
 *
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
public interface IMultiHooks {
    /**
     * Called before any solving is started, once per tick (regardless of multi-ticks)
     */
    default void prepare(int multiTicks) { }

    /**
     * Called after a single small tick solve
     */
    default void postMicroTick() { }
}
