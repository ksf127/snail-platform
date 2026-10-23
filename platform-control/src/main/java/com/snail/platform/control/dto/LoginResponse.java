package com.snail.platform.control.dto;

public record LoginResponse(String token, UserView user) {
}
