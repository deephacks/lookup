package deephacks.lookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public abstract class LookupProvider {
  protected final ConcurrentHashMap<Class<?>, Object> objectRegistry = new ConcurrentHashMap<>();

  /**
   * Look up an object matching a given interface.
   *
   * @param clazz The type of the object we want to lookupPrefered.
   * @return The object, if found, otherwise null.
   */
  public abstract <T> T lookup(Class<T> clazz);

  /**
   * Look up a list of objects that match a given interface.
   *
   * @param clazz The type of the object we want to lookupPrefered.
   * @return The object(s), if found, otherwise null.
   */
  public abstract <T> Collection<T> lookupAll(Class<T> clazz);

  /**
   * ServiceLoaderLookup is responsible for handling standard java service loader lookup.
   */
  static class ServiceLoaderLookup extends LookupProvider {

    public final <T> T lookup(Class<T> clazz) {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      for (T t : ServiceLoader.load(clazz, cl)) {
        try {
          // return first provider found.
          return t;
        } catch (ServiceConfigurationError e) {
          // treat lookup failures as if the implementation is unavailable
        }
      }
      return null;
    }

    @Override
    public <T> Collection<T> lookupAll(Class<T> clazz) {
      ArrayList<T> found = new ArrayList<>();
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      for (T t : ServiceLoader.load(clazz, cl)) {
        try {
          found.add(t);
        } catch (ServiceConfigurationError e) {
          // treat lookup failures as if the implementation is unavailable
        }
      }
      return found;
    }
  }
}