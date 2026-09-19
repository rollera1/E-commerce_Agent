package com.ecommerce.agent.copyseo;

import org.springframework.data.jpa.repository.JpaRepository;

/** 文案生成记录的数据访问接口。 */
public interface CopySeoGenerationRepository extends JpaRepository<CopySeoGeneration, String> {
}
