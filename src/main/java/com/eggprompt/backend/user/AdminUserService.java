package com.eggprompt.backend.user;

import com.eggprompt.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.eggprompt.backend.user.AdminUserController.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private static final List<String> ROLES = List.of("Super Admin", "Farm Admin", "Farm Staff", "Finance Staff");

    private User actor(String username) {
        User u = users.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        if (!u.isActive() || !List.of("Super Admin", "Farm Admin").contains(u.getUserRole()))
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์จัดการผู้ใช้");
        return u;
    }
    private boolean superAdmin(User u) { return "Super Admin".equals(u.getUserRole()); }
    private Specification<User> scope(User actor) {
        return (root, query, cb) -> {
            var alive = cb.isNull(root.get("deletedAt"));
            if (superAdmin(actor)) return alive;
            if (actor.getFarmId() == null) return cb.disjunction();
            return cb.and(alive, cb.equal(root.get("farmId"), actor.getFarmId()),
                    cb.notEqual(root.get("userRole"), "Super Admin"));
        };
    }
    @Transactional(readOnly = true)
    public UserPage list(String username, String search, String role, int page, int size) {
        User actor = actor(username);
        if (page < 0 || size < 1 || size > 100 || search.length() > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "ค่าการค้นหาหรือแบ่งหน้าไม่ถูกต้อง");
        Specification<User> base = scope(actor);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", users.count(base));
        for (String r : ROLES) counts.put(r, users.count(base.and((root,q,cb) -> cb.equal(root.get("userRole"), r))));
        Specification<User> filtered = base;
        if (!search.isBlank()) {
            String value = search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            filtered = filtered.and((root,q,cb) -> cb.like(cb.lower(root.get("username")), "%" + value + "%", '\\'));
        }
        if (!role.isBlank()) {
            validateRole(role);
            filtered = filtered.and((root,q,cb) -> cb.equal(root.get("userRole"), role));
        }
        Page<User> result = users.findAll(filtered, PageRequest.of(page, size, Sort.by("userId").ascending()));
        return new UserPage(result.getContent().stream().map(UserView::from).toList(),
                result.getTotalElements(), result.getTotalPages(), page, counts);
    }
    private void validateRole(String role) {
        if (!ROLES.contains(role)) throw new ApiException(HttpStatus.BAD_REQUEST, "สิทธิ์ผู้ใช้ไม่ถูกต้อง");
    }
    private void allowedRole(User actor, String role) {
        validateRole(role);
        if (!superAdmin(actor) && !List.of("Farm Staff", "Finance Staff").contains(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "Farm Admin จัดการได้เฉพาะ Farm Staff และ Finance Staff ในฟาร์มของตน");
    }
    private User target(User actor, Integer id) {
        User target = users.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ไม่พบผู้ใช้"));
        if (actor.getUserId().equals(id)) throw new ApiException(HttpStatus.BAD_REQUEST, "ไม่สามารถเปลี่ยนสิทธิ์ ระงับ หรือลบบัญชีของตนเองจากหน้านี้");
        if (!superAdmin(actor) && (actor.getFarmId() == null || !Objects.equals(actor.getFarmId(), target.getFarmId())
                || !List.of("Farm Staff", "Finance Staff").contains(target.getUserRole())))
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์จัดการบัญชีนี้");
        return target;
    }
    private void save(User user) {
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT, "บันทึกไม่สำเร็จ กรุณาตรวจสอบชื่อผู้ใช้ซ้ำหรือข้อมูลฟาร์ม"); }
    }
    public UserView create(String username, CreateUserRequest request) {
        return create(username, request, false);
    }
    public UserView createForFarm(String username, CreateUserRequest request) {
        return create(username, request, "Farm Admin".equals(request.role()));
    }

    private UserView create(String username, CreateUserRequest request, boolean mustChangePassword) {
    User actor = actor(username);
        allowedRole(actor, request.role());
        String farmId = superAdmin(actor) ? request.farmId() : actor.getFarmId();
        if (farmId != null) farmId = farmId.trim();
        if (farmId != null && farmId.isEmpty()) farmId = null;
        if (!"Super Admin".equals(request.role()) && farmId == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "กรุณาระบุฟาร์มสำหรับผู้ใช้นี้");
        String password = request.password();
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("(?s).*[A-Z].*") || !password.matches("(?s).*[a-z].*")
                || !password.matches("(?s).*[0-9].*") || !password.matches("(?s).*[^A-Za-z0-9\\s].*"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "รหัสผ่านต้องมีอย่างน้อย 8 ตัว พิมพ์ใหญ่ พิมพ์เล็ก ตัวเลข และอักขระพิเศษ");
        if (users.existsByUsername(request.username())) throw new ApiException(HttpStatus.CONFLICT, "ชื่อผู้ใช้นี้ถูกใช้งานแล้ว");
        User user = new User();
        user.setUsername(request.username()); user.setUserRole(request.role());
        user.setFarmId("Super Admin".equals(request.role()) ? null : farmId);
        user.setPasswordHash(encoder.encode(password)); user.setActive(true);
        user.setMustChangePassword(mustChangePassword);
        save(user);
        return UserView.from(user);
    }
    public UserView update(String username, Integer id, UpdateUserRequest request) {
        User actor = actor(username);
        User user = target(actor, id);
        allowedRole(actor, request.role());
        if (!"Super Admin".equals(request.role()) && user.getFarmId() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "บัญชีนี้ยังไม่ได้ผูกกับฟาร์ม จึงเปลี่ยนเป็น Role นี้ไม่ได้");
        if (!user.getUsername().equals(request.username()) && users.existsByUsername(request.username()))
            throw new ApiException(HttpStatus.CONFLICT, "ชื่อผู้ใช้นี้ถูกใช้งานแล้ว");
        user.setUsername(request.username()); user.setUserRole(request.role()); user.setActive(request.active());
        save(user);
        return UserView.from(user);
    }
    public void delete(String username, Integer id) {
        User user = target(actor(username), id);
        user.setDeletedAt(Instant.now()); user.setActive(false); save(user);
    }
}
