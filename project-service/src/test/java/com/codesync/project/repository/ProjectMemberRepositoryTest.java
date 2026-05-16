package com.codesync.project.repository;

import com.codesync.project.entity.Project;
import com.codesync.project.entity.ProjectMember;
import com.codesync.project.enums.MemberRole;
import com.codesync.project.enums.Visibility;
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
class ProjectMemberRepositoryTest {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        projectRepository.deleteAll();
        project = projectRepository.save(Project.builder()
                .ownerId(1L).name("TestProject")
                .visibility(Visibility.PUBLIC)
                .isArchived(false).starCount(0).forkCount(0)
                .build());
    }

    private ProjectMember buildMember(Long userId, MemberRole role) {
        return ProjectMember.builder()
                .project(project)
                .userId(userId)
                .role(role)
                .build();
    }

    // ── findByProject_ProjectId ───────────────────────────────────────

    @Test
    @DisplayName("findByProject_ProjectId - returns all members for project")
    void findByProjectId_existing_returnsMembers() {
        memberRepository.save(buildMember(10L, MemberRole.OWNER));
        memberRepository.save(buildMember(20L, MemberRole.EDITOR));

        List<ProjectMember> members =
                memberRepository.findByProject_ProjectId(project.getProjectId());

        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("findByProject_ProjectId - returns empty for non-existing project")
    void findByProjectId_noMembers_returnsEmpty() {
        assertThat(memberRepository.findByProject_ProjectId(999L)).isEmpty();
    }

    // ── findByUserId ──────────────────────────────────────────────────

    @Test
    @DisplayName("findByUserId - returns all memberships for a user")
    void findByUserId_existing_returnsMemberships() {
        memberRepository.save(buildMember(10L, MemberRole.EDITOR));

        List<ProjectMember> result = memberRepository.findByUserId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(10L);
    }

    // ── findByProject_ProjectIdAndUserId ──────────────────────────────

    @Test
    @DisplayName("findByProject_ProjectIdAndUserId - returns member when found")
    void findByProjectIdAndUserId_existing_returnsMember() {
        memberRepository.save(buildMember(10L, MemberRole.OWNER));

        Optional<ProjectMember> result =
                memberRepository.findByProject_ProjectIdAndUserId(
                        project.getProjectId(), 10L);

        assertThat(result).isPresent();
        assertThat(result.get().getRole()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    @DisplayName("findByProject_ProjectIdAndUserId - returns empty when not a member")
    void findByProjectIdAndUserId_nonExisting_returnsEmpty() {
        Optional<ProjectMember> result =
                memberRepository.findByProject_ProjectIdAndUserId(
                        project.getProjectId(), 99L);

        assertThat(result).isEmpty();
    }

    // ── existsByProject_ProjectIdAndUserId ────────────────────────────

    @Test
    @DisplayName("existsByProject_ProjectIdAndUserId - returns true when member exists")
    void exists_memberExists_returnsTrue() {
        memberRepository.save(buildMember(10L, MemberRole.VIEWER));

        boolean exists = memberRepository.existsByProject_ProjectIdAndUserId(
                project.getProjectId(), 10L);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByProject_ProjectIdAndUserId - returns false when not a member")
    void exists_notMember_returnsFalse() {
        boolean exists = memberRepository.existsByProject_ProjectIdAndUserId(
                project.getProjectId(), 99L);

        assertThat(exists).isFalse();
    }

    // ── deleteByProject_ProjectIdAndUserId ────────────────────────────

    @Test
    @DisplayName("deleteByProject_ProjectIdAndUserId - removes the member")
    void deleteByProjectIdAndUserId_existing_removedSuccessfully() {
        memberRepository.save(buildMember(10L, MemberRole.EDITOR));

        memberRepository.deleteByProject_ProjectIdAndUserId(
                project.getProjectId(), 10L);

        assertThat(memberRepository.existsByProject_ProjectIdAndUserId(
                project.getProjectId(), 10L)).isFalse();
    }

    // ── countByProject_ProjectId ──────────────────────────────────────

    @Test
    @DisplayName("countByProject_ProjectId - returns correct count")
    void count_multipleMembers_returnsCorrectCount() {
        memberRepository.save(buildMember(10L, MemberRole.OWNER));
        memberRepository.save(buildMember(20L, MemberRole.EDITOR));
        memberRepository.save(buildMember(30L, MemberRole.VIEWER));

        int count = memberRepository.countByProject_ProjectId(project.getProjectId());

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countByProject_ProjectId - returns 0 when no members")
    void count_noMembers_returnsZero() {
        assertThat(memberRepository.countByProject_ProjectId(project.getProjectId()))
                .isZero();
    }

    // ── findProjectIdsByUserId ────────────────────────────────────────

    @Test
    @DisplayName("findProjectIdsByUserId - returns project IDs for user")
    void findProjectIdsByUserId_existing_returnsIds() {
        memberRepository.save(buildMember(10L, MemberRole.EDITOR));

        List<Long> ids = memberRepository.findProjectIdsByUserId(10L);

        assertThat(ids).contains(project.getProjectId());
    }

    @Test
    @DisplayName("findProjectIdsByUserId - returns empty list for unknown user")
    void findProjectIdsByUserId_unknown_returnsEmpty() {
        assertThat(memberRepository.findProjectIdsByUserId(999L)).isEmpty();
    }

    // ── findByProject_ProjectIdAndRole ────────────────────────────────

    @Test
    @DisplayName("findByProject_ProjectIdAndRole - returns members with matching role")
    void findByProjectIdAndRole_existing_returnsFiltered() {
        memberRepository.save(buildMember(10L, MemberRole.OWNER));
        memberRepository.save(buildMember(20L, MemberRole.EDITOR));
        memberRepository.save(buildMember(30L, MemberRole.EDITOR));

        List<ProjectMember> editors = memberRepository
                .findByProject_ProjectIdAndRole(project.getProjectId(), MemberRole.EDITOR);

        assertThat(editors).hasSize(2);
        assertThat(editors).allMatch(m -> m.getRole() == MemberRole.EDITOR);
    }
}