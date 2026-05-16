package com.codesync.execution.repository;

import com.codesync.execution.entity.SupportedLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SupportedLanguageRepositoryTest {

    @Autowired
    private SupportedLanguageRepository languageRepository;

    @BeforeEach
    void setUp() {
        languageRepository.deleteAllInBatch();
    }

    private SupportedLanguage build(String name, String displayName,
                                    String version, boolean isActive) {
        return SupportedLanguage.builder()
                .name(name).displayName(displayName)
                .version(version).dockerImage("docker/" + name)
                .fileExtension("." + name).isActive(isActive)
                .build();
    }

    // ── findByIsActiveTrue ────────────────────────────────────────────

    @Test
    @DisplayName("findByIsActiveTrue - returns only active languages")
    void findByIsActiveTrue_returnsActiveOnly() {
        languageRepository.save(build("java",   "Java",   "21",   true));
        languageRepository.save(build("python", "Python", "3.11", true));
        languageRepository.save(build("cobol",  "COBOL",  "6.4",  false));

        List<SupportedLanguage> active =
                languageRepository.findByIsActiveTrue();

        assertThat(active).hasSize(2);
        assertThat(active).allMatch(SupportedLanguage::getIsActive);
    }

    @Test
    @DisplayName("findByIsActiveTrue - returns empty when no active languages")
    void findByIsActiveTrue_noneActive_returnsEmpty() {
        languageRepository.save(build("cobol", "COBOL", "6.4", false));

        assertThat(languageRepository.findByIsActiveTrue()).isEmpty();
    }

    // ── findByName ────────────────────────────────────────────────────

    @Test
    @DisplayName("findByName - returns language for exact name match")
    void findByName_existing_returnsLanguage() {
        languageRepository.save(build("java", "Java", "21", true));

        Optional<SupportedLanguage> result =
                languageRepository.findByName("java");

        assertThat(result).isPresent();
        assertThat(result.get().getDisplayName()).isEqualTo("Java");
        assertThat(result.get().getVersion()).isEqualTo("21");
    }

    @Test
    @DisplayName("findByName - returns empty for unknown name")
    void findByName_unknown_returnsEmpty() {
        assertThat(languageRepository.findByName("haskell")).isEmpty();
    }

    @Test
    @DisplayName("findByName - is case-sensitive (stores lowercase)")
    void findByName_caseSensitive_noMatchForUppercase() {
        languageRepository.save(build("java_test", "Java", "21", true));

        Optional<SupportedLanguage> result =
                languageRepository.findByName("Java_Test");

        assertThat(result).isEmpty();
    }

    // ── existsByName ──────────────────────────────────────────────────

    @Test
    @DisplayName("existsByName - returns true when language name exists")
    void existsByName_existing_returnsTrue() {
        languageRepository.save(build("python", "Python", "3.11", true));

        assertThat(languageRepository.existsByName("python")).isTrue();
    }

    @Test
    @DisplayName("existsByName - returns false for unknown language")
    void existsByName_unknown_returnsFalse() {
        assertThat(languageRepository.existsByName("fortran")).isFalse();
    }

    // ── save & findById ───────────────────────────────────────────────

    @Test
    @DisplayName("save - persists language and auto-generates ID")
    void save_newLanguage_persistsWithId() {
        SupportedLanguage lang =
                build("go", "Go", "1.21", true);

        SupportedLanguage saved = languageRepository.save(lang);

        assertThat(saved.getId()).isNotNull();
        assertThat(languageRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("save - defaults isActive to true when not set")
    void save_defaultIsActive_isTrue() {
        SupportedLanguage lang = SupportedLanguage.builder()
                .name("rust_test")
                .displayName("Rust")
                .version("1.75")
                .dockerImage("docker/rust")
                .fileExtension(".rs")
                .build();

        SupportedLanguage saved = languageRepository.save(lang);

        assertThat(saved.getIsActive()).isTrue();
    }
}