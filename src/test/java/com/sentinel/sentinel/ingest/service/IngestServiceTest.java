package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.config.KafkaTopicConfig;
import com.sentinel.sentinel.ingest.dto.EventBatch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IngestServiceTest {

    @Test
    void publishesValidEventsAndQuarantinesInvalidEvents() throws Exception {
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS), eq("acme"), anyString());
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS_QUARANTINE), eq("acme"), anyString());
        var objectMapper = JsonMapper.builder().build();
        var service = new IngestService(new EventValidator(), kafkaTemplate, objectMapper);
        JsonNode events = objectMapper.readTree("""
                [{"type":"LOGIN","user":"alice","sourceIp":"192.0.2.21",
                  "success":true,"timestamp":"2026-11-23T09:00:00Z"},
                 {"type":"UNKNOWN","timestamp":"2026-11-23T09:01:00Z"}]
                """);
        var batch = new EventBatch(List.of(events.get(0), events.get(1)));

        var response = service.ingest("acme", batch);

        assertEquals(1, response.accepted());
        assertEquals(1, response.quarantined());
        assertEquals(1, response.rejections().get(0).index());
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS), eq("acme"), anyString());
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS_QUARANTINE), eq("acme"), anyString());
    }

    @Test
    void publishesOnlyTokenCustomerIdAndUsesItAsKafkaKey() throws Exception {
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS), eq("acme"), anyString());
        var objectMapper = JsonMapper.builder().build();
        var service = new IngestService(new EventValidator(), kafkaTemplate, objectMapper);
        JsonNode event = objectMapper.readTree("""
                {"type":"LOGIN","user":"alice","sourceIp":"192.0.2.21",
                 "success":true,"timestamp":"2026-11-23T09:00:00Z",
                 "customerId":"globex"}
                """);

        var response = service.ingest("acme", new EventBatch(List.of(event)));

        assertEquals(1, response.accepted());
        assertEquals(0, response.quarantined());
        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS), eq("acme"), messageCaptor.capture());
        JsonNode message = objectMapper.readTree(messageCaptor.getValue());
        assertEquals("acme", message.get("customerId").stringValue());
        assertFalse(messageCaptor.getValue().contains("globex"));
    }
}
