package com.lanchat.adapter.in.ws;

import com.lanchat.domain.model.AuthUser;
import com.lanchat.domain.port.TokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket 握手 JWT 校验单元测试（SEC-03 / EV-SEC-03）。
 */
class JwtHandshakeInterceptorTest {

    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    @Test
    void validQueryTokenShouldPassHandshakeAndExposeUserAttributes() {
        TokenIssuer tokenIssuer = mock(TokenIssuer.class);
        when(tokenIssuer.verify("good-token")).thenReturn(Optional.of(new AuthUser(5L, "bob")));
        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(tokenIssuer);
        ServerHttpRequest request = requestWithUri("http://localhost:8081/ws/chat?token=good-token");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes)
                .containsEntry(JwtHandshakeInterceptor.ATTRIBUTE_USER_ID, 5L)
                .containsEntry(JwtHandshakeInterceptor.ATTRIBUTE_USERNAME, "bob");
    }

    @Test
    void invalidOrMissingTokenShouldRejectHandshakeWith401() {
        TokenIssuer tokenIssuer = mock(TokenIssuer.class);
        when(tokenIssuer.verify(anyString())).thenReturn(Optional.empty());
        when(tokenIssuer.verify(null)).thenReturn(Optional.empty());
        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(tokenIssuer);

        for (String uri : new String[]{
                "http://localhost:8081/ws/chat",
                "http://localhost:8081/ws/chat?token=",
                "http://localhost:8081/ws/chat?token=forged"}) {
            ServerHttpResponse mockedResponse = mock(ServerHttpResponse.class);
            boolean accepted = interceptor.beforeHandshake(
                    requestWithUri(uri), mockedResponse, wsHandler, new HashMap<>());

            assertThat(accepted).as("握手应被拒绝: %s", uri).isFalse();
            verify(mockedResponse).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void bearerAuthorizationHeaderShouldAlsoBeAccepted() {
        TokenIssuer tokenIssuer = mock(TokenIssuer.class);
        when(tokenIssuer.verify("header-token")).thenReturn(Optional.of(new AuthUser(6L, "carol")));
        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(tokenIssuer);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8081/ws/chat"));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer header-token");
        when(request.getHeaders()).thenReturn(headers);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(JwtHandshakeInterceptor.ATTRIBUTE_USER_ID, 6L);
    }

    @Test
    void urlEncodedTokenShouldBeDecodedBeforeVerification() {
        TokenIssuer tokenIssuer = mock(TokenIssuer.class);
        when(tokenIssuer.verify("abc+def/ghi=")).thenReturn(Optional.of(new AuthUser(7L, "dave")));
        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(tokenIssuer);
        ServerHttpRequest request = requestWithUri("http://localhost:8081/ws/chat?token=abc%2Bdef%2Fghi%3D");

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertThat(accepted).isTrue();
        verify(tokenIssuer).verify("abc+def/ghi=");
    }

    private static ServerHttpRequest requestWithUri(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }
}
