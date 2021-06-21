import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Function;

public class Invoke {
    static File path = new File("nballerina.jar");
    private static final PrintStream console = System.out;
    public static void main(String[] args){
        try {
            ClassLoader prcl = ClassLoader.getSystemClassLoader();

            //URLClassLoader urlCL = new URLClassLoader(new URL[]{path.toURI().toURL()});
            //Class<?> nStrand = urlCL.loadClass("io.ballerina.runtime.internal.scheduling.Strand");
            URLClassLoader cl = new URLClassLoader(new URL[]{path.toURI().toURL()});
            Class<?> strandClass = cl.loadClass("io.ballerina.runtime.internal.scheduling.Strand");
            Class<?> schedulerClass = cl.loadClass("io.ballerina.runtime.internal.scheduling.Scheduler");
            Class<?> cbClass = cl.loadClass("io.ballerina.runtime.api.async.Callback");
            Class<?> smdClass = cl.loadClass("io.ballerina.runtime.api.async.StrandMetadata");
            Class<?> typeClass = cl.loadClass("io.ballerina.runtime.api.types.Type");
            Class<?> predeftypeClass = cl.loadClass("io.ballerina.runtime.api.PredefinedTypes");
            Class<?> mainClass = cl.loadClass("$_init");

            Object value = predeftypeClass.getDeclaredField("TYPE_ANY");

            Class<?> c = cl.loadClass("nballerina");

            Method m = c.getMethod("compile", strandClass);
            console.println("method found " + m.getName());

            Method startMethod = schedulerClass.getDeclaredMethod("start");
            Method mainMethod = mainClass.getMethod("$moduleInit", strandClass);

            Function<Object[], Object> funcInit = createFunc(mainMethod);

            Function<Object[], Object> funcCompile = createFunc(m);

            Object scheduler = schedulerClass.getConstructor(Boolean.TYPE)
                    .newInstance(false);
            Method scheduleMethod = schedulerClass.getMethod("schedule", Object[].class,
                    Function.class, strandClass, cbClass, Map.class, typeClass, String.class, smdClass);
            scheduleMethod.invoke(schedulerClass.cast(scheduler), new Object[1],
                    funcInit, null, null, new HashMap<>(), null, null, null);
            startMethod.invoke(schedulerClass.cast(scheduler));
            scheduleMethod.invoke(schedulerClass.cast(scheduler), new Object[1],
                    funcCompile, null, null, new HashMap<>(), null, null, null);
            startMethod.invoke(schedulerClass.cast(scheduler));


        } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException| IllegalAccessException e) {
            console.println(e);
        } catch (InvocationTargetException e) {
            console.println(e.getCause());
        } catch (InstantiationException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        console.println("nbal active");
    }

    private static Function<Object[], Object> createFunc(Method m) {
        return objects -> {
            try {
                console.println("invoking function");
                return m.invoke(null, objects[0]);
            } catch (InvocationTargetException e) {
                Throwable targetException = e.getTargetException();
                console.println("internal error1: " + targetException);
                console.println("internal error2: " + Arrays.toString(targetException.getStackTrace()));
                console.println("internal error3: " + e);
                if (targetException instanceof RuntimeException) {
                    throw (RuntimeException) targetException;
                } else {
                    throw new RuntimeException(targetException);
                }
            } catch (IllegalAccessException e) {
                try {
                    throw new Exception("Method has private access", e);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
            return null;
        };
    }

}
class ChildFirstClassLoader extends URLClassLoader {
    private ClassLoader system;

    public ChildFirstClassLoader(URL[] classpath, ClassLoader parent) {
        super(classpath, parent);
        system = getSystemClassLoader();
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        Class<?> c = findLoadedClass(name);
        //Class<?> c = null;
        if (c == null) {
            try {
                // checking local
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                try {
                    c = super.loadClass(name, resolve);
                } catch (ClassNotFoundException e2) {
                    if (system != null) {
                        c = system.loadClass(name);
                    }
                }
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url == null) {
            url = super.getResource(name);
        }
        if (url == null && system != null) {
            url = system.getResource(name);
        }
        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> systemUrls = null;
        if (system != null) {
            systemUrls = system.getResources(name);
        }
        Enumeration<URL> localUrls = findResources(name);
        Enumeration<URL> parentUrls = null;
        if (getParent() != null) {
            parentUrls = getParent().getResources(name);
        }
        final List<URL> urls = new ArrayList<URL>();
        if (localUrls != null) {
            while (localUrls.hasMoreElements()) {
                URL local = localUrls.nextElement();
                urls.add(local);
            }
        }
        if (systemUrls != null) {
            while (systemUrls.hasMoreElements()) {
                urls.add(systemUrls.nextElement());
            }
        }
        if (parentUrls != null) {
            while (parentUrls.hasMoreElements()) {
                urls.add(parentUrls.nextElement());
            }
        }
        return new Enumeration<URL>() {
            Iterator<URL> iter = urls.iterator();

            public boolean hasMoreElements() {
                return iter.hasNext();
            }

            public URL nextElement() {
                return iter.next();
            }
        };
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
        }
        return null;
    }
}
