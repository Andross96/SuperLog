package fr.andross.superlog.Log.Utils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public final class LogEvent {
    private final Map<String, Set<String>> conditions;
    private final String defaultMessage;
    private final Field[] fields;
    private final boolean versionBiggerThan1_13;

    public LogEvent(final Map<String, Set<String>> conditions, final String defaultMessage, final Field[] fields, final boolean versionBiggerThan1_13) {
        this.conditions = conditions;
        this.defaultMessage = defaultMessage;
        this.fields = fields;
        this.versionBiggerThan1_13 = versionBiggerThan1_13;
    }

    public final Set<String> getConditions(final String condition) {
        return conditions.get(condition);
    }

    public final String getDefaultMessage() {
        return defaultMessage;
    }

    public final Field[] getFields() {
        return fields;
    }

    public final boolean isVersionBiggerThan1_13() {
        return versionBiggerThan1_13;
    }
}
