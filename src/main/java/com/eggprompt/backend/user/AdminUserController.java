package com.eggprompt.backend.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/v1/admin/users", "/api/admin/users"})
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService service;
    public record UserView(Integer userId, String username, String role, String farmId,
                           boolean active, Instant createdAt, Instant lastLoginAt) {
        static UserView from(User u) {
            return new UserView(u.getUserId(), u.getUsername(), u.getUserRole(), u.getFarmId(),
                    u.isActive(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }
    public record UserPage(List<UserView> content, long totalElements, int totalPages,
                           int page, Map<String, Long> counts) {}
    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,24}", message = "ชื่อผู้ใช้ต้องยาว 4–24 ตัว และใช้ตัวอักษรอังกฤษ ตัวเลข จุด ขีดกลาง หรือขีดล่าง") String username,
            @NotBlank String password, @NotBlank String role, String farmId) {}
    public record UpdateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,24}", message = "ชื่อผู้ใช้ไม่ถูกต้อง") String username,
            @NotBlank String role, @NotNull Boolean active) {}

    @GetMapping
    public UserPage list(Authentication auth, @RequestParam(defaultValue = "") String search,
                         @RequestParam(defaultValue = "") String role,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size) {
        return service.list(auth.getName(), search, role, page, size);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(Authentication auth, @Valid @RequestBody CreateUserRequest request) {
        return service.create(auth.getName(), request);
    }
    @PutMapping("/{id}")
    public UserView update(Authentication auth, @PathVariable Integer id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(auth.getName(), id, request);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Integer id) { service.delete(auth.getName(), id); }
}
