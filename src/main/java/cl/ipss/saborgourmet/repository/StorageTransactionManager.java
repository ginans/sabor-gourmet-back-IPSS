package cl.ipss.saborgourmet.repository;

import java.util.function.Supplier;

/**
 * Abstracción para coordinar unidades de trabajo de lectura y escritura entre repositorios.
 * En modo JSON utiliza {@code ReentrantReadWriteLock} y en SQL puede delegarse al gestor transaccional de Spring.
 */
public interface StorageTransactionManager {

    <T> T executeRead(Supplier<T> action);

    <T> T executeWrite(Supplier<T> action);

    void executeWrite(Runnable action);
}
