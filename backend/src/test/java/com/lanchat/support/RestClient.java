package com.lanchat.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST 集成测试的轻量客户端：统一处理 JSON、Bearer token、multipart 上传与文件下载，
 * 断言里直接拿 HTTP 状态码 + 原始 JSON 字符串，避免过度封装。
 */
public final class RestClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TestRestTemplate rest;

    public RestClient(TestRestTemplate rest) {
        this.rest = rest;
    }

    /** 一次 REST 调用的原始结果。 */
    public record ApiResponse(int status, String body, HttpHeaders headers) {

        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (Exception e) {
                throw new IllegalStateException("响应不是合法 JSON: " + body, e);
            }
        }
    }

    /** 下载结果（字节 + 响应头）。 */
    public record BytesResponse(int status, byte[] body, HttpHeaders headers) {
    }

    /** 注册/登录后的认证信息。 */
    public record AuthInfo(long id, String username, String token) {
    }

    public ApiResponse postJson(String path, Map<String, ?> body, String token) {
        HttpHeaders headers = jsonHeaders(token);
        String payload;
        try {
            payload = JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化请求体", e);
        }
        return exchange(path, HttpMethod.POST, new HttpEntity<>(payload, headers));
    }

    /** 发送故意构造的非法 JSON（测试 400 分支）。 */
    public ApiResponse postRawJson(String path, String rawBody, String token) {
        return exchange(path, HttpMethod.POST, new HttpEntity<>(rawBody, jsonHeaders(token)));
    }

    public ApiResponse get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return exchange(path, HttpMethod.GET, new HttpEntity<>(headers));
    }

    /** 注册用户并返回 id + token；失败直接抛断言错误（测试前置条件）。 */
    public AuthInfo register(String username, String password) {
        return register(username, password, null);
    }

    public AuthInfo register(String username, String password, String nickname) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        if (nickname != null) {
            body.put("nickname", nickname);
        }
        ApiResponse response = postJson("/api/auth/register", body, null);
        if (response.status() != 200) {
            throw new AssertionError("注册测试用户失败: username=" + username
                    + ", status=" + response.status() + ", body=" + response.body());
        }
        JsonNode json = response.json();
        return new AuthInfo(json.path("user").path("id").asLong(),
                json.path("user").path("username").asText(),
                json.path("token").asText());
    }

    /** 上传文件（multipart/form-data）。 */
    public ApiResponse upload(String token, String filename, byte[] content, Long receiverId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        if (receiverId != null) {
            parts.add("to", Long.toString(receiverId));
        }
        return exchange("/api/files", HttpMethod.POST, new HttpEntity<>(parts, headers));
    }

    public BytesResponse download(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        var response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        return new BytesResponse(response.getStatusCode().value(), response.getBody(), response.getHeaders());
    }

    private ApiResponse exchange(String path, HttpMethod method, HttpEntity<?> entity) {
        var response = rest.exchange(path, method, entity, String.class);
        return new ApiResponse(response.getStatusCode().value(), response.getBody(), response.getHeaders());
    }

    private static HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
