package site.yuqi.career.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NativeMessageProtocolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final NativeMessageProtocol protocol = new NativeMessageProtocol(mapper);

    @Test
    void roundTripsChromeNativeMessageFraming() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        protocol.write(output, mapper.readTree("{\"type\":\"resolve_fields\",\"requestId\":\"r-1\"}"));

        var decoded = protocol.read(new ByteArrayInputStream(output.toByteArray()));

        assertThat(decoded.path("type").asText()).isEqualTo("resolve_fields");
        assertThat(decoded.path("requestId").asText()).isEqualTo("r-1");
    }
}
