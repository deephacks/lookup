package deephacks.lookup.processor;

import deephacks.lookup.ServiceProvider;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;


/**
 * Processes {@link ServiceProvider} annotations and generates the service provider
 * configuration files described in {@link java.util.ServiceLoader}.
 */
public class ServiceProviderProcessor extends AbstractProcessor {

  private static final String SERVICES_PATH = "META-INF/services";

  /**
   * Maps service provider interfaces to concrete implementations
   */
  private Map<String, List<String>> providers = new HashMap<>();

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return new HashSet<>(Arrays.asList(ServiceProvider.class.getName()));
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      return processImpl(annotations, roundEnv);
    } catch (Exception e) {
      StringWriter writer = new StringWriter();
      e.printStackTrace(new PrintWriter(writer));
      fatalError(writer.toString());
      return true;
    }
  }

  private boolean processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      generateConfigFiles();
    } else {
      processAnnotations(roundEnv);
    }

    return true;
  }

  private void processAnnotations(RoundEnvironment roundEnv) {
    Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(ServiceProvider.class);

    for (Element e : elements) {
      TypeElement providerImplementer = (TypeElement) e;
      AnnotationMirror providerAnnotation = getAnnotationMirror(e, ServiceProvider.class);
      DeclaredType providerInterface = getProviderInterface(providerAnnotation);
      TypeElement providerType;
      if (providerInterface != null) {
        providerType = (TypeElement) providerInterface.asElement();
      } else {
        providerType = providerImplementer;
      }

      if (!checkImplementer(providerImplementer, providerType)) {
        String message = "ServiceProviders must implement their service provider interface. "
                + providerImplementer.getQualifiedName() + " does not implement "
                + providerType.getQualifiedName();
        error(message, e, providerAnnotation);
      }

      String providerName = getBinaryName(providerType);
      String implName = getBinaryName(providerImplementer);
      List<String> implementations = providers.get(providerName);
      if (implementations == null) {
        providers.put(providerName, implementations = new ArrayList<>());
      }
      implementations.add(implName);
    }
  }

  private void generateConfigFiles() {
    Filer filer = processingEnv.getFiler();

    for (String providerInterface : providers.keySet()) {
      String resourceFile = getPath(providerInterface);
      try {
        SortedSet<String> allServices = new TreeSet<>();
        try {
          FileObject existingFile = filer.getResource(StandardLocation.CLASS_OUTPUT, "", resourceFile);
          Set<String> oldServices = readServiceFile(existingFile.openInputStream());
          allServices.addAll(oldServices);
        } catch (IOException e) {
        }

        Set<String> newServices = new HashSet<String>(providers.get(providerInterface));
        if (allServices.containsAll(newServices)) {
          return;
        }

        allServices.addAll(newServices);
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourceFile);
        OutputStream out = fileObject.openOutputStream();
        writeServiceFile(allServices, out);
        out.close();
      } catch (IOException e) {
        fatalError("Unable to create " + resourceFile + ", " + e);
        return;
      }
    }
  }

  private boolean checkImplementer(TypeElement providerImplementer, TypeElement providerType) {
    Types types = processingEnv.getTypeUtils();
    return types.isSubtype(providerImplementer.asType(), providerType.asType());
  }

  /**
   * Returns org.deephacks.Inner$Outer instead of org.deephacks.Inner.Outer.
   */
  private String getBinaryName(TypeElement element) {
    return getBinaryNameImpl(element, element.getSimpleName().toString());
  }

  private String getBinaryNameImpl(TypeElement element, String className) {
    Element enclosingElement = element.getEnclosingElement();

    if (enclosingElement instanceof PackageElement) {
      PackageElement pkg = (PackageElement) enclosingElement;
      if (pkg.isUnnamed()) {
        return className;
      }
      return pkg.getQualifiedName() + "." + className;
    }

    TypeElement typeElement = (TypeElement) enclosingElement;
    return getBinaryNameImpl(typeElement, typeElement.getSimpleName() + "$" + className);
  }

  private DeclaredType getProviderInterface(AnnotationMirror providerAnnotation) {
    Map<? extends ExecutableElement, ? extends AnnotationValue> valueIndex = providerAnnotation.getElementValues();
    Iterator<? extends AnnotationValue> iterator = valueIndex.values().iterator();
    if (!iterator.hasNext()) {
      return null;
    }
    AnnotationValue value = iterator.next();
    return (DeclaredType) value.getValue();
  }

  private AnnotationMirror getAnnotationMirror(Element e, Class<? extends Annotation> klass) {
    List<? extends AnnotationMirror> annotationMirrors = e.getAnnotationMirrors();
    for (AnnotationMirror mirror : annotationMirrors) {
      DeclaredType type = mirror.getAnnotationType();
      TypeElement typeElement = (TypeElement) type.asElement();
      if (typeElement.getQualifiedName().contentEquals(klass.getName())) {
        return mirror;
      }
    }
    return null;
  }

  private void error(String msg, Element element, AnnotationMirror annotation) {
    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element, annotation);
  }

  private void fatalError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, "FATAL ERROR: " + msg);
  }


  private String getPath(String serviceName) {
    return SERVICES_PATH + "/" + serviceName;
  }

  private Set<String> readServiceFile(InputStream input) throws IOException {
    HashSet<String> serviceClasses = new HashSet<>();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
      String line;
      while ((line = r.readLine()) != null) {
        int commentStart = line.indexOf('#');
        if (commentStart >= 0) {
          line = line.substring(0, commentStart);
        }
        line = line.trim();
        if (!line.isEmpty()) {
          serviceClasses.add(line);
        }
      }
      return serviceClasses;
    }
  }

  private void writeServiceFile(Collection<String> services, OutputStream output) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, "UTF-8"))) {
      for (String service : services) {
        writer.write(service);
        writer.newLine();
      }
      writer.flush();
    }
  }
}