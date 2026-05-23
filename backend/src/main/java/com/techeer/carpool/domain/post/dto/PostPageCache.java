package com.techeer.carpool.domain.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Page<PostSummaryResponse>는 PageImpl 역직렬화 이슈로 Redis 직렬화 불가.
 * content 리스트 + totalElements만 캐싱하고, PageImpl은 서비스 레이어에서 재조립.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostPageCache {
    private List<PostSummaryResponse> content;
    private long totalElements;
}
