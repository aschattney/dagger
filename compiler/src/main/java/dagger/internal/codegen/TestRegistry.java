package dagger.internal.codegen;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import static com.google.common.io.ByteStreams.toByteArray;

/**
 * Created by Andy on 07.05.2017.
 */
public class TestRegistry {

    private List<EncodedClass> encodedClasses = new ArrayList<>();
    private boolean debug = false;

    public void addEncodedClass(ClassName className, JavaFile javaFile) throws IOException {
        final JavaFileObject javaFileObject = javaFile.toJavaFileObject();
        final InputStream inputStream = javaFileObject.openInputStream();
        final String encodedClass = java.util.Base64.getEncoder().encodeToString(toByteArray(inputStream));
        Iterable<String> result = Splitter.fixedLength(65000).split(encodedClass);
        String[] parts = Iterables.toArray(result, String.class);
        final String name = className.packageName() + "." + className.simpleName();
        encodedClasses.add(new EncodedClass(name, parts));
    }

    public ClassName getClassName() {
        return ClassName.bestGuess("dagger.TestTrigger");
    }

    public Iterator<EncodedClass> iterator() {
        return encodedClasses.iterator();
    }

    public static class EncodedClass {

        public EncodedClass(String qualifiedName, String[] encodedParts) {
            this.qualifiedName = qualifiedName;
            this.encodedParts = encodedParts;
        }

        public String qualifiedName;
        public String[] encodedParts;
    }
}
