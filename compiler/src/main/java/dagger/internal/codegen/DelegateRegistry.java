package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Andy on 07.05.2017.
 */
public class DelegateRegistry {

    UniqueNameSet uniqueNameSet = new UniqueNameSet();
    private Map<Key, String> fieldMap = new HashMap<>();
    private Map<Key, ClassName> typeMap = new HashMap<>();

    String getDelegateFieldName(Key key) {
        if (fieldMap.containsKey(key)) {
            return fieldMap.get(key);
        }
        final String s = MoreTypes.asTypeElement(key.type()).getSimpleName().toString();
        final String uniqueName = uniqueNameSet.getUniqueName(s + "Delegate");
        fieldMap.put(key, uniqueName);
        return uniqueName;
    }

    ClassName getDelegateTypeName(Key key) {
        if (typeMap.containsKey(key)) {
            return typeMap.get(key);
        }
        final String s = key.type().toString();
        final String uniqueName = uniqueNameSet.getUniqueName(s + "Delegate");
        final ClassName className = ClassName.bestGuess(uniqueName);
        typeMap.put(key, className);
        return className;
    }

}
