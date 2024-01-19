package com.br.inventory.kafka.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.br.inventory.utils.JsonUtil;

@Component
public class InventoryConsumer {

	private Logger logger = LoggerFactory.getLogger(InventoryConsumer.class);

	@Autowired
	private JsonUtil jsonUtil;
	
	@KafkaListener(
			groupId = "${spring.kafka.consumer.group-id}", 
			topics = "${spring.kafka.topic.inventory-success}"
	)
	public void consumeSuccessEvent(String payload) {
		
		this.logger.info("Receiving success event {} from notify inventory-success topic", payload);
		
		var event = jsonUtil.toEvent(payload);
		
		this.logger.info(event.toString());
	}
	
	@KafkaListener(
			groupId = "${spring.kafka.consumer.group-id}", 
			topics = "${spring.kafka.topic.inventory-fail}"
	)
	public void consumeFailEvent(String payload) {
		
		this.logger.info("Receiving rollback event {} from notify inventory-fail topic", payload);
		
		var event = jsonUtil.toEvent(payload);
		
		this.logger.info(event.toString());
	}
	
	
 

}
