package com.eggprompt.backend.farm;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping({"/v1/admin/farms", "/api/admin/farms", "/admin/farms"})
@RequiredArgsConstructor
public class AdminFarmController {
    private final AdminFarmService service;
    private final FarmService farmService;
    @GetMapping
    public AdminFarmService.FarmPage list(Authentication auth, @RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="all") String status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        return service.list(auth.getName(), search, status, page, size);
    }
    @PutMapping("/{id}")
    public FarmController.FarmCreatedResponse update(Authentication auth, @PathVariable String id,
            @jakarta.validation.Valid @RequestBody CreateFarmRequest request) {
        return new FarmController.FarmCreatedResponse(farmService.updateAsAdmin(auth.getName(), id, request));
    }

    @GetMapping("/summary")
    public AdminFarmService.Summary summary(Authentication auth) { return service.summary(auth.getName()); }
    @GetMapping("/{id}")
    public AdminFarmService.FarmView detail(Authentication auth, @PathVariable String id) {
        return service.detail(auth.getName(), id);
    }
}
