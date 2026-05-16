package com.codesync.comment.repository;

import com.codesync.comment.entity.Comment;
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
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @BeforeEach
    void setUp() { commentRepository.deleteAll(); }

    // ── Helper ────────────────────────────────────────────────────────

    private Comment build(Long projectId, Long fileId, Long authorId,
                          String content, Integer line,
                          Long parentId, boolean resolved, Long snapshotId) {
        return Comment.builder()
                .projectId(projectId).fileId(fileId).authorId(authorId)
                .content(content).lineNumber(line).parentCommentId(parentId)
                .resolved(resolved).snapshotId(snapshotId)
                .build();
    }

    // ── findByFileIdAndParentCommentIdIsNull ──────────────────────────

    @Test
    @DisplayName("findByFileIdAndParentCommentIdIsNull - returns only top-level comments")
    void findByFileIdAndParentIsNull_returnsTopLevel() {
        Comment top1  = commentRepository.save(build(1L,10L,1L,"top1",  5, null, false, null));
        Comment top2  = commentRepository.save(build(1L,10L,2L,"top2", 10, null, false, null));
        commentRepository.save(build(1L,10L,3L,"reply", 5, top1.getCommentId(), false, null));

        List<Comment> results =
                commentRepository.findByFileIdAndParentCommentIdIsNull(10L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getParentCommentId() == null);
    }

    @Test
    @DisplayName("findByFileIdAndParentCommentIdIsNull - returns empty when no top-level comments")
    void findByFileIdAndParentIsNull_noTopLevel_returnsEmpty() {
        Comment top = commentRepository.save(build(1L,10L,1L,"top",5,null,false,null));
        commentRepository.save(build(1L,10L,2L,"reply",5,top.getCommentId(),false,null));

        // file 99 has no comments at all
        assertThat(commentRepository.findByFileIdAndParentCommentIdIsNull(99L)).isEmpty();
    }

    // ── findByFileIdOrderByCreatedAtAsc ───────────────────────────────

    @Test
    @DisplayName("findByFileIdOrderByCreatedAtAsc - returns all comments sorted oldest first")
    void findByFileIdOrderByCreatedAtAsc_returnsAllSorted() {
        Comment top   = commentRepository.save(build(1L,10L,1L,"first", 1, null, false,null));
        Comment reply = commentRepository.save(build(1L,10L,2L,"second",1, top.getCommentId(),false,null));

        List<Comment> results =
                commentRepository.findByFileIdOrderByCreatedAtAsc(10L);

        assertThat(results).hasSize(2);
        // oldest first — top was saved first
        assertThat(results.get(0).getContent()).isEqualTo("first");
    }

    // ── findByProjectIdAndParentCommentIdIsNull ───────────────────────

    @Test
    @DisplayName("findByProjectIdAndParentCommentIdIsNull - returns only project top-level comments")
    void findByProjectIdAndParentIsNull_returnsTopLevel() {
        Comment t1 = commentRepository.save(build(1L,10L,1L,"p1-c1",1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"p1-reply",1,t1.getCommentId(),false,null));
        commentRepository.save(build(2L,20L,3L,"p2-c1",1,null,false,null));

        List<Comment> results =
                commentRepository.findByProjectIdAndParentCommentIdIsNull(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContent()).isEqualTo("p1-c1");
    }

    // ── findByAuthorId ────────────────────────────────────────────────

    @Test
    @DisplayName("findByAuthorId - returns all comments by a given author")
    void findByAuthorId_existing_returnsComments() {
        commentRepository.save(build(1L,10L,5L,"by-author5",1,null,false,null));
        commentRepository.save(build(1L,10L,5L,"also-author5",2,null,false,null));
        commentRepository.save(build(1L,10L,9L,"by-author9",3,null,false,null));

        List<Comment> results = commentRepository.findByAuthorId(5L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getAuthorId().equals(5L));
    }

    @Test
    @DisplayName("findByAuthorId - returns empty for unknown author")
    void findByAuthorId_unknown_returnsEmpty() {
        assertThat(commentRepository.findByAuthorId(99L)).isEmpty();
    }

    // ── findByParentCommentId ─────────────────────────────────────────

    @Test
    @DisplayName("findByParentCommentId - returns all replies to a comment")
    void findByParentCommentId_existing_returnsReplies() {
        Comment parent = commentRepository.save(build(1L,10L,1L,"parent",1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"reply1",1,parent.getCommentId(),false,null));
        commentRepository.save(build(1L,10L,3L,"reply2",1,parent.getCommentId(),false,null));

        List<Comment> replies =
                commentRepository.findByParentCommentId(parent.getCommentId());

        assertThat(replies).hasSize(2);
        assertThat(replies).allMatch(c ->
                c.getParentCommentId().equals(parent.getCommentId()));
    }

    @Test
    @DisplayName("findByParentCommentId - returns empty when comment has no replies")
    void findByParentCommentId_noReplies_returnsEmpty() {
        Comment parent = commentRepository.save(build(1L,10L,1L,"alone",1,null,false,null));

        assertThat(commentRepository.findByParentCommentId(parent.getCommentId())).isEmpty();
    }

    // ── findByFileIdAndLineNumber ─────────────────────────────────────

    @Test
    @DisplayName("findByFileIdAndLineNumber - returns comments on specific line")
    void findByFileIdAndLineNumber_existing_returnsComments() {
        commentRepository.save(build(1L,10L,1L,"line5-c1", 5,null,false,null));
        commentRepository.save(build(1L,10L,2L,"line5-c2", 5,null,false,null));
        commentRepository.save(build(1L,10L,3L,"line10-c1",10,null,false,null));

        List<Comment> results =
                commentRepository.findByFileIdAndLineNumber(10L, 5);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getLineNumber().equals(5));
    }

    @Test
    @DisplayName("findByFileIdAndLineNumber - returns empty for line with no comments")
    void findByFileIdAndLineNumber_noComments_returnsEmpty() {
        assertThat(commentRepository.findByFileIdAndLineNumber(10L, 999)).isEmpty();
    }

    // ── findByFileIdAndResolved ───────────────────────────────────────

    @Test
    @DisplayName("findByFileIdAndResolved(false) - returns only unresolved comments")
    void findByFileIdAndResolved_false_returnsUnresolved() {
        commentRepository.save(build(1L,10L,1L,"open",   1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"closed", 2,null,true, null));

        List<Comment> unresolved =
                commentRepository.findByFileIdAndResolved(10L, false);

        assertThat(unresolved).hasSize(1);
        assertThat(unresolved.get(0).getResolved()).isFalse();
    }

    @Test
    @DisplayName("findByFileIdAndResolved(true) - returns only resolved comments")
    void findByFileIdAndResolved_true_returnsResolved() {
        commentRepository.save(build(1L,10L,1L,"open",   1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"closed", 2,null,true, null));

        List<Comment> resolved =
                commentRepository.findByFileIdAndResolved(10L, true);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).getResolved()).isTrue();
    }

    // ── countByFileId ─────────────────────────────────────────────────

    @Test
    @DisplayName("countByFileId - returns total comment count for file")
    void countByFileId_returnsCorrectCount() {
        commentRepository.save(build(1L,10L,1L,"A",1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"B",2,null,false,null));
        commentRepository.save(build(1L,10L,3L,"C",3,null,true, null));

        assertThat(commentRepository.countByFileId(10L)).isEqualTo(3);
    }

    @Test
    @DisplayName("countByFileId - returns 0 for file with no comments")
    void countByFileId_noComments_returnsZero() {
        assertThat(commentRepository.countByFileId(99L)).isZero();
    }

    // ── countByFileIdAndResolved ──────────────────────────────────────

    @Test
    @DisplayName("countByFileIdAndResolved(false) - returns unresolved count")
    void countByFileIdAndResolved_false_returnsUnresolvedCount() {
        commentRepository.save(build(1L,10L,1L,"A",1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"B",2,null,false,null));
        commentRepository.save(build(1L,10L,3L,"C",3,null,true, null));

        assertThat(commentRepository.countByFileIdAndResolved(10L, false)).isEqualTo(2);
    }

    @Test
    @DisplayName("countByFileIdAndResolved(true) - returns resolved count")
    void countByFileIdAndResolved_true_returnsResolvedCount() {
        commentRepository.save(build(1L,10L,1L,"A",1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"B",2,null,true, null));

        assertThat(commentRepository.countByFileIdAndResolved(10L, true)).isEqualTo(1);
    }

    // ── findBySnapshotId ──────────────────────────────────────────────

    @Test
    @DisplayName("findBySnapshotId - returns comments linked to snapshot")
    void findBySnapshotId_existing_returnsComments() {
        commentRepository.save(build(1L,10L,1L,"snap-c1",1,null,false,5L));
        commentRepository.save(build(1L,10L,2L,"snap-c2",2,null,false,5L));
        commentRepository.save(build(1L,10L,3L,"other",  3,null,false,9L));

        List<Comment> results = commentRepository.findBySnapshotId(5L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getSnapshotId().equals(5L));
    }

    @Test
    @DisplayName("findBySnapshotId - returns empty for unknown snapshot")
    void findBySnapshotId_unknown_returnsEmpty() {
        assertThat(commentRepository.findBySnapshotId(999L)).isEmpty();
    }

    // ── searchByContent ───────────────────────────────────────────────

    @Test
    @DisplayName("searchByContent - returns case-insensitive content matches")
    void searchByContent_keyword_returnsCaseInsensitiveMatches() {
        commentRepository.save(build(1L,10L,1L,"Fix the NullPointerException here",1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"This is fine",2,null,false,null));
        commentRepository.save(build(1L,10L,3L,"Another nullpointerexception issue",3,null,false,null));

        List<Comment> results =
                commentRepository.searchByContent(1L, "nullpointerexception");

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("searchByContent - returns empty when no content matches")
    void searchByContent_noMatch_returnsEmpty() {
        commentRepository.save(build(1L,10L,1L,"All good here",1,null,false,null));

        assertThat(commentRepository.searchByContent(1L, "xyznotfound")).isEmpty();
    }

    @Test
    @DisplayName("searchByContent - does not return comments from another project")
    void searchByContent_differentProject_notReturned() {
        commentRepository.save(build(2L,20L,1L,"Fix this bug",1,null,false,null));

        List<Comment> results = commentRepository.searchByContent(1L, "fix");

        assertThat(results).isEmpty();
    }

    // ── findMentions ──────────────────────────────────────────────────

    @Test
    @DisplayName("findMentions - returns comments that @mention a username")
    void findMentions_existing_returnsComments() {
        commentRepository.save(build(1L,10L,1L,"@johndoe please fix this",1,null,false,null));
        commentRepository.save(build(1L,10L,2L,"@johndoe look here too",  2,null,false,null));
        commentRepository.save(build(1L,10L,3L,"@janedoe check this",     3,null,false,null));

        List<Comment> results =
                commentRepository.findMentions(1L, "johndoe");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getContent().contains("@johndoe"));
    }

    @Test
    @DisplayName("findMentions - returns empty when username not mentioned")
    void findMentions_noMention_returnsEmpty() {
        commentRepository.save(build(1L,10L,1L,"No mentions here",1,null,false,null));

        assertThat(commentRepository.findMentions(1L, "ghostuser")).isEmpty();
    }
}