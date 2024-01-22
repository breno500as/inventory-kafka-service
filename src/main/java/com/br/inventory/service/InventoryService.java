package com.br.inventory.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.br.inventory.dtos.Event;
import com.br.inventory.dtos.History;
import com.br.inventory.dtos.Order;
import com.br.inventory.dtos.OrderProducts;
import com.br.inventory.enums.SagaStatusEnum;
import com.br.inventory.kafka.producer.OrchestratorProducer;
import com.br.inventory.model.Inventory;
import com.br.inventory.model.OrderInventory;
import com.br.inventory.repository.InventoryRepository;
import com.br.inventory.repository.OrderInventoryRepository;
import com.br.inventory.utils.JsonUtil;

import jakarta.validation.ValidationException;

@Service
public class InventoryService {

	private Logger logger = LoggerFactory.getLogger(InventoryService.class);

	private static final String CURRENT_SOURCE = "INVENTORY_SERVICE";

	@Autowired
	private JsonUtil jsonUtil;

	@Autowired
	private InventoryRepository inventoryRepository;

	@Autowired
	private OrchestratorProducer orchestratorProducer;

	@Autowired
	private OrderInventoryRepository orderInventoryRepository;

	public void trySuccessEvent(Event event) {

		try {
			this.checkCurrentValidation(event);
			this.createOrderInventory(event);
			this.updateInventory(event.getPayload());
			this.handleSuccess(event);

		} catch (Exception e) {
			this.logger.error("Error trying update inventory!", e);
			this.handleFailCurrentNotExecuted(event, CURRENT_SOURCE);

		}

		// Envia para o orquestrador a informação de sucesso para atualização do evento
		this.orchestratorProducer.sendEvent(jsonUtil.toJson(event));
	}

	public void rollbackEvent(Event event) {

		event.setStatus(SagaStatusEnum.FAIL);

		event.setSource(CURRENT_SOURCE);

		try {
			this.returnInventoryToPreviousValues(event);
			this.addHistory(event, "Rollback executed!");
		} catch (Exception e) {
			addHistory(event, "Rollback not executed for inventory: " + e.getMessage());
		}

		this.orchestratorProducer.sendEvent(jsonUtil.toJson(event));

	}

	private void returnInventoryToPreviousValues(Event event) {

		this.orderInventoryRepository
				.findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
				.forEach(orderInventory -> {
					var inventory = orderInventory.getInventory();
					inventory.setAvailable(orderInventory.getOldQuantity());

					this.inventoryRepository.save(inventory);

					this.logger.info("Restored inventory for order {} from {} to {}", event.getPayload().getId(),
							orderInventory.getNewQuantity(), inventory.getAvailable());
				});
		;
	}

	private void updateInventory(Order payload) {

		payload.getProducts().forEach(p -> {
			var inventory = this.findInventoryByProductCode(p);

			if (p.getQuantity() > inventory.getAvailable()) {
				throw new ValidationException("Product out of stock!");
			}

			inventory.setAvailable(inventory.getAvailable() - p.getQuantity());

			this.inventoryRepository.save(inventory);
		});

	}

	private void handleFailCurrentNotExecuted(Event event, String message) {

		event.setStatus(SagaStatusEnum.ROLLBACK_PENDING);
		event.setSource(CURRENT_SOURCE);

		this.addHistory(event, "Failed to update inventory: " + message);

	}

	private void createOrderInventory(Event event) {
		event.getPayload().getProducts().forEach(op -> {

			var inventory = this.findInventoryByProductCode(op);

			var orderInventory = new OrderInventory();
			orderInventory.setInventory(inventory);
			orderInventory.setOrderId(event.getPayload().getId());
			orderInventory.setTransactionId(event.getTransactionId());
			orderInventory.setOldQuantity(inventory.getAvailable());
			orderInventory.setOrderQuantity(op.getQuantity());
			orderInventory.setNewQuantity(inventory.getAvailable() - op.getQuantity());

			this.orderInventoryRepository.save(orderInventory);
		});

	}

	private Inventory findInventoryByProductCode(OrderProducts op) {
		return this.inventoryRepository.findByProductCode(op.getProduct().getCode())
				.orElseThrow(() -> new ValidationException("Inventory not found by product code"));
	}

	private void checkCurrentValidation(Event event) {

		// Faz o tratamento para evitar inconsistência de dados referente ao kafka
		// enviar mais de uma
		/// vez a mesma mensagem
		if (this.orderInventoryRepository.existsByOrderIdAndTransactionId(event.getPayload().getId(),
				event.getPayload().getTransactionId())) {
			throw new ValidationException("There's another transactionId for this order!");
		}

	}

	private void handleSuccess(Event event) {

		event.setStatus(SagaStatusEnum.SUCCESS);
		event.setSource(CURRENT_SOURCE);

		this.addHistory(event, "Inventory updated success!");

	}

	private void addHistory(Event event, String message) {

		History h = new History();
		h.setCreatedAt(LocalDateTime.now());
		h.setSource(event.getSource());
		h.setStatus(event.getStatus());
		h.setMessage(message);

		event.addToHistory(h);
	}

}
