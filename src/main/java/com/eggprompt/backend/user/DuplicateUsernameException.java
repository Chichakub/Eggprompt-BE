package com.eggprompt.backend.user;

import com.eggprompt.backend.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateUsernameException extends ApiException {
    private final String username;

    public DuplicateUsernameException(String username) {
        super(HttpStatus.CONFLICT, "ชื่อผู้ใช้นี้ถูกใช้งานแล้ว");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
