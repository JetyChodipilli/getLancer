package com.getlancer.products;

import com.getlancer.dto.ProductRequests;
import com.getlancer.responses.ProjectResponses;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class ProductsController {
  private final ProductService service;

  public ProductsController(ProductService service) {
    this.service = service;
  }

  @GetMapping("/products")
  public Map<String, Object> search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "") String technology,
      @RequestParam(defaultValue = "") String projectType,
      @RequestParam(defaultValue = "") String availability,
      @RequestParam(defaultValue = "") String builder,
      @RequestParam(defaultValue = "false") boolean liveDemo,
      @RequestParam(defaultValue = "newest") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.search(
        q, category, technology, projectType, availability, builder, liveDemo, sort, page, size);
  }

  @GetMapping("/products/{slug}")
  public Map<String, Object> detail(@PathVariable String slug) {
    return service.detail(slug);
  }

  @GetMapping("/builders/{slug}")
  public Map<String, Object> builder(@PathVariable String slug) {
    return service.builder(slug);
  }

  @GetMapping("/builders/{slug}/reviews")
  public Map<String, Object> reviews(@PathVariable String slug, HttpServletRequest r) {
    return service.reviews(slug, r);
  }

  @GetMapping("/developer/products")
  public ProjectResponses.Owned own(HttpServletRequest r) {
    return service.own(r);
  }

  @PostMapping("/developer/products")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody ProductRequests.Mutation b, HttpServletRequest r) {
    return service.create(TypedInputs.map(b), r);
  }

  @PatchMapping("/developer/products/{id}")
  public Map<String, Object> edit(
      @PathVariable UUID id, @Valid @RequestBody ProductRequests.Mutation b, HttpServletRequest r) {
    return service.edit(id, TypedInputs.map(b), r);
  }

  @PostMapping("/developer/products/{id}/{action}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Map<String, Object> action(
      @PathVariable UUID id, @PathVariable String action, HttpServletRequest r) {
    return service.action(id, action, r);
  }

  @PostMapping("/products/{id}/save")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Map<String, Object> saveProduct(@PathVariable UUID id, HttpServletRequest r) {
    return service.saveProduct(id, r);
  }

  @DeleteMapping("/products/{id}/save")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Map<String, Object> unsave(@PathVariable UUID id, HttpServletRequest r) {
    return service.unsave(id, r);
  }

  @GetMapping("/me/saved-products")
  public Map<String, Object> saved(HttpServletRequest r) {
    return service.saved(r);
  }

  @GetMapping("/categories")
  public Map<String, Object> categories() {
    return service.categories();
  }

  @GetMapping("/technologies")
  public Map<String, Object> technologies() {
    return service.technologies();
  }
}
