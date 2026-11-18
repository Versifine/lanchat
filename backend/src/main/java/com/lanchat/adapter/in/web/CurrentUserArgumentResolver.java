package com.lanchat.adapter.in.web;

import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.AuthUser;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser} 参数解析器：从请求属性中取出 JWT 过滤器写入的登录用户。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object authUser = webRequest.getAttribute(AuthAttributes.AUTH_USER, RequestAttributes.SCOPE_REQUEST);
        if (authUser instanceof AuthUser user) {
            return user;
        }
        // 理论上被 JwtAuthenticationFilter 拦截，不会走到这里；兜底返回 1001
        throw new BusinessException(ErrorCodes.UNAUTHORIZED, "未登录或登录已过期");
    }
}
