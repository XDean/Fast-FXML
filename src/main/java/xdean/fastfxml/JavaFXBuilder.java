package xdean.fastfxml;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.javafx.fxml.BeanAdapter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.util.Builder;
import sun.reflect.misc.MethodUtil;

/**
 * JavaFX builder.
 */
@SuppressWarnings({ "restriction", "unchecked" })
final class JavaFXBuilder {
  private static final Object[] NO_ARGS = {};
  private static final Class<?>[] NO_SIG = {};

  private final Class<?> builderClass;
  private final Method createMethod;
  private final Method buildMethod;
  private final Map<String, Method> methods = new HashMap<String, Method>();
  private final Map<String, Method> getters = new HashMap<String, Method>();
  private final Map<String, Method> setters = new HashMap<String, Method>();

  final class ObjectBuilder extends AbstractMap<String, Object> implements Builder<Object> {
    private final Map<String, Object> containers = new HashMap<String, Object>();
    private Object builder = null;
    private Map<Object, Object> properties;

    private ObjectBuilder() {
      try {
        builder = MethodUtil.invoke(createMethod, null, NO_ARGS);
      } catch (Exception e) {
        // TODO
        throw new RuntimeException("Creation of the builder " + builderClass.getName() + " failed.", e);
      }
    }

    @Override
    public Object build() {
      for (Iterator<Entry<String, Object>> iter = containers.entrySet().iterator(); iter.hasNext();) {
        Entry<String, Object> entry = iter.next();

        put(entry.getKey(), entry.getValue());
      }

      Object res;
      try {
        res = MethodUtil.invoke(buildMethod, builder, NO_ARGS);
        // TODO:
        // temporary special case for Node properties until
        // platform builders are fixed
        if (properties != null && res instanceof Node) {
          ((Map<Object, Object>) ((Node) res).getProperties()).putAll(properties);
        }
      } catch (InvocationTargetException exception) {
        throw new RuntimeException(exception);
      } catch (IllegalAccessException exception) {
        throw new RuntimeException(exception);
      } finally {
        builder = null;
      }

      return res;
    }

    @Override
    public int size() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(Object key) {
      return (getTemporaryContainer(key.toString()) != null);
    }

    @Override
    public boolean containsValue(Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object get(Object key) {
      return getTemporaryContainer(key.toString());
    }

    @Override
    public Object put(String key, Object value) {
      // TODO:
      // temporary hack: builders don't have a method for properties...
      if (Node.class.isAssignableFrom(getTargetClass()) && "properties".equals(key)) {
        properties = (Map<Object, Object>) value;
        return null;
      }
      try {
        Method m = methods.get(key);
        if (m == null) {
          m = findMethod(key);
          methods.put(key, m);
        }
        try {
          final Class<?> type = m.getParameterTypes()[0];

          // If the type is an Array, and our value is a list,
          // we simply convert the list into an array. Otherwise,
          // we treat the value as a string and split it into a
          // list using the array component delimiter.
          if (type.isArray()) {
            final List<?> list;
            if (value instanceof List) {
              list = (List<?>) value;
            } else {
              list = Arrays.asList(value.toString().split(FXMLLoader.ARRAY_COMPONENT_DELIMITER));
            }

            final Class<?> componentType = type.getComponentType();
            Object array = Array.newInstance(componentType, list.size());
            for (int i = 0; i < list.size(); i++) {
              Array.set(array, i, BeanAdapter.coerce(list.get(i), componentType));
            }
            value = array;
          }

          MethodUtil.invoke(m, builder, new Object[] { BeanAdapter.coerce(value, type) });
        } catch (Exception e) {
          Logger.getLogger(JavaFXBuilder.class.getName()).log(Level.WARNING,
              "Method " + m.getName() + " failed", e);
        }
        // TODO Is it OK to return null here?
        return null;
      } catch (Exception e) {
        // TODO Should be reported
        Logger.getLogger(JavaFXBuilder.class.getName()).log(Level.WARNING,
            "Failed to set " + getTargetClass() + "." + key + " using " + builderClass, e);
        return null;
      }
    }

    // Should do this in BeanAdapter?
    // This is used to support read-only collection property.
    // This method must return a Collection of the appropriate type
    // if 1. the property is read-only, and 2. the property is a collection.
    // It must return null otherwise.
    Object getReadOnlyProperty(String propName) {
      if (setters.get(propName) != null) {
        return null;
      }
      Method getter = getters.get(propName);
      if (getter == null) {
        Method setter = null;
        Class<?> target = getTargetClass();
        String suffix = Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        try {
          getter = MethodUtil.getMethod(target, "get" + suffix, NO_SIG);
          setter = MethodUtil.getMethod(target, "set" + suffix, new Class[] { getter.getReturnType() });
        } catch (Exception x) {
        }
        if (getter != null) {
          getters.put(propName, getter);
          setters.put(propName, setter);
        }
        if (setter != null) {
          return null;
        }
      }

      Class<?> type;
      if (getter == null) {
        // if we have found no getter it might be a constructor property
        // try to get the type from the builder method.
        final Method m = findMethod(propName);
        if (m == null) {
          return null;
        }
        type = m.getParameterTypes()[0];
        if (type.isArray()) {
          type = List.class;
        }
      } else {
        type = getter.getReturnType();
      }

      if (ObservableMap.class.isAssignableFrom(type)) {
        return FXCollections.observableMap(new HashMap<Object, Object>());
      } else if (Map.class.isAssignableFrom(type)) {
        return new HashMap<Object, Object>();
      } else if (ObservableList.class.isAssignableFrom(type)) {
        return FXCollections.observableArrayList();
      } else if (List.class.isAssignableFrom(type)) {
        return new ArrayList<Object>();
      } else if (Set.class.isAssignableFrom(type)) {
        return new HashSet<Object>();
      }
      return null;
    }

    /**
     * This is used to support read-only collection property. This method must return a Collection
     * of the appropriate type if 1. the property is read-only, and 2. the property is a collection.
     * It must return null otherwise.
     **/
    public Object getTemporaryContainer(String propName) {
      Object o = containers.get(propName);
      if (o == null) {
        o = getReadOnlyProperty(propName);
        if (o != null) {
          containers.put(propName, o);
        }
      }

      return o;
    }

    @Override
    public Object remove(Object key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> keySet() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Object> values() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
      throw new UnsupportedOperationException();
    }
  }

  JavaFXBuilder() {
    builderClass = null;
    createMethod = null;
    buildMethod = null;
  }

  JavaFXBuilder(Class<?> builderClass) throws NoSuchMethodException, InstantiationException, IllegalAccessException {
    this.builderClass = builderClass;
    createMethod = MethodUtil.getMethod(builderClass, "create", NO_SIG);
    buildMethod = MethodUtil.getMethod(builderClass, "build", NO_SIG);
    assert Modifier.isStatic(createMethod.getModifiers());
    assert !Modifier.isStatic(buildMethod.getModifiers());
  }

  Builder<Object> createBuilder() {
    return new ObjectBuilder();
  }

  private Method findMethod(String name) {
    if (name.length() > 1
        && Character.isUpperCase(name.charAt(1))) {
      name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    for (Method m : MethodUtil.getMethods(builderClass)) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    throw new IllegalArgumentException("Method " + name + " could not be found at class " + builderClass.getName());
  }

  /**
   * The type constructed by this builder.
   *
   * @return The type constructed by this builder.
   */
  public Class<?> getTargetClass() {
    return buildMethod.getReturnType();
  }
}