package cl.ipss.saborgourmet.repository.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import cl.ipss.saborgourmet.config.StorageProperties;
import cl.ipss.saborgourmet.exception.infrastructure.DataStorageException;
import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.model.ReservationStatus;
import cl.ipss.saborgourmet.model.RestaurantTable;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileStorageTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private Path storageFile;
    private JsonFileStorage storage;
    private JsonClientRepository clientRepository;
    private JsonTableRepository tableRepository;
    private JsonReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        storageFile = tempDir.resolve("nested").resolve("data.json");
        storage = new JsonFileStorage(new StorageProperties(storageFile.toString()), objectMapper);
        clientRepository = new JsonClientRepository(storage);
        tableRepository = new JsonTableRepository(storage);
        reservationRepository = new JsonReservationRepository(storage);
    }

    @Test
    @DisplayName("SG-PER-01: Initializes JSON file automatically with empty collections when missing")
    void initializesFileAutomaticallyWhenMissing() throws Exception {
        assertThat(Files.exists(storageFile)).isFalse();

        storage.initialize();

        assertThat(Files.exists(storageFile)).isTrue();
        String jsonContent = Files.readString(storageFile);
        assertThat(jsonContent).contains("\"clients\"", "\"tables\"", "\"reservations\"");
        assertThat(jsonContent).contains("\"nextClientId\" : 1", "\"nextTableId\" : 1", "\"nextReservationId\" : 1");
        assertThat(clientRepository.findAll()).isEmpty();
        assertThat(tableRepository.findAll()).isEmpty();
        assertThat(reservationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("SG-PER-02: Preserves all entities and ID references across simulated server restart")
    void preservesDataAcrossRestartWithoutRedundancy() throws Exception {
        Client savedClient = clientRepository.save(new Client(
                null, "Ana Perez", "ana@example.com", "+56912345678", ClientStatus.ACTIVE, 0));
        RestaurantTable savedTable = tableRepository.save(new RestaurantTable(
                null, 1, 4, "Main Hall", true));
        Reservation savedReservation = reservationRepository.save(new Reservation(
                null, savedClient.getId(), savedTable.getId(),
                LocalDate.of(2026, 12, 10), LocalTime.of(20, 0), LocalTime.of(22, 0),
                4, ReservationStatus.CANCELLED, "Window seat"));

        JsonFileStorage restartedStorage =
                new JsonFileStorage(new StorageProperties(storageFile.toString()), objectMapper);
        JsonClientRepository reloadedClients = new JsonClientRepository(restartedStorage);
        JsonTableRepository reloadedTables = new JsonTableRepository(restartedStorage);
        JsonReservationRepository reloadedReservations = new JsonReservationRepository(restartedStorage);

        assertThat(reloadedClients.findById(savedClient.getId())).isPresent();
        assertThat(reloadedTables.findById(savedTable.getId())).isPresent();
        assertThat(reloadedReservations.findById(savedReservation.getId()))
                .isPresent()
                .get()
                .satisfies(r -> {
                    assertThat(r.getClientId()).isEqualTo(savedClient.getId());
                    assertThat(r.getTableId()).isEqualTo(savedTable.getId());
                    assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                });

        String rawJson = Files.readString(storageFile);
        assertThat(rawJson).contains("\"clientId\"", "\"tableId\"");
        assertThat(rawJson).doesNotContain("\"client\" :", "\"table\" :");
    }

    @Test
    @DisplayName("SG-PER-03: Preserves existing JSON data intact when atomic write encounters an I/O error")
    void preservesExistingDataWhenWriteFails() throws Exception {
        clientRepository.save(new Client(
                null, "Ana Perez", "ana@example.com", "+56912345678", ClientStatus.ACTIVE, 0));
        String contentBeforeError = Files.readString(storageFile);

        Path blockingTempDir = storageFile.resolveSibling("data.json.tmp");
        Files.createDirectory(blockingTempDir);

        try {
            assertThatThrownBy(() -> clientRepository.save(new Client(
                    null, "Second User", "second@example.com", "+56999999999", ClientStatus.ACTIVE, 0)))
                    .isInstanceOf(DataStorageException.class);

            assertThat(Files.readString(storageFile)).isEqualTo(contentBeforeError);
            assertThat(clientRepository.findAll()).hasSize(1);
        } finally {
            Files.deleteIfExists(blockingTempDir);
        }
    }

    @Test
    @DisplayName("SG-PER-04: Serializes concurrent write transactions without losing records or corrupting JSON")
    void serializesConcurrentWritesSafely() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Client>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return storage.executeWrite(() -> clientRepository.save(new Client(
                            null,
                            "Client " + index,
                            "client" + index + "@example.com",
                            "+5690000000" + index,
                            ClientStatus.ACTIVE,
                            0
                    )));
                }));
            }
            ready.await();
            start.countDown();

            for (Future<Client> future : futures) {
                assertThat(future.get().getId()).isNotNull();
            }
        } finally {
            executor.shutdownNow();
        }

        List<Client> allClients = clientRepository.findAll();
        assertThat(allClients).hasSize(threads);
        assertThat(allClients.stream().map(Client::getId).distinct().count()).isEqualTo(threads);
    }

    @Test
    @DisplayName("Regression: executeRead when JSON file is missing never deadlocks on lock upgrade")
    void executeReadWhenFileMissingDoesNotDeadlock() {
        assertThat(Files.exists(storageFile)).isFalse();
        List<Client> result = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> storage.executeRead(() -> clientRepository.findAll())
        );
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Regression: executeWrite rolls back multi-entity mutations if an exception occurs before completion")
    void executeWriteIsAtomicAcrossMultipleRepositories() {
        storage.initialize();
        assertThatThrownBy(() -> storage.executeWrite(() -> {
            clientRepository.save(Client.builder()
                    .name("Ana").email("ana@correo.cl").phone("+56912345678").status(ClientStatus.ACTIVE).build());
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(clientRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Regression: Deleted entity IDs are never recycled and unknown JSON fields are tolerated")
    void doesNotRecycleIdsAndToleratesUnknownJsonFields() throws Exception {
        Client first = clientRepository.save(Client.builder()
                .name("First").email("first@correo.cl").phone("+56911111111").status(ClientStatus.ACTIVE).build());
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(clientRepository.deleteById(1L)).isTrue();

        Client second = clientRepository.save(Client.builder()
                .name("Second").email("second@correo.cl").phone("+56922222222").status(ClientStatus.ACTIVE).build());
        assertThat(second.getId()).isEqualTo(2L);

        ObjectMapper strictHttpMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        Files.writeString(storageFile, """
                {
                  "unknownFutureField": "ignored",
                  "clients": [],
                  "tables": [],
                  "reservations": []
                }
                """);
        JsonFileStorage strictStorage = new JsonFileStorage(new StorageProperties(storageFile.toString()), strictHttpMapper);
        assertThat(new JsonClientRepository(strictStorage).findAll()).isEmpty();
    }

    @Test
    @DisplayName("Regression: Rejects write transaction nested inside an active read transaction instead of deadlocking")
    void rejectsNestedWriteInsideActiveReadTransaction() {
        storage.initialize();
        assertThatThrownBy(() -> storage.executeRead(() ->
                storage.executeWrite(() -> clientRepository.findAll())))
                .isInstanceOf(IllegalStateException.class);
    }
}
