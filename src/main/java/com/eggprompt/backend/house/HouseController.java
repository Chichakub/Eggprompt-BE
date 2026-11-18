package com.eggprompt.backend.house;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/houses")
@RequiredArgsConstructor
public class HouseController {

    private final HouseService houseService;

    @GetMapping
    public List<HouseResponse> list(
            Authentication authentication,
            @RequestParam(required = false) String farmId,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope
    ) {
        return houseService.list(authentication.getName(), farmScope == null ? farmId : farmScope);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseResponse create(
            Authentication authentication,
            @Valid @RequestBody HouseRequest request,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope
    ) {
        return houseService.create(authentication.getName(), request, farmScope);
    }

    @PutMapping("/{id}")
    public HouseResponse update(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody HouseRequest request,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope
    ) {
        return houseService.update(authentication.getName(), id, request, farmScope);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Integer id,
                       @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        houseService.delete(authentication.getName(), id, farmScope);
    }
}