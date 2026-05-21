package com.techeer.carpool.domain.comment;

import tools.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.comment.dto.CommentRequest;
import com.techeer.carpool.domain.comment.entity.Comment;
import com.techeer.carpool.domain.comment.repository.CommentRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CommentRepository commentRepository;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Mock RefreshTokenRedisRepository refreshTokenRedisRepository;
    @Mock BlacklistRedisRepository blacklistRedisRepository;

    private Long memberId;
    private Long otherMemberId;
    private Long postId;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        postRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = memberRepository.save(Member.builder()
                .email("test@test.com")
                .password("password")
                .nickname("테스터")
                .build());
        memberId = member.getId();
        token = "Bearer " + jwtTokenProvider.createAccessToken(memberId);

        Member other = memberRepository.save(Member.builder()
                .email("other@test.com")
                .password("password")
                .nickname("다른유저")
                .build());
        otherMemberId = other.getId();
        otherToken = "Bearer " + jwtTokenProvider.createAccessToken(otherMemberId);

        Post post = postRepository.save(Post.builder()
                .memberId(memberId)
                .title("테스트 카풀")
                .departureLocation("강남역")
                .destinationLocation("판교역")
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(3)
                .description("테스트")
                .autoAccept(false)
                .build());
        postId = post.getId();
    }

    @Test
    @DisplayName("댓글 작성 성공")
    void createComment_success() throws Exception {
        CommentRequest request = new CommentRequest();
        setField(request, "content", "탑승 신청합니다!");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("탑승 신청합니다!"))
                .andExpect(jsonPath("$.data.nickname").value("테스터"))
                .andExpect(jsonPath("$.data.postId").value(postId));
    }

    @Test
    @DisplayName("댓글 목록 조회 성공")
    void getComments_success() throws Exception {
        commentRepository.save(Comment.builder()
                .postId(postId).memberId(memberId).content("첫 번째 댓글").build());
        commentRepository.save(Comment.builder()
                .postId(postId).memberId(memberId).content("두 번째 댓글").build());

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content").value("첫 번째 댓글"))
                .andExpect(jsonPath("$.data[1].content").value("두 번째 댓글"));
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 댓글 작성 시 404")
    void createComment_postNotFound() throws Exception {
        CommentRequest request = new CommentRequest();
        setField(request, "content", "댓글");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", 999L)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_001"));
    }

    @Test
    @DisplayName("댓글 삭제 성공 - 본인")
    void deleteComment_success() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .postId(postId).memberId(memberId).content("삭제될 댓글").build());

        mockMvc.perform(delete("/api/v1/comments/{commentId}", comment.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("댓글이 삭제되었습니다."));
    }

    @Test
    @DisplayName("댓글 삭제 실패 - 타인")
    void deleteComment_forbidden() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .postId(postId).memberId(memberId).content("남의 댓글").build());

        mockMvc.perform(delete("/api/v1/comments/{commentId}", comment.getId())
                        .header("Authorization", otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENT_002"));
    }

    @Test
    @DisplayName("빈 내용으로 댓글 작성 시 400")
    void createComment_emptyContent() throws Exception {
        CommentRequest request = new CommentRequest();
        setField(request, "content", "");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
