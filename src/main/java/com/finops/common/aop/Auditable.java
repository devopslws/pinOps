package com.finops.common.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 감사 로그 자동 기록 어노테이션.
 * Service 또는 Controller 메서드에 부착.
 *
 * 사용 예: @Auditable(action = "LOGIN", resource = "AUTH")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String resource() default "";
}
