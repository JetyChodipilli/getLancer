package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.dto.LabRequests;
import jakarta.validation.Validation;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LabRequestsTest {
  private final ObjectMapper json=new ObjectMapper();
  @Test void concreteInputsPreserveObjectWireFormatAndCanonicalRetryHash() throws Exception {
    var first=json.readValue("{\"operationId\":\"echo\",\"inputs\":{\"z\":\"last\",\"a\":\"first\"}}",LabRequests.Request.class);
    var second=json.readValue("{\"operationId\":\"echo\",\"inputs\":{\"a\":\"first\",\"z\":\"last\"}}",LabRequests.Request.class);
    assertEquals(Map.of("a","first","z","last"),first.inputs().values());
    assertEquals(json.writeValueAsString(first),json.writeValueAsString(second));
    assertTrue(json.valueToTree(first).path("inputs").isObject());
    assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->json.readValue("{\"operationId\":\"echo\",\"inputs\":{\"a\":{\"nested\":true}}}",LabRequests.Request.class));
  }
  @Test void validationTraversesConcreteFieldNamesValuesAndCollectionBounds() {
    try(var factory=Validation.buildDefaultValidatorFactory()) {
      var validator=factory.getValidator();
      assertTrue(validator.validate(new LabRequests.Request("echo",LabRequests.Inputs.from(Map.of("message","Hello")))).isEmpty());
      for(var values:java.util.List.of(Map.of("invalid name","x"),Map.of("message","x".repeat(4097)),IntStream.range(0,21).boxed().collect(Collectors.toMap(i->"field"+i,i->"x"))))
        assertFalse(validator.validate(new LabRequests.Request("echo",LabRequests.Inputs.from(values))).isEmpty());
      assertFalse(validator.validate(new LabRequests.Request("echo",null)).isEmpty());
    }
  }
}
