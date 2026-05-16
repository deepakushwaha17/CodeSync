package com.codesync.project.repository;

import com.codesync.project.entity.Project;
import com.codesync.project.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProjectRepositoryTest {

    @Autowired private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() { projectRepository.deleteAll(); }

    private Project build(Long ownerId, String name,
                          String language, Visibility visibility, boolean archived) {
        return Project.builder()
                .ownerId(ownerId)
                .name(name)
                .language(language)
                .visibility(visibility)
                .isArchived(archived)
                .starCount(0).forkCount(0)
                .build();
    }

    // ── findByOwnerId ────────────────────────────────────────────────

    @Test
    @DisplayName("findByOwnerId - returns projects belonging to owner")
    void findByOwnerId_existing_returnsProjects() {
        projectRepository.save(build(1L, "ProjectA", "Java", Visibility.PUBLIC, false));
        projectRepository.save(build(1L, "ProjectB", "Python", Visibility.PRIVATE, false));
        projectRepository.save(build(2L, "OtherProject", "Go", Visibility.PUBLIC, false));

        List<Project> results = projectRepository.findByOwnerId(1L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p -> p.getOwnerId().equals(1L));
    }

    @Test
    @DisplayName("findByOwnerId - returns empty list for unknown owner")
    void findByOwnerId_noMatch_returnsEmpty() {
        assertThat(projectRepository.findByOwnerId(99L)).isEmpty();
    }

    // ── findByVisibility ─────────────────────────────────────────────

    @Test
    @DisplayName("findByVisibility - returns only PUBLIC projects")
    void findByVisibility_public_returnsPublicOnly() {
        projectRepository.save(build(1L, "Public1", "Java", Visibility.PUBLIC, false));
        projectRepository.save(build(1L, "Private1", "Go", Visibility.PRIVATE, false));

        List<Project> results = projectRepository.findByVisibility(Visibility.PUBLIC);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Public1");
    }

    // ── findByIsArchived ──────────────────────────────────────────────

    @Test
    @DisplayName("findByIsArchived - returns only archived projects")
    void findByIsArchived_true_returnsArchived() {
        projectRepository.save(build(1L, "Active",   "Java", Visibility.PUBLIC, false));
        projectRepository.save(build(1L, "Archived", "Java", Visibility.PUBLIC, true));

        List<Project> results = projectRepository.findByIsArchived(true);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Archived");
    }

    // ── findByOwnerIdAndIsArchived ─────────────────────────────────────

    @Test
    @DisplayName("findByOwnerIdAndIsArchived - filters by owner and archived flag")
    void findByOwnerIdAndIsArchived_ownerActive_returnsFiltered() {
        projectRepository.save(build(1L, "Active1",   "Java", Visibility.PUBLIC, false));
        projectRepository.save(build(1L, "Archived1", "Java", Visibility.PUBLIC, true));
        projectRepository.save(build(2L, "Active2",   "Java", Visibility.PUBLIC, false));

        List<Project> results = projectRepository.findByOwnerIdAndIsArchived(1L, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Active1");
    }

    // ── searchByName ──────────────────────────────────────────────────

    @Test
    @DisplayName("searchByName - returns case-insensitive name matches")
    void searchByName_keyword_returnsCaseInsensitiveMatch() {
        projectRepository.save(build(1L, "JavaApp",   "Java",   Visibility.PUBLIC,  false));
        projectRepository.save(build(1L, "JAVATOOLS", "Java",   Visibility.PRIVATE, false));
        projectRepository.save(build(1L, "GoService", "Go",     Visibility.PUBLIC,  false));

        List<Project> results = projectRepository.searchByName("java");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p ->
                p.getName().toLowerCase().contains("java"));
    }

    @Test
    @DisplayName("searchByName - returns empty when no name match")
    void searchByName_noMatch_returnsEmpty() {
        projectRepository.save(build(1L, "GoService", "Go", Visibility.PUBLIC, false));

        assertThat(projectRepository.searchByName("java")).isEmpty();
    }

    // ── searchPublicByName ────────────────────────────────────────────

    @Test
    @DisplayName("searchPublicByName - returns only PUBLIC projects with name match")
    void searchPublicByName_keyword_returnsPublicOnly() {
        projectRepository.save(build(1L, "JavaPublic",  "Java", Visibility.PUBLIC,  false));
        projectRepository.save(build(1L, "JavaPrivate", "Java", Visibility.PRIVATE, false));

        List<Project> results = projectRepository.searchPublicByName("java");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("JavaPublic");
    }

    // ── findByVisibilityAndLanguage ───────────────────────────────────

    @Test
    @DisplayName("findByVisibilityAndLanguage - returns public Java projects only")
    void findByVisibilityAndLanguage_publicJava_returnsFiltered() {
        projectRepository.save(build(1L, "JavaPub",  "Java",   Visibility.PUBLIC,  false));
        projectRepository.save(build(1L, "JavaPriv", "Java",   Visibility.PRIVATE, false));
        projectRepository.save(build(1L, "GoPub",    "Go",     Visibility.PUBLIC,  false));

        List<Project> results = projectRepository
                .findByVisibilityAndLanguage(Visibility.PUBLIC, "Java");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("JavaPub");
    }

    // ── countByOwnerId ────────────────────────────────────────────────

    @Test
    @DisplayName("countByOwnerId - returns correct project count for owner")
    void countByOwnerId_existing_returnsCount() {
        projectRepository.save(build(1L, "P1", "Java", Visibility.PUBLIC, false));
        projectRepository.save(build(1L, "P2", "Go",   Visibility.PUBLIC, false));

        int count = projectRepository.countByOwnerId(1L);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByOwnerId - returns 0 for unknown owner")
    void countByOwnerId_unknown_returnsZero() {
        assertThat(projectRepository.countByOwnerId(999L)).isZero();
    }

    // ── findByForkedFromId ────────────────────────────────────────────

    @Test
    @DisplayName("findByForkedFromId - returns all forks of a project")
    void findByForkedFromId_existing_returnsForks() {
        Project original = projectRepository.save(
                build(1L, "Original", "Java", Visibility.PUBLIC, false));

        Project fork1 = build(2L, "Original-fork", "Java", Visibility.PRIVATE, false);
        fork1.setForkedFromId(original.getProjectId());
        Project fork2 = build(3L, "Original-fork2", "Java", Visibility.PRIVATE, false);
        fork2.setForkedFromId(original.getProjectId());
        projectRepository.save(fork1);
        projectRepository.save(fork2);

        List<Project> forks = projectRepository.findByForkedFromId(original.getProjectId());

        assertThat(forks).hasSize(2);
    }

    @Test
    @DisplayName("findByForkedFromId - returns empty when project has no forks")
    void findByForkedFromId_noForks_returnsEmpty() {
        Project original = projectRepository.save(
                build(1L, "Original", "Java", Visibility.PUBLIC, false));

        assertThat(projectRepository.findByForkedFromId(original.getProjectId())).isEmpty();
    }
}