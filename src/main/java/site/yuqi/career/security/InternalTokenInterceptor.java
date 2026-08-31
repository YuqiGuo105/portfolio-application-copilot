package site.yuqi.career.security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import site.yuqi.career.config.CareerProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {
    private final byte[] expected;
    public InternalTokenInterceptor(CareerProperties properties) { this.expected = bytes(properties.internalToken()); }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (expected.length > 0 && MessageDigest.isEqual(expected, bytes(request.getHeader("X-Internal-Token")))) return true;
        response.setStatus(HttpStatus.UNAUTHORIZED.value()); response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\"}"); return false;
    }
    private static byte[] bytes(String value) { return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8); }
}
