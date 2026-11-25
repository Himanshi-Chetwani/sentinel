package com.sentinel.sentinel.enrich;

import com.sentinel.sentinel.config.KafkaTopicConfig;
import com.sentinel.sentinel.ingest.service.EventValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class EventConsumerTest {

    @Test
    void enrichesAndPersistsValidatedEvent() {
        var objectMapper = JsonMapper.builder().build();
        var eventRepository = mock(EventRepository.class);
        var badIpList = mock(BadIpList.class);
        var kafkaTemplate = mock(KafkaTemplate.class);
        when(badIpList.contains("203.0.113.7")).thenReturn(true);
        var consumer = new EventConsumer(
                objectMapper, new EventValidator(), badIpList, eventRepository, kafkaTemplate);

        consumer.consume("""
                {"customerId":"acme","event":{"type":"LOGIN","user":"alice",
                 "sourceIp":"203.0.113.7","success":true,"timestamp":"2026-11-23T09:00:00Z"}}
                """);

        var storedEventCaptor = ArgumentCaptor.forClass(StoredEvent.class);
        verify(eventRepository).save(storedEventCaptor.capture());
        StoredEvent storedEvent = storedEventCaptor.getValue();
        assertEquals("acme", storedEvent.getCustomerId());
        assertEquals("LOGIN", storedEvent.getType());
        assertEquals("alice", storedEvent.getUser());
        assertTrue(storedEvent.isKnownBadIp());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void persistsFirewallEventFields() {
        var objectMapper = JsonMapper.builder().build();
        var eventRepository = mock(EventRepository.class);
        var badIpList = mock(BadIpList.class);
        var kafkaTemplate = mock(KafkaTemplate.class);
        var consumer = new EventConsumer(
                objectMapper, new EventValidator(), badIpList, eventRepository, kafkaTemplate);

        consumer.consume("""
                {"customerId":"acme","event":{"type":"FIREWALL","sourceIp":"192.0.2.21",
                 "destinationIp":"10.0.0.10","destinationPort":443,"action":"ALLOW",
                 "timestamp":"2026-11-23T09:04:00Z"}}
                """);

        var storedEventCaptor = ArgumentCaptor.forClass(StoredEvent.class);
        verify(eventRepository).save(storedEventCaptor.capture());
        StoredEvent storedEvent = storedEventCaptor.getValue();
        assertEquals("FIREWALL", storedEvent.getType());
        assertEquals("10.0.0.10", storedEvent.getDestinationIp());
        assertEquals(443, storedEvent.getDestinationPort());
        assertEquals("ALLOW", storedEvent.getAction());
    }

    @Test
    void quarantinesMalformedMessageWithRawMessageAndReason() throws Exception {
        var objectMapper = JsonMapper.builder().build();
        var eventRepository = mock(EventRepository.class);
        var badIpList = mock(BadIpList.class);
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS_QUARANTINE), eq(null), anyString());
        var consumer = new EventConsumer(
                objectMapper, new EventValidator(), badIpList, eventRepository, kafkaTemplate);
        String rawMessage = "{not-json}";

        consumer.consume(rawMessage);

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS_QUARANTINE), eq(null), messageCaptor.capture());
        var quarantine = objectMapper.readTree(messageCaptor.getValue());
        assertEquals(rawMessage, quarantine.get("rawMessage").stringValue());
        assertEquals("Unreadable message on events topic", quarantine.get("reason").stringValue());
        verifyNoInteractions(eventRepository, badIpList);
    }

    @Test
    void quarantinesMissingCustomerIdWithoutSaving() throws Exception {
        var objectMapper = JsonMapper.builder().build();
        var eventRepository = mock(EventRepository.class);
        var badIpList = mock(BadIpList.class);
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS_QUARANTINE), eq(null), anyString());
        var consumer = new EventConsumer(
                objectMapper, new EventValidator(), badIpList, eventRepository, kafkaTemplate);
        String rawMessage = """
                {"event":{"type":"LOGIN","user":"alice","sourceIp":"192.0.2.21",
                 "success":true,"timestamp":"2026-11-23T09:00:00Z"}}
                """;

        consumer.consume(rawMessage);

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.EVENTS_QUARANTINE), eq(null), messageCaptor.capture());
        var quarantine = objectMapper.readTree(messageCaptor.getValue());
        assertEquals(rawMessage, quarantine.get("rawMessage").stringValue());
        assertTrue(quarantine.get("reason").stringValue().contains("customerId"));
        verifyNoInteractions(eventRepository, badIpList);
    }

    @Test
    void databaseFailurePropagatesForKafkaRetry() {
        var objectMapper = JsonMapper.builder().build();
        var eventRepository = mock(EventRepository.class);
        var badIpList = mock(BadIpList.class);
        var kafkaTemplate = mock(KafkaTemplate.class);
        var databaseFailure = new IllegalStateException("database unavailable");
        doThrow(databaseFailure).when(eventRepository).save(org.mockito.ArgumentMatchers.any());
        var consumer = new EventConsumer(
                objectMapper, new EventValidator(), badIpList, eventRepository, kafkaTemplate);

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                consumer.consume("""
                        {"customerId":"acme","event":{"type":"LOGIN","user":"alice",
                         "sourceIp":"192.0.2.21","success":true,"timestamp":"2026-11-23T09:00:00Z"}}
                        """));

        assertEquals(databaseFailure, thrown);
    }
}
