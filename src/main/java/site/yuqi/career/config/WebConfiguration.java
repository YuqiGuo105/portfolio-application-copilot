package site.yuqi.career.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import site.yuqi.career.security.InternalTokenInterceptor;
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final InternalTokenInterceptor interceptor;
    public WebConfiguration(InternalTokenInterceptor interceptor) { this.interceptor = interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor).addPathPatterns("/internal/**"); }
}
