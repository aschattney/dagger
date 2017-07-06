package dagger.android.testcase;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.inject.Qualifier;

public class Apply {

    public static void decorationsOf(final Object obj, Object app) {
        final Field[] declaredFields = obj.getClass().getDeclaredFields();
        for (final Field declaredField : declaredFields) {
            if (declaredField.isAnnotationPresent(Replace.class) || hasMockAnnotation(declaredField)) {
                declaredField.setAccessible(true);
                final Annotation[] annotations = declaredField.getAnnotations();
                String component = "";
                for (Annotation a : annotations) {
                    if (a.annotationType().getSimpleName().startsWith("In")) {
                        component = a.annotationType().getSimpleName().substring(2);
                        break;
                    }
                }
                if (component.equals("")) {
                    continue;
                }
                String name = String.valueOf(component.charAt(0)).toLowerCase() +
                        component.substring(1);
                final String field = name + "DecoratorImpl";
                final Field decoratorImplField = getDeclaredField(app, field);
                if (decoratorImplField != null) {
                    decoratorImplField.setAccessible(true);
                    final Class<?> declaredFieldClass = declaredField.getType();
                    if (declaredFieldClass != null) {
                        String methodName = getWithMethodName(declaredField);
                        final Class<?> clazz = classForName(getDelegateType(declaredField));
                        final Class<?> decoratorImplClass = decoratorImplField.getType();
                        final Method declaredMethod = getDeclaredMethod(decoratorImplClass, methodName, clazz);
                        if (declaredMethod != null) {
                            final ClassLoader cl = obj.getClass().getClassLoader();
                            final Object o = Proxy.newProxyInstance(cl, new Class<?>[]{clazz}, new InvocationHandler() {
                                @Override
                                public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                                    if (method.getName().equals("get")) {
                                        return declaredField.get(obj);
                                    }else {
                                        return method.invoke(o, objects);
                                    }
                                }
                            });
                            try
                            {
                                invoke(decoratorImplField.get(app), declaredMethod, o);
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean hasMockAnnotation(Field declaredField) {
        final Annotation[] annotations = declaredField.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getName().equals("org.mockito.Mock")) {
                return true;
            }
        }
        return false;
    }

    private static String getDelegateType(Field declaredField) {
        final StringBuilder sb = new StringBuilder("delegates.");
        final Annotation qualifier = findQualifier(declaredField);
        if (qualifier != null) {
            try
            {
                final Method[] methods = qualifier.annotationType().getDeclaredMethods();
                if (methods.length > 0 && methods[0].getName().equals("value")) {
                    String identifier = String.valueOf(methods[0].invoke(qualifier));
                    identifier = String.valueOf(identifier.charAt(0)).toUpperCase() +  identifier.substring(1);
                    sb.append(qualifier.annotationType().getSimpleName());
                    sb.append(identifier);
                }
            } catch (IllegalAccessException e)
            {
                e.printStackTrace();
            } catch (InvocationTargetException e)
            {
                e.printStackTrace();
            }
        }else {
            sb.append(declaredField.getType().getSimpleName()).toString();
        }
        return sb.append("Delegate").toString();
    }

    private static String  getWithMethodName(Field declaredField) {
        final StringBuilder sb = new StringBuilder("with");
        final Annotation qualifier = findQualifier(declaredField);
        if (qualifier != null) {
            try
            {
                final Method[] methods = qualifier.annotationType().getDeclaredMethods();
                if (methods.length > 0 && methods[0].getName().equals("value")) {
                    String identifier = String.valueOf(methods[0].invoke(qualifier));
                    identifier = String.valueOf(identifier.charAt(0)).toUpperCase() +  identifier.substring(1);
                    sb.append(qualifier.annotationType().getSimpleName());
                    sb.append(identifier);
                }
            } catch (IllegalAccessException e)
            {
                e.printStackTrace();
            } catch (InvocationTargetException e)
            {
                e.printStackTrace();
            }
        }else {
            sb.append(declaredField.getType().getSimpleName());
        }
        return sb.toString();
    }

    private static Annotation findQualifier(Field declaredField) {
        final Annotation[] declaredAnnotations = declaredField.getDeclaredAnnotations();
        for (Annotation declaredAnnotation : declaredAnnotations)
        {
            for (Annotation annotation : declaredAnnotation.annotationType()
                                                           .getDeclaredAnnotations()) {
                if (annotation.annotationType().equals(Qualifier.class)) {
                    return declaredAnnotation;
                }
            }
        }
        return null;
    }

    private static Object invoke(Object instance, Method declaredMethod, Object o) {
        try
        {
            return declaredMethod.invoke(instance, o);
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        } catch (InvocationTargetException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static Method getDeclaredMethod(Class<?> aClass, String methodName, Class<?>... params) {

        try
        {
            return aClass.getDeclaredMethod(methodName, params);
        } catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static Class<?> classForName(String name){
        try
        {
            return Class.forName(name);
        } catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static Field getDeclaredField(Object app, String field) {
        try
        {
            return app.getClass().getDeclaredField(field);
        } catch (NoSuchFieldException e)
        {
            e.printStackTrace();
        }
        return null;
    }
}
