import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Q2 {

    /*
     * Build a trie (prefix tree) in O(m) average time and space, where m is the total number of segments in all routes;
     * then search (depth-first, implying static-first) via recursive backtracking in O(n) average time,
     * where n in the number of segments in a path.
     *
     * Time complexity for building the trie can increase to O(m^2) in the worst case due to hash collisions
     * between segments of paths in different routes, e.g. if path segments /a and /b compute to the same hash.
     *
     * Although backtracking can result in exponential lookup time in the worst case, if the amount of backtracking is
     * limited it may still be a sensible trade-off. Alternatively, if backtracking is excessive or it is critical to
     * maintain O(n) worst case lookup time, the trie can be converted to a deterministic finite automaton at the cost
     * of exponential space.
     */
    static class TrieNode {
        // fields:
        String endpoint = null;
        final Map<String, TrieNode> children = new HashMap<>();

        // constants:
        static final String NOT_FOUND = "404";
        static final String PATH_SEPARATOR = "/";
        static final String WILDCARD = "X";

        // methods:
        /**
         * Parse a route into ({@code this}) trie
         * @param route a {@code Route} (path and endpoint) to parse into the trie
         */
        void addRoute(Route route) {  // non-recursively, for expedience
            TrieNode node = this;
            String[] pathSegments = route.path.split(PATH_SEPARATOR, -1);  // tokenize path, keep empty segments
            for (String pathSegment: pathSegments) {
                node = node.addSegment(pathSegment);  // insert each segment into the trie and advance current node
            }
            assert node.endpoint == null;  // To make sure config doesn't redefine endpoints, compile with -ea
            node.endpoint = route.endpoint;  // register an endpoint at the appropriate (current) node in the trie
        }

        private TrieNode addSegment(String pathSegment) {
            return children.computeIfAbsent(pathSegment, p -> new TrieNode());
        }

        /**
         * Look up a path in ({@code this}) trie
         * @param path to look up in the trie
         * @return the endpoint for the given path or "404" if none found
         */
        String findPath(String path) {
            List<String> pathSegments = Arrays.asList(path.split(PATH_SEPARATOR, -1));
            final String ep = findSegmentsRecursively(pathSegments);
            return ep != null ? ep : NOT_FOUND;
        }

        private String findSegmentsRecursively(List<String> pathSegments) {
            if (pathSegments.isEmpty()) { return this.endpoint; }  // base case

            // a segment can: (1) match a child, or (2) match an X if available, or (3) not match anything:

            return Stream.of(pathSegments.get(0), WILDCARD)  // try (1) *then* backtrack to (2), in that order
                    .map(children::get)  // look it up in children
                    .filter(Objects::nonNull)  // if a match is found, i.e. the segment or wildcard is allowed...
                    .map(next -> next.findSegmentsRecursively(pathSegments.subList(1, pathSegments.size())))  // recurse
                    .filter(Objects::nonNull)  // if obtained a non-null endpoint...
                    .findFirst()  // ...return the first one found...
                    .orElse(null);  // ...otherwise return null. Nothing to it! :)
        }
    }

    private static List<String> routeAll(List<Route> routes, List<String> paths) {
        if (paths == null || paths.isEmpty()) { return paths; }
        if (routes == null) { return null; }

        TrieNode trie = new TrieNode();  // good thing we only need to do it once (per given configuration of routes)
        routes.forEach(trie::addRoute);  // parse all the routes into the trie

        // call findPath on every path in {@code paths}, return corresponding endpoints (or "404"s) as a list:
        return paths.stream().map(trie::findPath).collect(Collectors.toList());
    }

    /**
     *      Hey! You probably won't need to edit anything below here.
     */

    static class Route {
        String path;
        String endpoint;
        public Route(String path, String endpoint) {
            this.path = path;
            this.endpoint = endpoint;
        }
    }

    private static List<Route> getRoutes(InputStream is) throws IOException {
        List<Route> routes = new ArrayList<Route>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null && line.length() != 0) {
            String[] tokenizedLine = line.split(" ");
            routes.add(new Q2.Route(tokenizedLine[0], tokenizedLine[1]));
        }
        return routes;
    }

    private static List<String> getPaths(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        List<String> paths = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null && line.length() != 0) {
            paths.add(line);
        }
        return paths;
    }

    public static void main(String... args) throws IOException {
        List<Route> routes = Q2.getRoutes(new FileInputStream(args[0]));
        List<String> paths = Q2.getPaths(System.in);

        for(String endpoint : Q2.routeAll(routes, paths)) {
            System.out.println(endpoint);
        }
    }
}
