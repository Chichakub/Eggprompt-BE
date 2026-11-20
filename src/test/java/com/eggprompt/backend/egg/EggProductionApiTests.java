package com.eggprompt.backend.egg;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EggProductionApiTests {
    @Test void summaryAcceptsDateRangeAndAuthenticatedFarmScope() throws Exception {
        var service=mock(EggProductionService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new EggProductionController(service)).build();
        mvc.perform(get("/api/egg-production/flocks/1/summary").principal(new UsernamePasswordAuthenticationToken("staff",null))
                .param("from","2026-01-01").param("to","2026-01-31")
                .requestAttr(com.eggprompt.backend.auth.JwtAuthenticationFilter.FARM_SCOPE,"F1"))
                .andExpect(status().isOk());
        verify(service).overview("staff",1,java.time.LocalDate.of(2026,1,1),java.time.LocalDate.of(2026,1,31),"F1");
    }
    @Test void validPayloadCreatedAndNegativeOrMissingCountsRejected() throws Exception {
        var service=mock(EggProductionService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new EggProductionController(service)).build();
        var auth=new UsernamePasswordAuthenticationToken("staff",null);
        String payload="""
            {"flockId":1,"recordDate":"2026-01-01","gradeJumboQty":0,"grade0Qty":10,
            "grade1Qty":50,"grade2Qty":30,"grade3Qty":0,"grade4Qty":0,"grade5Qty":0,"defectiveQty":2}
            """;
        mvc.perform(post("/api/egg-production").principal(auth).contentType("application/json").content(payload)).andExpect(status().isCreated());
        verify(service).create(eq("staff"),any(),isNull());
        clearInvocations(service);
        mvc.perform(post("/api/egg-production").principal(auth).contentType("application/json")
                .content(payload.replace("\"defectiveQty\":2","\"defectiveQty\":-1"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/egg-production").principal(auth).contentType("application/json")
                .content(payload.replace(",\"defectiveQty\":2",""))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
