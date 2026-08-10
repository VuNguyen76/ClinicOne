package com.clinicone.audit;

import java.util.List;

public record BusinessLogPageResponse(
        List<BusinessLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
