package de.qaware.qav.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Pattern matching for fully qualified class names.
 * <p>
 * This is based on the Ant path matcher, see {@link AntPathMatcher}.
 *
 * @author QAware GmbH
 */
@Slf4j
public class QavNameMatcher {

    private final AntPathMatcher antPathMatcher;

    /**
     * Constructor. Use the default <tt>pathSeparator</tt> with "."
     */
    public QavNameMatcher() {
        this(".");
    }

    /**
     * Constructor.
     *
     * @param pathSeparator the path separator
     */
    public QavNameMatcher(String pathSeparator) {
        this.antPathMatcher = new AntPathMatcher(pathSeparator);
    }

    /**
     * returns <tt>true</tt> if the name matches the pattern, and false if it does not.
     * <p>
     * The pattern matching is Ant-style, with one difference: a ".*" at the end will be handled as ".**".
     *
     * @param pattern the pattern, Ant-style
     * @param name    the name to check
     * @return <tt>true</tt> if it matches, <tt>false</tt> if it does not.
     */
    public boolean matches(String pattern, String name) {
        checkNotNull(pattern, "pattern");
        checkNotNull(name, "name");

        return antPathMatcher.match(pattern, name);
    }
}
