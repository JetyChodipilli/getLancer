package com.getlancer.profiles;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class AvatarController {
  private final AvatarService avatars;

  public AvatarController(AvatarService avatars) {
    this.avatars = avatars;
  }

  @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AvatarService.Picture upload(@RequestParam("file") MultipartFile file, HttpServletRequest request)
      throws IOException {
    return avatars.upload(file, request);
  }

  @DeleteMapping("/me/avatar")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(HttpServletRequest request) {
    avatars.remove(request);
  }

  @GetMapping("/profile-avatars/{id}")
  public ResponseEntity<byte[]> read(@PathVariable UUID id, HttpServletRequest request) {
    return avatars.read(id, request);
  }
}
