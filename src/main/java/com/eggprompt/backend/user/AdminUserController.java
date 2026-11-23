package com.eggprompt.backend.user;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;

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
                           boolean active, Instant createdAt, Instant lastLoginAt, String email, boolean emailVerificationPending, boolean mustChangePassword) {
        static UserView from(User u) {
            return new UserView(u.getUserId(), u.getUsername(), u.getUserRole(), u.getFarmId(),
                    u.isActive(), u.getCreatedAt(), u.getLastLoginAt(), u.getEmail(),u.isEmailVerificationPending(),Boolean.TRUE.equals(u.getMustChangePassword()));
        }
    }
    public record UserPage(List<UserView> content, long totalElements, int totalPages,
                           int page, Map<String, Long> counts) {}
    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,24}", message = "ชื่อผู้ใช้ต้องยาว 4–24 ตัว และใช้ตัวอักษรอังกฤษ ตัวเลข จุด ขีดกลาง หรือขีดล่าง") String username,
            @NotBlank String password, @NotBlank String role, String farmId,
            @Email @Size(max=255) String email) {
        public CreateUserRequest { email=email==null||email.isBlank()?null:email.trim().toLowerCase(java.util.Locale.ROOT); }
        public CreateUserRequest(String username,String password,String role,String farmId){this(username,password,role,farmId,null);}
    }
    public record TemporaryPassword(String username,String temporaryPassword) {}
    public record UpdateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,24}", message = "ชื่อผู้ใช้ไม่ถูกต้อง") String username,
            @NotBlank String role, @NotNull Boolean active) {}

    @GetMapping
    public UserPage list(Authentication auth, @RequestParam(defaultValue = "") String search,
                         @RequestParam(defaultValue = "") String role,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size,
                         @RequestParam(required = false) String farmId,
                         @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        return service.list(auth.getName(), search, role, page, size, farmScope == null ? farmId : farmScope);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(Authentication auth, @Valid @RequestBody CreateUserRequest request,
                           @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        if (farmScope != null) {
            request = new CreateUserRequest(request.username(), request.password(), request.role(), farmScope,request.email());
        }
        return service.create(auth.getName(), request);
    }
    @PostMapping("/{id}/temporary-password")
    public TemporaryPassword temporaryPassword(Authentication auth,@PathVariable Integer id,
            @RequestAttribute(name=JwtAuthenticationFilter.FARM_SCOPE,required=false) String farmScope) {
        return service.reissueTemporaryPassword(auth.getName(),id,farmScope);
    }
    @PutMapping("/{id}")
    public UserView update(Authentication auth, @PathVariable Integer id, @Valid @RequestBody UpdateUserRequest request,
                           @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        return service.update(auth.getName(), id, request, farmScope);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Integer id,
                       @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        service.delete(auth.getName(), id, farmScope);
    }
}
