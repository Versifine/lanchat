package com.lanchat.adapter.in.ws;

import com.lanchat.domain.model.AuthUser;
import com.lanchat.domain.port.TokenIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 握手拦截器：校验查询参数 {@code ?token=<JWT>}。
 *
 * <p>契约：握手校验 token 失败返回 HTTP 401，连接不建立（SEC-03）。
 * 校验通过后把 userId / username 放入会话属性，后续消息的发送者身份
 * 一律以此为准，忽略消息体里的 from/sender 字段（SEC-04）。</p>
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    /** 握手阶段写入会话属性的当前用户 id。 */
    public static final String ATTRIBUTE_USER_ID = "lanchat.ws.userId";
    /** 握手阶段写入会话属性的当前用户名。 */
    public static final String ATTRIBUTE_USERNAME = "lanchat.ws.username";

    private static final String TOKEN_PARAMETER = "token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenIssuer tokenIssuer;

    public JwtHandshakeInterceptor(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request);
        Optional<AuthUser> authUser = tokenIssuer.verify(token);
        if (authUser.isEmpty()) {
            // 注意：不打印 token 内容，只记录失败事实
            log.info("WebSocket 握手被拒绝：token 缺失或无效");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTRIBUTE_USER_ID, authUser.get().id());
        attributes.put(ATTRIBUTE_USERNAME, authUser.get().username());
        log.debug("WebSocket 握手通过: userId={}", authUser.get().id());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 无需处理
    }

    /**
     * 优先从查询参数 ?token= 取 token（契约约定）；兼容 Authorization: Bearer 头（服务端测试更友好）。
     */
    private static String extractToken(ServerHttpRequest request) {
        String rawQuery = request.getURI().getRawQuery();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                int equalsIndex = pair.indexOf('=');
                if (equalsIndex > 0 && TOKEN_PARAMETER.equals(pair.substring(0, equalsIndex))) {
                    try {
                        return URLDecoder.decode(pair.substring(equalsIndex + 1), StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
            }
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
