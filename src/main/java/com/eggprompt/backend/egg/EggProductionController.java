package com.eggprompt.backend.egg;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/v1/egg-production", "/api/egg-production", "/egg-production"})
public class EggProductionController {
    private final EggProductionService service;
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EggProductionService.Result create(Authentication auth, @Valid @RequestBody Request request,
            @RequestAttribute(name=JwtAuthenticationFilter.FARM_SCOPE, required=false) String farmScope) {
        return service.create(auth.getName(), request, farmScope);
    }
    @GetMapping("/flocks/{flockId}/summary")
    public EggProductionService.FlockOverview summary(Authentication auth, @PathVariable int flockId,
            @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to,
            @RequestAttribute(name=JwtAuthenticationFilter.FARM_SCOPE, required=false) String farmScope) {
        return service.overview(auth.getName(),flockId,from,to,farmScope);
    }
    @GetMapping
    public java.util.List<EggProductionService.Log> logs(Authentication auth,
            @RequestParam(required=false) String farmId,
            @RequestAttribute(name=JwtAuthenticationFilter.FARM_SCOPE, required=false) String farmScope) {
        return service.logs(auth.getName(),farmScope==null?farmId:farmScope);
    }
    @PutMapping("/{id}")
    public EggProductionService.Result update(Authentication auth,@PathVariable long id,@Valid @RequestBody Request request,
            @RequestAttribute(name=JwtAuthenticationFilter.FARM_SCOPE, required=false) String scope) {
        return service.update(auth.getName(),id,request,scope);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,@PathVariable long id,
            @RequestAttribute(name=JwtAuthenticationFilter.FARM_SCOPE, required=false) String scope) {
        service.delete(auth.getName(),id,scope);
    }
    public record Request(@NotNull @Positive Integer flockId, @NotNull LocalDate recordDate,
            @NotNull @PositiveOrZero Integer gradeJumboQty,
            @NotNull @PositiveOrZero Integer grade0Qty,
            @NotNull @PositiveOrZero Integer grade1Qty,
            @NotNull @PositiveOrZero Integer grade2Qty,
            @NotNull @PositiveOrZero Integer grade3Qty,
            @NotNull @PositiveOrZero Integer grade4Qty,
            @NotNull @PositiveOrZero Integer grade5Qty,
            @NotNull @PositiveOrZero Integer defectiveQty,
            @Size(max=50) String eggLotNumber,
            @Pattern(regexp="DAILY|MORNING|AFTERNOON|EVENING") String collectionRound) {
        public Request {
            collectionRound=collectionRound==null?"DAILY":collectionRound;
        }
        public Request(Integer flockId,LocalDate recordDate,Integer gradeJumboQty,Integer grade0Qty,
                Integer grade1Qty,Integer grade2Qty,Integer grade3Qty,Integer grade4Qty,Integer grade5Qty,
                Integer defectiveQty,String eggLotNumber) {
            this(flockId,recordDate,gradeJumboQty,grade0Qty,grade1Qty,grade2Qty,grade3Qty,grade4Qty,grade5Qty,
                    defectiveQty,eggLotNumber,"DAILY");
        }
    }
}
