package com.getlancer.media;

import com.getlancer.dto.MediaRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@PreAuthorize("@authorization.routeAllowed(authentication)")
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
      @Valid @RequestBody MediaRequests.Upload body,
      HttpServletRequest r) {
    return service.requestUpload(product, TypedInputs.map(body), r);
  }

  @PostMapping("/developer/products/{product}/media/complete")
  public Map<String, Object> complete(
      @PathVariable UUID product,
      @Valid @RequestBody MediaRequests.Complete body,
      HttpServletRequest r)
      throws IOException {
    return service.complete(product, TypedInputs.map(body), r);
  }

  @GetMapping("/developer/products/{product}/proof")
  public Map<String, Object> proof(@PathVariable UUID product, HttpServletRequest r) {
    return service.proof(product, r);
  }

  @PatchMapping("/developer/products/{product}/media")
  public Map<String, Object> order(
      @PathVariable UUID product,
      @Valid @RequestBody MediaRequests.Order b,
      HttpServletRequest r) {
    return service.order(product, TypedInputs.map(b), r);
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
