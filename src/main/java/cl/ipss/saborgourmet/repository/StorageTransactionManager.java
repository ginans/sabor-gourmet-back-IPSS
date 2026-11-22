package cl.ipss.saborgourmet.repository;

import java.util.function.Supplier;

/**
 * Abstraction for coordinating read and write critical sections across repositories.
 * Backed by a {@code ReentrantReadWriteLock} in JSON storage mode and replaceable
 * by database transaction management in future persistence backends.
 */
public interface StorageTransactionManager {

    <T> T executeRead(Supplier<T> action);

    <T> T executeWrite(Supplier<T> action);

    void executeWrite(Runnable action);
}
