package com.ecommerce.agent.copyseo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 文案生成记录的数据访问接口。 */
public interface CopySeoGenerationRepository extends JpaRepository<CopySeoGeneration, String> {
    List<CopySeoGeneration> findTop20ByStatusInAndNextPollAtLessThanEqualOrderByCreatedAtAsc(
            Collection<GenerationStatus> statuses, Instant now);
}
