package site.yuqi.career.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseStorageClientTest {
    @Test
    void resolvesSignedStoragePathsWithoutTreatingQueryAsPath() {
        String origin = "https://project.supabase.co";

        assertThat(SupabaseStorageClient.resolveSignedUrl(origin,
                "/object/upload/sign/career-resumes/resume.pdf?token=abc_def-123"))
                .hasToString("https://project.supabase.co/storage/v1/object/upload/sign/career-resumes/resume.pdf?token=abc_def-123");
        assertThat(SupabaseStorageClient.resolveSignedUrl(origin,
                "/storage/v1/object/sign/career-resumes/resume.pdf?token=download"))
                .hasToString("https://project.supabase.co/storage/v1/object/sign/career-resumes/resume.pdf?token=download");
        assertThat(SupabaseStorageClient.resolveSignedUrl(origin,
                "https://cdn.example/resume.pdf?token=complete"))
                .hasToString("https://cdn.example/resume.pdf?token=complete");
    }
}
