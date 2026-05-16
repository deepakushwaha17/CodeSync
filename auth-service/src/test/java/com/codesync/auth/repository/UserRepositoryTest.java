package com.codesync.auth.repository;

import com.codesync.auth.entity.User;
import com.codesync.auth.enums.Provider;
import com.codesync.auth.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests for UserRepository.
 *
 * Uses @DataJpaTest → spins up an in-memory H2 database,
 * loads only JPA slice (no full Spring context).
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    // ── Fixture ─────────────────────────────────────────────────────────

    private User buildUser(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("$2a$10$hashedpassword")
                .fullName("Test User")
                .role(Role.DEVELOPER)
                .provider(Provider.LOCAL)
                .isActive(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ── findByEmail ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findByEmail - returns user when email exists")
    void findByEmail_existingEmail_returnsUser() {
        userRepository.save(buildUser("john_doe", "john@example.com"));

        Optional<User> result = userRepository.findByEmail("john@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("findByEmail - returns empty when email does not exist")
    void findByEmail_nonExistingEmail_returnsEmpty() {
        Optional<User> result = userRepository.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    // ── findByUsername ───────────────────────────────────────────────────

    @Test
    @DisplayName("findByUsername - returns user when username exists")
    void findByUsername_existingUsername_returnsUser() {
        userRepository.save(buildUser("john_doe", "john@example.com"));

        Optional<User> result = userRepository.findByUsername("john_doe");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("findByUsername - returns empty when username does not exist")
    void findByUsername_nonExistingUsername_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("ghost");

        assertThat(result).isEmpty();
    }

    // ── existsByEmail ────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByEmail - returns true when email exists")
    void existsByEmail_existingEmail_returnsTrue() {
        userRepository.save(buildUser("alice", "alice@example.com"));

        boolean exists = userRepository.existsByEmail("alice@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEmail - returns false when email does not exist")
    void existsByEmail_nonExistingEmail_returnsFalse() {
        boolean exists = userRepository.existsByEmail("nothere@example.com");

        assertThat(exists).isFalse();
    }

    // ── existsByUsername ─────────────────────────────────────────────────

    @Test
    @DisplayName("existsByUsername - returns true when username exists")
    void existsByUsername_existingUsername_returnsTrue() {
        userRepository.save(buildUser("bob", "bob@example.com"));

        boolean exists = userRepository.existsByUsername("bob");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByUsername - returns false when username does not exist")
    void existsByUsername_nonExistingUsername_returnsFalse() {
        boolean exists = userRepository.existsByUsername("unknown");

        assertThat(exists).isFalse();
    }

    // ── findAllByRole ────────────────────────────────────────────────────

    @Test
    @DisplayName("findAllByRole - returns all users with matching role")
    void findAllByRole_developerRole_returnsDevelopers() {
        userRepository.save(buildUser("dev1", "dev1@example.com"));
        userRepository.save(buildUser("dev2", "dev2@example.com"));

        User admin = buildUser("adminUser", "admin@example.com");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        List<User> developers = userRepository.findAllByRole(Role.DEVELOPER);

        assertThat(developers).hasSize(2).allMatch(u -> u.getRole() == Role.DEVELOPER);
    }

    @Test
    @DisplayName("findAllByRole - returns empty list when no users have the role")
    void findAllByRole_noMatch_returnsEmptyList() {
        userRepository.save(buildUser("dev1", "dev1@example.com")); // DEVELOPER

        List<User> admins = userRepository.findAllByRole(Role.ADMIN);

        assertThat(admins).isEmpty();
    }

    // ── findByProviderAndProviderId ───────────────────────────────────────

    @Test
    @DisplayName("findByProviderAndProviderId - returns user for OAuth provider match")
    void findByProviderAndProviderId_existing_returnsUser() {
        User oauthUser = buildUser("githubUser", "github@example.com");
        oauthUser.setProvider(Provider.GITHUB);
        oauthUser.setProviderId("gh-123456");
        userRepository.save(oauthUser);

        Optional<User> result =
                userRepository.findByProviderAndProviderId(Provider.GITHUB, "gh-123456");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("githubUser");
    }

    @Test
    @DisplayName("findByProviderAndProviderId - returns empty when providerId not found")
    void findByProviderAndProviderId_nonExisting_returnsEmpty() {
        Optional<User> result =
                userRepository.findByProviderAndProviderId(Provider.GOOGLE, "no-id");

        assertThat(result).isEmpty();
    }

    // ── searchByUsername ─────────────────────────────────────────────────

    @Test
    @DisplayName("searchByUsername - returns matching users (case-insensitive)")
    void searchByUsername_keyword_returnsCaseInsensitiveMatches() {
        userRepository.save(buildUser("JavaDev", "javadev@example.com"));
        userRepository.save(buildUser("javaguru", "javaguru@example.com"));
        userRepository.save(buildUser("python_dev", "python@example.com"));

        List<User> results = userRepository.searchByUsername("java");

        assertThat(results).hasSize(2).allMatch(u ->
                u.getUsername().toLowerCase().contains("java"));
    }

    @Test
    @DisplayName("searchByUsername - returns empty list when no match found")
    void searchByUsername_noMatch_returnsEmptyList() {
        userRepository.save(buildUser("dev1", "dev1@example.com"));

        List<User> results = userRepository.searchByUsername("xyz_no_match");

        assertThat(results).isEmpty();
    }

    // ── deleteByUserId ───────────────────────────────────────────────────

    @Test
    @DisplayName("deleteByUserId - deletes user with given ID")
    void deleteByUserId_existingUser_deletesSuccessfully() {
        User saved = userRepository.save(buildUser("toDelete", "delete@example.com"));
        Long userId = saved.getUserId();

        userRepository.deleteByUserId(userId);

        assertThat(userRepository.findById(userId)).isEmpty();
    }

    // ── save & findById ──────────────────────────────────────────────────

    @Test
    @DisplayName("save - persists user and auto-generates ID")
    void save_newUser_persistsWithGeneratedId() {
        User user = buildUser("newUser", "new@example.com");

        User saved = userRepository.save(user);

        assertThat(saved.getUserId()).isNotNull();
        assertThat(userRepository.findById(saved.getUserId())).isPresent();
    }

    @Test
    @DisplayName("save - updates existing user fields")
    void save_existingUser_updatesFields() {
        User saved = userRepository.save(buildUser("updater", "updater@example.com"));
        saved.setFullName("Updated Full Name");
        userRepository.save(saved);

        User fetched = userRepository.findById(saved.getUserId()).orElseThrow();
        assertThat(fetched.getFullName()).isEqualTo("Updated Full Name");
    }
}