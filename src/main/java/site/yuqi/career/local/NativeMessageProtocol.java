package site.yuqi.career.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class NativeMessageProtocol {
    private static final int MAX_MESSAGE_BYTES = 1_048_576;
    private final ObjectMapper mapper;

    NativeMessageProtocol(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    JsonNode read(InputStream input) throws IOException {
        byte[] header = input.readNBytes(4);
        if (header.length == 0) return null;
        if (header.length != 4) throw new EOFException("Incomplete native-message header");
        int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length <= 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException("Native message exceeds the allowed size");
        }
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) throw new EOFException("Incomplete native-message payload");
        return mapper.readTree(payload);
    }

    void write(OutputStream output, Object value) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(value);
        if (payload.length > MAX_MESSAGE_BYTES) throw new IOException("Native response exceeds the allowed size");
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array());
        output.write(payload);
        output.flush();
    }
}
