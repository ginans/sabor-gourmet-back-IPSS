package cl.ipss.saborgourmet.repository.json;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.config.StorageProperties;
import cl.ipss.saborgourmet.exception.infrastructure.DataStorageException;
import cl.ipss.saborgourmet.model.RestaurantData;
import cl.ipss.saborgourmet.repository.StorageTransactionManager;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Motor de persistencia en archivo JSON con soporte de unidad de trabajo por hilo,
 * escritura atómica (.tmp -> Files.move) y sincronización local mediante
 * {@link ReentrantReadWriteLock} en modo justo (fair).
 *
 * <p>Dentro de una misma operación {@code executeRead} o {@code executeWrite}, el archivo
 * se lee una sola vez (evitando lecturas N+1) y las mutaciones sobre múltiples entidades
 * se persisten en un único volcado atómico al finalizar sin errores.</p>
 */
@Slf4j
@Component
public class JsonFileStorage implements StorageTransactionManager {

    private final Path storagePath;
    private final ObjectMapper storageObjectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final ThreadLocal<ReadSession> readSession = new ThreadLocal<>();
    private final ThreadLocal<WriteSession> writeSession = new ThreadLocal<>();

    public JsonFileStorage(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storagePath = Path.of(storageProperties.filePath()).toAbsolutePath().normalize();
        this.storageObjectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Garantiza que el archivo JSON exista con estructura inicial válida al arrancar la aplicación.
     */
    @PostConstruct
    public void initialize() {
        lock.writeLock().lock();
        try {
            ensureFileExistsUnderWriteLock();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public <T> T executeRead(Supplier<T> action) {
        if (writeSession.get() != null) {
            return action.get();
        }
        ReadSession current = readSession.get();
        if (current != null) {
            current.depth++;
            try {
                return action.get();
            } finally {
                current.depth--;
            }
        }

        lock.readLock().lock();
        try {
            RestaurantData snapshot = Files.exists(storagePath) ? readFromDisk() : new RestaurantData();
            readSession.set(new ReadSession(snapshot));
            return action.get();
        } finally {
            readSession.remove();
            lock.readLock().unlock();
        }
    }

    @Override
    public <T> T executeWrite(Supplier<T> action) {
        WriteSession current = writeSession.get();
        if (current != null) {
            current.depth++;
            try {
                return action.get();
            } finally {
                current.depth--;
            }
        }

        lock.writeLock().lock();
        try {
            ensureFileExistsUnderWriteLock();
            WriteSession session = new WriteSession(readFromDisk());
            writeSession.set(session);
            T result = action.get();
            if (session.dirty) {
                writeAtomically(session.data);
            }
            return result;
        } finally {
            writeSession.remove();
            lock.writeLock().unlock();
        }
    }

    @Override
    public void executeWrite(Runnable action) {
        executeWrite(() -> {
            action.run();
            return null;
        });
    }

    public <T> T readData(Function<RestaurantData, T> reader) {
        WriteSession activeWrite = writeSession.get();
        if (activeWrite != null) {
            return reader.apply(activeWrite.data);
        }
        ReadSession activeRead = readSession.get();
        if (activeRead != null) {
            return reader.apply(activeRead.data);
        }
        return executeRead(() -> reader.apply(readSession.get().data));
    }

    public <T> T mutateData(Function<RestaurantData, T> mutator) {
        WriteSession activeWrite = writeSession.get();
        if (activeWrite != null) {
            T result = mutator.apply(activeWrite.data);
            activeWrite.dirty = true;
            return result;
        }
        return executeWrite(() -> {
            WriteSession session = writeSession.get();
            T result = mutator.apply(session.data);
            session.dirty = true;
            return result;
        });
    }

    /**
     * Inserta o reemplaza una entidad dentro de una lista del almacén asignando un ID único
     * proveniente de su secuencia persistente cuando la entidad es nueva.
     */
    static <E> E upsert(
            List<E> collection,
            E entityCopy,
            Function<E, Long> idGetter,
            BiConsumer<E, Long> idSetter,
            LongSupplier idAllocator) {
        Long id = idGetter.apply(entityCopy);
        if (id == null) {
            idSetter.accept(entityCopy, idAllocator.getAsLong());
            collection.add(entityCopy);
            return entityCopy;
        }
        for (int i = 0; i < collection.size(); i++) {
            if (Objects.equals(idGetter.apply(collection.get(i)), id)) {
                collection.set(i, entityCopy);
                return entityCopy;
            }
        }
        collection.add(entityCopy);
        return entityCopy;
    }

    public Path getStoragePath() {
        return storagePath;
    }

    private void ensureFileExistsUnderWriteLock() {
        if (Files.exists(storagePath)) {
            return;
        }
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeAtomically(new RestaurantData());
            log.info("Initialized empty JSON storage file at {}", storagePath);
        } catch (IOException ex) {
            throw new DataStorageException("Failed to initialize JSON storage directory: " + storagePath, ex);
        }
    }

    private RestaurantData readFromDisk() {
        try {
            if (Files.size(storagePath) == 0) {
                return new RestaurantData();
            }
            RestaurantData loaded = storageObjectMapper.readValue(storagePath.toFile(), RestaurantData.class);
            return loaded != null ? loaded : new RestaurantData();
        } catch (IOException ex) {
            throw new DataStorageException("Failed to read JSON storage file: " + storagePath, ex);
        }
    }

    private void writeAtomically(RestaurantData data) {
        Path parent = storagePath.getParent();
        Path tempPath = storagePath.resolveSibling(
                storagePath.getFileName().toString() + ApiConstants.TEMP_FILE_SUFFIX);
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            storageObjectMapper.writeValue(tempPath.toFile(), data);
            try {
                Files.move(tempPath, storagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            cleanupTempFile(tempPath);
            throw new DataStorageException("Failed to write JSON storage file atomically: " + storagePath, ex);
        }
    }

    private void cleanupTempFile(Path tempPath) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException cleanupEx) {
            log.warn("Could not delete temporary storage file {}: {}", tempPath, cleanupEx.getMessage());
        }
    }

    private static final class ReadSession {
        private final RestaurantData data;
        private int depth = 1;

        private ReadSession(RestaurantData data) {
            this.data = data;
        }
    }

    private static final class WriteSession {
        private final RestaurantData data;
        private boolean dirty;
        private int depth = 1;

        private WriteSession(RestaurantData data) {
            this.data = data;
        }
    }
}
