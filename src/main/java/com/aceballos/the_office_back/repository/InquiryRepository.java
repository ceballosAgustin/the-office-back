package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Inquiry;
import com.aceballos.the_office_back.entity.InquiryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByInquiryNumber(String inquiryNumber);

    boolean existsByInquiryNumber(String inquiryNumber);

    Page<Inquiry> findByStatus(InquiryStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inquiry i WHERE i.id = :id")
    Optional<Inquiry> findByIdWithLock(@Param("id") Long id);
}

