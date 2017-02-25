import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Q1 {
    static class Interval {
        int start;
        int end;
        public Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /* Simple implementation in O(n*log(n)) time using sort.
     * TODO: Other interesting possibilities to consider:
     * 1) Streaming interface that accepts intervals one by one, inserts them into a sorted set in O(log(n)) each
     *    and can present current gaps at any time in O(m) (where m is the size of output) or O(1);
     *    or
     * 2) A potential improvement on (1) that uses the fact that intervals arrive from real-time monitoring system
     *    in sorted order (by start or by end of the interval) to reduce ingestion time to O(1) per interval
     *    for an O(n) overall solution.
     */
    private static List<Interval> uncoveredIntervals(List<Interval> intervals) {

        if (intervals == null || intervals.isEmpty()) { return intervals; }  // TODO: more validation if needed

        intervals.sort(Comparator.comparingInt(interval -> interval.start));  // sort intervals by start ascending

        List<Interval> uncovered = new ArrayList<>();
        int coveredTill = intervals.get(0).end;  // covered up to here so far

        for (Interval interval : intervals
                .subList(1, intervals.size())) {  // or could just say intervals for readability

            if (interval.start > coveredTill) {  // found a gap -- file it:
                uncovered.add(new Interval(coveredTill, interval.start));
            }
            coveredTill = Math.max(coveredTill, interval.end);  // adjust coveredTill, maybe
        }
        return uncovered;
    }

    /*
     *  Hey! You probably don't need to edit anything below here
     */

    private static List<Q1.Interval> readIntervals(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        List<Q1.Interval> intervals = new ArrayList<Q1.Interval>();
        String line;
        while ((line = reader.readLine()) != null && line.length() != 0) {
            intervals.add(toInterval(line));
        }
        return intervals;
    }

    private static Q1.Interval toInterval(String line) {
        final String[] tokenizedInterval = line.split(" ");

        return new Interval(Integer.valueOf(tokenizedInterval[0]),
                            Integer.valueOf(tokenizedInterval[1]));
    }

    public static void main(String... args) throws IOException {
        List<Q1.Interval> intervals = Q1.readIntervals(System.in);
        List<Q1.Interval> uncovered = Q1.uncoveredIntervals(intervals);
        for (Interval i : uncovered) {
            System.out.println(i.start + " " + i.end);
        }
    }
}
