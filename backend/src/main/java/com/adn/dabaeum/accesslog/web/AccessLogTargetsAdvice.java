package com.adn.dabaeum.accesslog.web;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** @PersonalDataAccess 핸들러의 응답에서 대상 사용자 id 를 모아 요청 속성에 둔다. 본문은 바꾸지 않는다. */
@ControllerAdvice
public class AccessLogTargetsAdvice implements ResponseBodyAdvice<Object> {

    static final String ATTRIBUTE = AccessLogTargetsAdvice.class.getName() + ".captured";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(PersonalDataAccess.class);
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        PersonalDataAccess access = returnType.getMethodAnnotation(PersonalDataAccess.class);
        if (access != null && request instanceof ServletServerHttpRequest servletRequest) {
            servletRequest.getServletRequest().setAttribute(ATTRIBUTE, extract(body, access.targetField()));
        }
        return body;
    }

    static CapturedTargets extract(Object body, String targetField) {
        Object data = component(body, "data");
        if (data == null) {
            return CapturedTargets.NONE;
        }
        List<?> rows = data instanceof List<?> list ? list : List.of(data);
        Set<UUID> ids = new LinkedHashSet<>();
        if (!targetField.isBlank()) {
            for (Object row : rows) {
                if (component(row, targetField) instanceof UUID id) {
                    ids.add(id);
                }
            }
        }
        return new CapturedTargets(ids, rows.size());
    }

    private static Object component(Object record, String name) {
        if (record == null || !record.getClass().isRecord()) {
            return null;
        }
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                try {
                    return component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    return null;
                }
            }
        }
        return null;
    }
}
