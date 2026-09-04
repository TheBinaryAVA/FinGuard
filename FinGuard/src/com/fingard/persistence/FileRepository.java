package com.fingard.persistence;

import com.fingard.exception.PersistenceException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FileRepository<T extends Serializable> implements Repository<T> {
    private final Path snapshotFile;
    private final Path auditFile;
    private final Map<String, T> values;

    public FileRepository(Path directory, String name) {
        if (directory == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Repository directory and name are required.");
        }
        if (!name.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Repository name contains unsupported characters.");
        }
        this.snapshotFile = directory.resolve(name + ".bin");
        this.auditFile = directory.resolve(name + ".log");
        try {
            Files.createDirectories(directory);
            this.values = load();
        } catch (IOException | ClassNotFoundException exception) {
            throw new PersistenceException("Unable to load file repository " + name, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, T> load() throws IOException, ClassNotFoundException {
        if (!Files.exists(snapshotFile)) {
            return new HashMap<>();
        }
        try (FileInputStream input = new FileInputStream(snapshotFile.toFile());
             ObjectInputStream objectInput = new ObjectInputStream(input)) {
            Object value = objectInput.readObject();
            if (!(value instanceof Map)) {
                throw new IOException("Snapshot does not contain a map.");
            }
            return new HashMap<>((Map<String, T>) value);
        }
    }

    @Override
    public synchronized void save(String id, T entity) {
        if (id == null || id.isBlank() || entity == null) {
            throw new IllegalArgumentException("Repository ID and entity are required.");
        }
        Map<String, T> updatedValues = new HashMap<>(values);
        updatedValues.put(id, entity);
        Path temporaryFile = snapshotFile.resolveSibling(snapshotFile.getFileName() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporaryFile.toFile());
                 ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeObject(updatedValues);
                objectOutput.flush();
            }
            try {
                Files.move(temporaryFile, snapshotFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, snapshotFile, StandardCopyOption.REPLACE_EXISTING);
            }
            values.clear();
            values.putAll(updatedValues);
            appendAudit(id);
        } catch (IOException exception) {
            throw new PersistenceException("Unable to save repository record " + id, exception);
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
            }
        }
    }

    private void appendAudit(String id) throws IOException {
        try (FileWriter audit = new FileWriter(auditFile.toFile(), true)) {
            audit.write("saved=" + id + System.lineSeparator());
        }
    }

    @Override
    public synchronized Optional<T> findById(String id) {
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public synchronized List<T> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(values.values()));
    }
}