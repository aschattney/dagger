package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.google.common.io.ByteStreams.toByteArray;

/**
 * Created by Andy on 07.05.2017.
 */
public class TestRegistry {

    private List<EncodedClass> encodedClasses = new ArrayList<>();

    public void addEncodedClass(ClassName className, JavaFile javaFile) throws IOException {
        final JavaFileObject javaFileObject = javaFile.toJavaFileObject();
        final InputStream inputStream = javaFileObject.openInputStream();
        final String encodedClass = Base64.encode(toByteArray(inputStream));
        final String name = className.packageName() + "." + className.simpleName();
        encodedClasses.add(new EncodedClass(name, encodedClass));
    }

    public ClassName getClassName() {
        return ClassName.bestGuess("dagger.TestTrigger");
    }

    public Iterator<EncodedClass> iterator() {
        return encodedClasses.iterator();
    }

    public byte[] decodeClass(String value) throws Base64DecodingException {
        return Base64.decode(value);
    }

    public static class EncodedClass {

        public EncodedClass(String qualifiedName, String encoded) {
            this.qualifiedName = qualifiedName;
            this.encoded = encoded;
        }

        public String qualifiedName;
        public String encoded;
    }
}
