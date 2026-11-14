package com.restorant.repository;
import com.restorant.entity.Inquiry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InquiryRepository extends JpaRepository<Inquiry,Long>{List<Inquiry> findTop200ByOrderByCreatedAtDesc();}
