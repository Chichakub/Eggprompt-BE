package com.eggprompt.backend.feed;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FeedLogController {

    private final FeedLogService service;

    @PostMapping({"/api/feed-logs", "/feed-logs"})
    @ResponseStatus(HttpStatus.CREATED)
    public FeedLogService.Result create(
            Authentication authentication,
            @Valid @RequestBody FeedLogRequest request,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false)
            String farmScope
    ) {
        return service.create(authentication.getName(), request, farmScope);
    }

    @GetMapping({"/api/houses/{id}/feed-logs", "/houses/{id}/feed-logs"})
    public List<FeedLogService.Result> findByHouse(
            Authentication authentication,
            @PathVariable("id") int houseId,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false)
            String farmScope
    ) {
        return service.findByHouse(authentication.getName(), houseId, farmScope);
    }
}