package com.lowcode.repository;

import com.lowcode.model.App;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for App entities
 */
@Repository
public interface AppRepository extends JpaRepository<App, String> {

    Optional<App> findBySchemaName(String schemaName);

    List<App> findAllByOrderByCreatedAtDesc();

    boolean existsBySchemaName(String schemaName);
}
