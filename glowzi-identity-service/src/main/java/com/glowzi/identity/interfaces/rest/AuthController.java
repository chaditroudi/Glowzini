package com.glowzi.identity.interfaces.rest;

import com.glowzi.identity.application.LoginUserUseCase;
import com.glowzi.identity.application.RegisterUserUseCase;
import com.glowzi.identity.application.command.LoginCommand;
import com.glowzi.identity.application.command.RegisterUserCommand;
import com.glowzi.identity.application.result.AuthResult;
import com.glowzi.identity.interfaces.rest.dto.AuthResponse;
import com.glowzi.identity.interfaces.rest.dto.LoginRequest;
import com.glowzi.identity.interfaces.rest.dto.RegisterUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller — the ONLY place that knows about HTTP.
 *
 * DDD RULE: The controller's job is:
 * 1. Validate the HTTP request (via @Valid)
 * 2. Map the DTO → Command (application-layer type)
 * 3. Call the use case
 * 4. Map the Result → Response DTO
 *
 * Zero business logic here.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUserUseCase loginUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterUserRequest request) {
        // 1. Map DTO → Command
        RegisterUserCommand command = new RegisterUserCommand(
                request.getFullName(),
                request.getPhone(),
                request.getPassword(),
                request.getRole().name(),
                request.getPreferredLanguage()
        );

        // 2. Execute use case
        AuthResult result = registerUserUseCase.execute(command);

        // 3. Map Result → Response DTO
        return new AuthResponse(result.userId(), result.role(), result.token());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(
                request.getPhone(),
                request.getPassword()
        );

        AuthResult result = loginUserUseCase.execute(command);

        return new AuthResponse(result.userId(), result.role(), result.token());
    }
}
