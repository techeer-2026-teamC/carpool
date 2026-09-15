package com.techeer.carpool.domain.discovery.dto;

import com.techeer.carpool.domain.post.dto.PostSummaryResponse;
import java.util.List;

public record DiscoveryPage(List<PostSummaryResponse> items, String nextCursor, boolean hasNext) {}
