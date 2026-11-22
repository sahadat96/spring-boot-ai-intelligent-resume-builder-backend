package com.opticareer.app.platform.json;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class Json {
  private final ObjectMapper mapper;

  public Json(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String canonical(Object value) {
    return canonicalNode(tree(value));
  }

  private String canonicalNode(JsonNode node) {
    if (node.isObject()) {
      java.util.List<String> names = new java.util.ArrayList<>();
      node.propertyNames().forEach(names::add);
      java.util.Collections.sort(names);
      return "{"
          + names.stream()
              .map(n -> write(n) + ":" + canonicalNode(node.get(n)))
              .collect(java.util.stream.Collectors.joining(","))
          + "}";
    }
    if (node.isArray()) {
      java.util.List<String> parts = new java.util.ArrayList<>();
      node.forEach(n -> parts.add(canonicalNode(n)));
      return "[" + String.join(",", parts) + "]";
    }
    return write(node);
  }

  public String write(Object value) {
    return mapper.writeValueAsString(value);
  }

  public JsonNode read(String value) {
    return mapper.readTree(value);
  }

  public <T> T read(String value, Class<T> type) {
    return mapper.readValue(value, type);
  }

  public <T> T convert(Object value, Class<T> type) {
    return mapper.convertValue(value, type);
  }

  public JsonNode tree(Object value) {
    return mapper.valueToTree(value);
  }
}
