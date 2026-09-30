package com.getlancer.products;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

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
  public Map<String, Object> own(HttpServletRequest r) {
    return service.own(r);
  }

  @PostMapping("/developer/products")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.create(b, r);
  }

  @PatchMapping("/developer/products/{id}")
  public Map<String, Object> edit(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.edit(id, b, r);
  }

  @PostMapping("/developer/products/{id}/{action}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public Map<String, Object> action(
      @PathVariable UUID id, @PathVariable String action, HttpServletRequest r) {
    return service.action(id, action, r);
  }

  @PostMapping("/products/{id}/save")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public Map<String, Object> saveProduct(@PathVariable UUID id, HttpServletRequest r) {
    return service.saveProduct(id, r);
  }

  @DeleteMapping("/products/{id}/save")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
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
