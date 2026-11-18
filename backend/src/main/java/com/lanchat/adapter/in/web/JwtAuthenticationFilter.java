package com.lanchat.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanchat.adapter.in.web.dto.ApiErrorResponse;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.AuthUser;
import com.lanchat.domain.port.TokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * REST 鉴权过滤器：校验 {@code Authorization: Bearer <JWT>}。
 *
 * <p>行为（SEC-03）：</p>
 * <ul>
 *   <li>只拦截 {@code /api/**}；注册/登录两个公开接口与 OPTIONS 预检直接放行；</li>
 *   <li>token 缺失、伪造、过期统一返回 HTTP 401 + {@code {"code":1001,...}}；</li>
 *   <li>校验通过后把 {@link AuthUser} 写入请求属性，Controller 通过 {@code @CurrentUser} 获取；</li>
 *   <li>下载接口额外兼容 {@code ?token=}（浏览器直接点下载链接无法自定义请求头），
 *       属于对契约的前向兼容扩展，不改变任何字段语义；</li>
 *   <li>日志不打印 token（SEC-09）。</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> PUBLIC_API_PATHS = Set.of("/api/auth/register", "/api/auth/login");
    private static final Pattern DOWNLOAD_PATH_PATTERN = Pattern.compile("^/api/files/\\d+/download$");

    private final TokenIssuer tokenIssuer;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(TokenIssuer tokenIssuer, ObjectMapper objectMapper) {
        this.tokenIssuer = tokenIssuer;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        return PUBLIC_API_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        Optional<AuthUser> authUser = tokenIssuer.verify(token);
        if (authUser.isEmpty()) {
            log.debug("REST 鉴权失败: path={}", request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(AuthAttributes.AUTH_USER, authUser.get());
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        // 浏览器点击 <a download> 无法携带 Authorization 头，下载接口兼容查询参数 token
        if (DOWNLOAD_PATH_PATTERN.matcher(request.getRequestURI()).matches()) {
            return request.getParameter("token");
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                new ApiErrorResponse(ErrorCodes.UNAUTHORIZED, "未登录或登录已过期"));
    }
}
