package site.yuqi.career.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveFieldsRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsLongAtsQuestionsAndOptions() {
        ResolveFieldsRequest request = new ResolveFieldsRequest("upstart-application", List.of(
                new ResolveFieldsRequest.Field(
                        "upstart-sponsorship",
                        "Do you need immigration support? " + "Additional legal context. ".repeat(40),
                        "sponsorship_required",
                        "combobox",
                        List.of("Yes", "No", "Protected veteran definition. ".repeat(25)))));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void stillRejectsUnboundedAtsMetadata() {
        ResolveFieldsRequest request = new ResolveFieldsRequest("upstart-application", List.of(
                new ResolveFieldsRequest.Field(
                        "field",
                        "Question ".repeat(600),
                        "other",
                        "combobox",
                        List.of("Option"))));

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("fields[0].label"));
    }
}
