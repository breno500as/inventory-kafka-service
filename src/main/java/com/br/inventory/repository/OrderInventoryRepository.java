package com.br.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.inventory.model.OrderInventory;

@Repository
public interface OrderInventoryRepository extends JpaRepository<OrderInventory, Long> {
	
	Boolean existsByOrderIdAndTransactionId(String orderId, String transactionId);

	List<OrderInventory> findByOrderIdAndTransactionId(String orderId, String transactionId);

}
