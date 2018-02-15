package de.qaware.qav.util;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.tools.ant.DirectoryScanner;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helper to deal with file names.
 *
 * @author QAware GmbH
 */
public final class FileNameUtil {

    /**
     * util class, no instances.
     */
    private FileNameUtil() {
    }

    /**
     * Finds all files identified by the given ant-style filter and returns them in a list, sorted according to the
     * absolute paths.
     *
     * @param parameters directory to start.
     * @return List. May be empty, but not null.
     */
    public static List<File> identifyFiles(Map parameters) {
        String baseDir = (String) parameters.get("baseDir");
        if (Strings.isNullOrEmpty(baseDir) || !new File(baseDir).exists()) {
            return new ArrayList<>();
        }

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(baseDir);
        setIncludesExcludes(parameters.get("includes"), scanner::setIncludes);
        setIncludesExcludes(parameters.get("excludes"), scanner::setExcludes);
        scanner.setCaseSensitive(false);
        scanner.scan();

        String[] files = scanner.getIncludedFiles();

        List<File> result = new ArrayList<>();
        for (String filename : files) {
            result.add(new File(baseDir, filename));
        }

        result.sort(Comparator.comparing(File::getAbsolutePath));

        return result;
    }

    /**
     * calls the given setter - but only if the input is not null.
     *
     * @param values the values, to be read and put into a String array
     * @param setter the setter to call if there are values to set
     */
    private static void setIncludesExcludes(Object values, Consumer<String[]> setter) {
        List<String> list = getAsList(values);
        if (list != null && !list.isEmpty()) {
            setter.accept(list.toArray(new String[]{}));
        }
    }

    /**
     * returns the given object as List of Strings:
     * <ul>
     * <li>if it's null: returns empty list</li>
     * <li>if it's a String: returns a list with one element</li>
     * <li>if it's a List: returns that list</li>
     * </ul>
     *
     * @param values the values
     * @return the list
     */
    /*package*/ static List<String> getAsList(Object values) {
        if (values == null) {
            return new ArrayList<>();
        }

        if (values instanceof String) {
            return Lists.newArrayList((String) values);
        } else if (values instanceof List) {
            return (List<String>) values;
        } else {
            throw new IllegalArgumentException("includes or excludes must be a String or a List<String> but is a " + values.getClass());
        }
    }

    /**
     * checks if the given file name is included in the given includes/excludes map.
     * <p>
     * The map has one entry for "includes", and one for "excludes". Both entries are optional.
     * This method does not check for a baseDir.
     *
     * @param name       the name to check
     * @param parameters the map with "incluces" and "excludes" patterns, in Ant-Style
     * @return true if included, false if not.
     */
    public static boolean isIncluded(String name, Map parameters) {
        checkNotNull(name, "name must be given.");

        if (parameters == null) {
            return true;
        }

        List<String> includes = getAsList(parameters.get("includes"));
        List<String> excludes = getAsList(parameters.get("excludes"));

        return matches(name, includes, true) && !matches(name, excludes, false);
    }

    /**
     * checks if the given name is matches by any of the given patterns. If the patterns are empty or null, the
     * defaultValue is returned.
     *
     * @param name         the name
     * @param patterns     the list of patterns
     * @param defaultValue the defaultValue
     * @return true if it matches at least one pattern, false if not
     */
    public static boolean matches(String name, List<String> patterns, boolean defaultValue) {
        if (patterns == null || patterns.isEmpty()) {
            return defaultValue;
        }

        return patterns.stream()
                .anyMatch(pattern -> matches(name, pattern));
    }

    /**
     * checks if the given name matches the given pattern. The pattern is in Ant-style.
     *
     * @param name    the name
     * @param pattern the pattern, in Ant-style
     * @return true if it matches, false if not
     */
    public static boolean matches(String name, String pattern) {
        return new AntPathMatcher().match(pattern, name);
    }

    /**
     * get the parent path of the given file.
     *
     * @param fileName file name
     * @return parent path of the given file
     */
    public static String getParentPath(String fileName) {
        if (fileName.startsWith(FileSystemUtil.CLASSPATH_PREFIX)) {
            int lastIndex = fileName.lastIndexOf('/');
            return fileName.substring(0, lastIndex + 1);
        } else {
            File file = new File(fileName);
            try {
                return new File(file.getCanonicalPath()).getParent() + File.separator;
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * Returns the canonical path, i.e. cleared and normalized.
     *
     * @param path the path
     * @return the canonical path
     * @throws IllegalArgumentException if {@link File#getCanonicalPath()} throws an {@link IOException}.
     */
    public static String getCanonicalPath(String path) {
        if (path.startsWith(FileSystemUtil.CLASSPATH_PREFIX)) {
            return path;
        }
        File file = new File(path);
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Error accessing file '" + file.getAbsolutePath() + "': ", e);
        }
    }

}
