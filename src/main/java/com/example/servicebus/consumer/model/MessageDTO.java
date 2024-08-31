package com.example.servicebus.consumer.model;


public record MessageDTO(int sleepTime, String message) {
  public MessageDTO(String sleepTimeStr) {
    this(Integer.parseInt(sleepTimeStr, 10), "");
  }
}
