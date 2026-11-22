package cl.ipss.saborgourmet.repository.json;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.config.StorageProperties;
import cl.ipss.saborgourmet.exception.infrastructure.DataStorageException;
import cl.ipss.saborgourmet.model.RestaurantData;
import cl.ipss.saborgourmet.repository.StorageTransactionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Thread-safe JSON file storage engine backed by Jackson, atomic file replacement,
 * and a fair {@link ReentrantReadWriteLock} to guarantee concurrent consistency
 * within a single JVM instance.
 */
@Slf4j
@Component
public class JsonFileStorage implements StorageTransactionManager {

    private final Path storagePath;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public JsonFileStorage(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storagePath = Path.of(storageProperties.filePath()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    /**
     * Ensures the JSON storage file exists with a valid empty structure on application startup.
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
        lock.readLock().lock();
        try {
            return action.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public <T> T executeWrite(Supplier<T> action) {
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void executeWrite(Runnable action) {
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Reads the current JSON snapshot under a shared read lock.
     *
     * @param reader function extracting the desired result from {@link RestaurantData}
     * @param <T>    return type
     * @return extracted result
     */
    public <T> T readData(Function<RestaurantData, T> reader) {
        lock.readLock().lock();
        boolean readLocked = true;
        try {
            if (!Files.exists(storagePath)) {
                lock.readLock().unlock();
                readLocked = false;
                initialize();
                lock.readLock().lock();
                readLocked = true;
            }
            RestaurantData data = readFromDisk();
            return reader.apply(data);
        } finally {
            if (readLocked) {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Mutates the JSON snapshot under an exclusive write lock and persists it atomically.
     * If either the mutator or disk write fails, existing file contents remain intact.
     *
     * @param mutator function modifying {@link RestaurantData} and returning a result
     * @param <T>     return type
     * @return mutation result
     */
    public <T> T mutateData(Function<RestaurantData, T> mutator) {
        lock.writeLock().lock();
        try {
            ensureFileExistsUnderWriteLock();
            RestaurantData data = readFromDisk();
            T result = mutator.apply(data);
            writeAtomically(data);
            return result;
        } finally {
            lock.writeLock().unlock();
        }
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
            RestaurantData loaded = objectMapper.readValue(storagePath.toFile(), RestaurantData.class);
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
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), data);
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
}
