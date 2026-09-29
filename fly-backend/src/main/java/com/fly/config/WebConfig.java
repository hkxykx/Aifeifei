package com.fly.config;

import com.fly.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CORS、鉴权拦截器、静态资源（/uploads、/admin）注册。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private FlyProperties properties;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 对齐 FastAPI 的 redirect_slashes 行为：尾斜杠与无斜杠等价
        configurer.setUseTrailingSlashMatch(true);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(properties.corsOriginList().toArray(new String[0]))
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploads = Paths.get(properties.getUploadsDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploads);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + uploads, e);
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploads + "/");

        Path adminDist = Paths.get(properties.getAdminDist()).toAbsolutePath().normalize();
        if (Files.isDirectory(adminDist)) {
            registry.addResourceHandler("/admin/**")
                    .addResourceLocations("file:" + adminDist + "/");
        }
    }
}
