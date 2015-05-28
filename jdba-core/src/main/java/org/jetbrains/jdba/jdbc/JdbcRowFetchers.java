package org.jetbrains.jdba.jdbc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jdba.exceptions.UnexpectedDBException;
import org.jetbrains.jdba.exceptions.UnexpectedReflectionException;
import org.jetbrains.jdba.util.NameAndClass;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;



/**
 * @author Leonid Bushuev from JetBrains
 */
public class JdbcRowFetchers {




  public static <V> OneValueFetcher<V> createOneValueFetcher(final int position,
                                                             final JdbcValueGetter<V> getter) {
    return new OneValueFetcher<V>(position,getter);
  }

  public static <V> ArrayFetcher<V> createArrayFetcher(final int position,
                                                       final Class<V> commonClass,
                                                       final JdbcValueGetter<? extends V>[] getters) {
    return new ArrayFetcher<V>(position, commonClass, getters);
  }

  public static <S> StructFetcher<S> createStructFetcher(final Class<S> structClass,
                                                         final NameAndClass[] components) {
    return new StructFetcher<S>(structClass, components);
  }


  //// FETCHERS \\\\

  public static final class OneValueFetcher<V> extends JdbcRowFetcher<V> {

    private final int position;
    private final JdbcValueGetter<V> getter;


    OneValueFetcher(final int position, final JdbcValueGetter<V> getter) {
      this.position = position;
      this.getter = getter;
    }

    @Override
    V fetchRow(@NotNull final ResultSet rset) throws SQLException {
      return getter.getValue(rset, position);
    }
  }


  public static final class ArrayFetcher<V> extends JdbcRowFetcher<V[]> {

    private final int position;
    private final Class<V> commonClass;
    private final JdbcValueGetter<? extends V>[] getters;


    private ArrayFetcher(final int position,
                         final Class<V> commonClass,
                         final JdbcValueGetter<? extends V>[] getters) {
      this.position = position;
      this.commonClass = commonClass;
      this.getters = getters;
    }


    @Override
    V[] fetchRow(@NotNull final ResultSet rset) throws SQLException {
      final int n = getters.length;
      //noinspection unchecked
      V[] array = (V[]) Array.newInstance(commonClass, n);
      for (int j = 0; j < n; j++) {
        array[j] = getters[j].getValue(rset, position + j);
      }
      return array;
    }
  }


  public static final class StructFetcher<S> extends JdbcRowFetcher<S> {

    private final Class<S> structClass;
    private final NameAndClass[] components;
    private final Constructor<S> structConstructor;
    private final int[] columnIndices;
    private final JdbcValueGetter<?>[] getters;
    private final Field[] fields;


    private boolean myRequiresInit = true;

    public StructFetcher(@NotNull final Class<S> structClass,
                         @NotNull final NameAndClass[] components) {
      this.structClass = structClass;
      this.components = components;

      int n = components.length;
      columnIndices = new int[n];
      getters = new JdbcValueGetter[n];
      fields = new Field[n];

      try {
        this.structConstructor = structClass.getDeclaredConstructor();
        this.structConstructor.setAccessible(true);

        for (int i = 0; i < n; i++) {
          String name = components[i].name;
          Field f = getClassField(structClass, name);
          if (f != null) {
            f.setAccessible(true);
            fields[i] = f;
          }
        }
      }
      catch (Exception e) {
        throw new UnexpectedReflectionException("Failed to analyze class " + structClass.getName(), e);
      }
    }



    @Override
    S fetchRow(@NotNull final ResultSet rset) throws SQLException {
      if (myRequiresInit) {
        initGetters(rset.getMetaData());
        myRequiresInit = true;
      }

      try {
        final S struct = structConstructor.newInstance();
        for (int i = 0, n = columnIndices.length; i < n; i++) {
          int columnIndex = columnIndices[i];
          Field f = fields[i];
          JdbcValueGetter<?> g = getters[i];
          if (columnIndex <= 0 || f == null || g == null) continue;
          Object value = g.getValue(rset, columnIndex);
          if (value != null) {
            f.set(struct, value);
          }
        }
        return struct;
      }
      catch (InstantiationException e) {
        throw new UnexpectedReflectionException("Failed to create/populate class " + structClass, e);
      }
      catch (IllegalAccessException e) {
        throw new UnexpectedReflectionException("Failed to create/populate class " + structClass, e);
      }
      catch (InvocationTargetException e) {
        throw new UnexpectedReflectionException("Failed to create/populate class " + structClass, e);
      }
    }

    private void initGetters(@NotNull final ResultSetMetaData md) {
      try {
        int n = components.length;
        Map<String, Integer> columnNames =
            new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        for (int j = 1, cn = md.getColumnCount(); j <= cn; j++) {
          String columnName = md.getColumnName(j);
          columnNames.put(columnName, j);
        }

        for (int i = 0; i < n; i++) {
          String name = components[i].name;
          Integer columnIndex = columnNames.get(name);
          if (columnIndex == null) continue;
          int jdbcType = md.getColumnType(columnIndex);
          JdbcValueGetter valueGetter = JdbcValueGetters.of(jdbcType, components[i].clazz);
          columnIndices[i] = columnIndex;
          getters[i] = valueGetter;
        }
      }
      catch (SQLException sqle) {
        throw new UnexpectedDBException("Analysing metadata of the query result", sqle, null);
      }
    }
  }

  protected static Field getClassField(final @NotNull Class<?> structClass, final String name)
      throws NoSuchFieldException
  {
    return structClass.getDeclaredField(name);
  }


}
