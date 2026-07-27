package de.uni_stuttgart.ils.reqif4j.attributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Value of a DATE attribute. ReqIF declares DATE as {@code xsd:dateTime}, so
 * the raw string is additionally parsed into a {@link OffsetDateTime}.
 *
 * {@link #getValue()} keeps returning the raw string; use
 * {@link #getDateTime()} for the parsed value.
 */
public class AttributeValueDate extends AttributeValue {

    private final OffsetDateTime dateTime;

    public AttributeValueDate(String value, AttributeDefinition type) {
        super(value, type);

        this.dateTime = parse(value);
    }

    /**
     * @return the raw value as written in the document (may be null)
     */
    @Override
    public Object getValue() {
        return (String) this.value;
    }

    /**
     * @return the parsed timestamp, or null if the attribute has no value or
     *         the value is not a valid date. Values without a zone offset are
     *         read as UTC, date-only values as start of day UTC.
     */
    public OffsetDateTime getDateTime() {
        return this.dateTime;
    }

    /**
     * @return the parsed date without time, or null if unparseable
     */
    public LocalDate getDate() {
        return this.dateTime == null ? null : this.dateTime.toLocalDate();
    }

    private static OffsetDateTime parse(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }
        String date = value.trim();

        try {
            return OffsetDateTime.parse(date);
        } catch (DateTimeParseException withoutOffset) {
            // fall through
        }
        try {
            return LocalDateTime.parse(date).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException notADateTime) {
            // fall through
        }
        try {
            return LocalDate.parse(date).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }
}
