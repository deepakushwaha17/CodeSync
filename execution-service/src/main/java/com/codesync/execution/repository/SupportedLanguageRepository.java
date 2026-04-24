package com.codesync.execution.repository;

import com.codesync.execution.entity.SupportedLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportedLanguageRepository
        extends JpaRepository<SupportedLanguage, Long> {

    List<SupportedLanguage> findByIsActiveTrue();

    Optional<SupportedLanguage> findByName(String name);

    boolean existsByName(String name);
}