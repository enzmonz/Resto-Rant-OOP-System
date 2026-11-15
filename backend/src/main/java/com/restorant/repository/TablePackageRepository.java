package com.restorant.repository;
import com.restorant.entity.TablePackage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TablePackageRepository extends JpaRepository<TablePackage, Integer> {
    List<TablePackage> findAllByOrderByCapacityAsc();
}
