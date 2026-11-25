package me.nexthub.sool.auth;

import org.springframework.context.annotation.Configuration;
import me.nexthub.sool.file.FileController;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    private final FileController files;

    public WebConfig(AuthInterceptor authInterceptor, FileController files) {
        this.authInterceptor = authInterceptor;
        this.files = files;
    }

    /** 올린 사진·동영상을 그대로 내려 준다. 동영상의 구간 요청(Range)도 처리된다. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/files/**")
                .addResourceLocations(files.directory().toUri().toString())
                .setCachePeriod(60 * 60 * 24 * 30);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
