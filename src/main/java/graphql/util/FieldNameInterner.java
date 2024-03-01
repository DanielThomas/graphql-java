package graphql.util;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import graphql.Internal;

/**
 * Interner allowing hot-path identity based equality for String.equals and field definition lookup by name.
 */
@Internal
public class FieldNameInterner {

    private FieldNameInterner() {
    }

    public static final Interner<String> INTERNER = Interners.newBuilder()
            .weak()
            .build();

    public static String intern(String name) {
        return INTERNER.intern(name);
    }

}
