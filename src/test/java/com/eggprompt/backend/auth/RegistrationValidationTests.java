package com.eggprompt.backend.auth;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegistrationValidationTests {
 @Test void usernameRulesAreEnforcedAtBackendAndConsentIsRequired(){
  try(var factory=Validation.buildDefaultValidatorFactory()){
   var validator=factory.getValidator();
   assertTrue(validator.validate(new RegisterRequest("farm.owner","Password123!","owner@example.com",true)).isEmpty());
   for(String name:new String[]{"abc","name with spaces","a".repeat(25),"ชื่อไทย"})
    assertFalse(validator.validate(new RegisterRequest(name,"Password123!","owner@example.com",true)).isEmpty());
   assertFalse(validator.validate(new RegisterRequest("farm.owner","Password123!","owner@example.com",false)).isEmpty());
  }
 }
}
