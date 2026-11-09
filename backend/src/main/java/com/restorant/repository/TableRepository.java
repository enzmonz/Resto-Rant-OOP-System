package com.restorant.repository;
import com.restorant.entity.RestaurantTable;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TableRepository extends JpaRepository<RestaurantTable, Long> {
    List<RestaurantTable> findAllByOrderByCapacityAscIdAsc();
}
