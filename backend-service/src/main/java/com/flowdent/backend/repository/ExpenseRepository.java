package com.flowdent.backend.repository;

import com.flowdent.backend.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByDateBetweenOrderByDateDescIdDesc(LocalDate from, LocalDate to);
    List<Expense> findAllByOrderByDateDescIdDesc();
    boolean existsBySupplierId(Long supplierId);
}
