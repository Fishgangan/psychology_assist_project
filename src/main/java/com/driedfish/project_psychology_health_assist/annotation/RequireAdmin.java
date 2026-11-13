package com.driedfish.project_psychology_health_assist.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)// 表示该注解只能写在方法上
@Retention(RetentionPolicy.RUNTIME)// 表示该注解在运行时保留
@Documented
public @interface RequireAdmin {
}
