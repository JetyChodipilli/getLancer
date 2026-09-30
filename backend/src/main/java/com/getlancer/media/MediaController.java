package com.getlancer.media;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.services.s3.model.*;

@RestController
@RequestMapping("/api/v1")
public class MediaController {
  private final MediaService service;

  public MediaController(MediaService service) {
    this.service = service;
  }

  @PostMapping(
      value = "/developer/products/{product}/media",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> upload(
      @PathVariable UUID product,
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "") String altText,
      HttpServletRequest r)
      throws IOException {
    return service.upload(product, file, altText, r);
  }

  @PostMapping("/developer/products/{product}/media/upload-request")
  public Map<String, Object> requestUpload(
      @PathVariable UUID product,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body,
      HttpServletRequest r) {
    return service.requestUpload(product, body, r);
  }

  @PostMapping("/developer/products/{product}/media/complete")
  public Map<String, Object> complete(
      @PathVariable UUID product,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body,
      HttpServletRequest r)
      throws IOException {
    return service.complete(product, body, r);
  }

  @GetMapping("/developer/products/{product}/proof")
  public Map<String, Object> proof(@PathVariable UUID product, HttpServletRequest r) {
    return service.proof(product, r);
  }

  @PatchMapping("/developer/products/{product}/media")
  public Map<String, Object> order(
      @PathVariable UUID product,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.order(product, b, r);
  }

  @DeleteMapping("/developer/products/{product}/media/{id}")
  public Map<String, Object> remove(
      @PathVariable UUID product, @PathVariable UUID id, HttpServletRequest r) {
    return service.remove(product, id, r);
  }

  @GetMapping("/media/{id}")
  public ResponseEntity<byte[]> get(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "full") String variant,
      HttpServletRequest r) {
    return service.get(id, variant, r);
  }
}
