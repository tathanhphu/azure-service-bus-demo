package com.example.servicebus.consumer.config;

import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.spring.messaging.checkpoint.Checkpointer;
import com.azure.spring.messaging.servicebus.support.ServiceBusMessageHeaders;
import com.example.servicebus.consumer.model.MessageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.util.StringUtils;

import static com.azure.spring.messaging.AzureHeaders.CHECKPOINTER;
import static java.lang.Thread.sleep;

@Slf4j
@Configuration
public class MessageConsumer {

  private final boolean autoComplete;

  private final Duration maxAutoLockRenewDuration;

  private final ObjectMapper objectMapper;

  public MessageConsumer(
                         @Value("${spring.cloud.stream.servicebus.bindings.consume-in-0.consumer.auto-complete}") boolean autoComplete,
                         @Value("${spring.cloud.stream.servicebus.bindings.consume-in-0.consumer.max-auto-lock-renew-duration:PT0s}") Duration maxAutoLockRenewDuration) {
    this.objectMapper = new ObjectMapper();
    this.autoComplete = autoComplete;
    this.maxAutoLockRenewDuration = maxAutoLockRenewDuration;
  }
// NEW

  @Bean
  public Consumer<Message<String>> consume() {
    return message -> {
      Checkpointer checkpointer = (Checkpointer) message.getHeaders().get(CHECKPOINTER);
      if (!autoComplete && Objects.isNull(checkpointer)) {
        log.error("Checkpoint is null");
        return;
      }
      // https://github.com/Azure/azure-sdk-for-java/blob/fd4ed4402038bf529d02642ed037a4669b396f1a/sdk/servicebus/azure-messaging-servicebus/src/main/java/com/azure/messaging/servicebus/models/ServiceBusReceiveMode.java#L50
      ServiceBusReceivedMessageContext context = message.getHeaders().get(ServiceBusMessageHeaders.RECEIVED_MESSAGE_CONTEXT, ServiceBusReceivedMessageContext.class);
      assert context != null;
      logMessageHeaders(message);
      try {
        log.info("Received message: {}", message.getPayload());
        MessageDTO messageDTO = parseMessage(message.getPayload());
        Thread.sleep((messageDTO.sleepTime() ) * 1000);
        if (StringUtils.hasText(messageDTO.message())) {

            throw new RuntimeException(messageDTO.message());
        }
        // see Message lock duration: https://docs.microsoft.com/en-us/azure/service-bus-messaging/message-transfers-locks-settlement

        if (!autoComplete) {
          context.complete();
        }

      } catch (RuntimeException rte) {
        log.warn("Abandoning a message: {}", message.getPayload(), rte);
        context.abandon();
        if (!autoComplete) {
          // Move the message to deal letter queue  <code>context.deadLetter();<code>
          context.abandon();
        } else {
          throw rte;
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      log.info("Message processed: {}", message.getHeaders().get(ServiceBusMessageHeaders.MESSAGE_ID));
    };
  }
//*/

  private MessageDTO parseMessage(String message) {
    try {
      return objectMapper.readValue(message, MessageDTO.class);
    } catch (JsonProcessingException e) {
      return new MessageDTO(message);
    }
  }
  private void logMessageHeaders(Message<String> message) {
    log.info("MESSAGE_ID: {}", message.getHeaders().get(ServiceBusMessageHeaders.MESSAGE_ID));
    log.info("DELIVERY_COUNT: {}", message.getHeaders().get(ServiceBusMessageHeaders.DELIVERY_COUNT));
    log.info("LOCKED_UNTIL: {}", message.getHeaders().get(ServiceBusMessageHeaders.LOCKED_UNTIL));
  }

  @PostConstruct
  public void init() {
    log.info("===================== SUMMARY ====================");
    log.info("Auto-complete is set to: {}", autoComplete);
    log.info("Max auto renewal is set to: {} seconds", maxAutoLockRenewDuration.toSeconds());
    log.info("====================== END =======================");
  }

  @Bean
  public Consumer<ErrorMessage> myErrorHandler() {
    return v -> {
      // send SMS notification code

      log.error("Consumer<ErrorMessage> {}", v);
    };
  }

  // OLD
/*
  @Bean
  public Consumer<Message<String>> consume() throws InterruptedException {
    return message -> {
      Checkpointer checkpointer = (Checkpointer) message.getHeaders().get(CHECKPOINTER);
      if (Objects.isNull(checkpointer)) {
        log.error("Checkpoint is null");
        return;
      }
      checkpointer.success()
        .doOnSuccess(
          s -> {
            logMessageHeaders(message);
            log.info("Received message: {}", message.getPayload());
            MessageDTO messageDTO = parseMessage(message.getPayload());
            try {
              sleep(messageDTO.sleepTime() * 1000L);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            log.info("after sleep");
            if (StringUtils.hasText(messageDTO.message())) {
              try {
                throw new Exception(messageDTO.message());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          })
        .doOnError(e -> log.error("Error found", e))
        .block();
    };
  }
  //*/


}
