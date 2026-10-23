package com.company.platform.control.security;

import com.company.platform.common.internal.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/internal/** 只允许持有共享 token 的 worker 调用。
 * 该 token 不是用户凭证，网关/公网必须屏蔽这些路径。
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public InternalTokenFilter(@Value("${platform.internal-token}") String internalToken) {
        this.expected = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(InternalHeaders.INTERNAL_TOKEN);
        if (provided == null
                || !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":40101,\"message\":\"内部 token 无效\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
