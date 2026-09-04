package com.fingard.persistence;

import java.util.List;
import java.util.Optional;

public interface Repository<T> {
    void save(String id, T entity);
    Optional<T> findById(String id);
    List<T> findAll();
}