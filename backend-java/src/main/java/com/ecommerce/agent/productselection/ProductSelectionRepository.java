package com.ecommerce.agent.productselection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

/** 选品记录的数据访问接口。 */
public interface ProductSelectionRepository extends JpaRepository<ProductSelection, String> {
    Optional<ProductSelection> findBySearchIdAndSelectedSourceProductId(String searchId, String sourceProductId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select selection from ProductSelection selection where selection.selectionId = :id")
    Optional<ProductSelection> findForConfirmation(@Param("id") String id);
}
