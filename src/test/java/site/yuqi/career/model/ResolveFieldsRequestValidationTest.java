package site.yuqi.career.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveFieldsRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsLongAtsQuestionsAndOptions() {
        List<String> countryOptions = IntStream.range(0, 250)
                .mapToObj(index -> "Country or region " + index)
                .toList();
        ResolveFieldsRequest request = new ResolveFieldsRequest("upstart-application", List.of(
                new ResolveFieldsRequest.Field(
                        "upstart-current-location",
                        "What is your current location? " + "Additional legal context. ".repeat(40),
                        "country",
                        "combobox",
                        countryOptions)));

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
