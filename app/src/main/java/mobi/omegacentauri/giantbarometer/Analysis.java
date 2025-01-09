package mobi.omegacentauri.giantbarometer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class Analysis {
    private static final double MIN_HALF_LAP_HEIGHT = 1.5;
    List<TimedDatum<Double>> rawData;
    List<TimedDatum<Double>> filteredData;
    static final double RISE_TOLERANCE = 0.35;
    static final double HALF_LAP_TOLERANCE = 0.3;
    int ascents;
    int descents;
    public Analysis(List<TimedDatum<Double>> _data) {
        rawData = _data;
    }

    public int countLaps() {
        List<TimedDatum<Double>> d = spacingFilter(rawData, 0.5);
        filteredData = medianFilter(d, 3.5);
        if (filteredData.size() < 3)
            return 0;
        double rise = getRise( 1, RISE_TOLERANCE);
        double fall = getRise(-1, RISE_TOLERANCE);
        ascents = 0;
        descents = 0;
        Log.v("GiantBarometer", "rise "+rise+" fall "+fall+" ascents "+ascents+" descents "+descents);
        if (rise < MIN_HALF_LAP_HEIGHT || fall < MIN_HALF_LAP_HEIGHT)
            return 0;
        double height = (rise+fall)/2.;
        countAscentsAndDescents(height, HALF_LAP_TOLERANCE);
        return Math.min(ascents,descents);
    }

    private void countAscentsAndDescents(double height, double halfLapTolerance) {
        ascents = 0;
        descents = 0;
        int direction = 0;
        double lastCriticalPoint = filteredData.get(0).value;
        int n = filteredData.size();
        for (int i = 1 ; i < n ; i++) {
            double y = filteredData.get(i).value;
            if (Math.abs(y-lastCriticalPoint) > (1.-halfLapTolerance)*height) {
                if (y > lastCriticalPoint) {
                    ascents++;
                    direction = 1;
                }
                else {
                    descents++;
                    direction = -1;
                }
                lastCriticalPoint = y;
            }
            else if (direction > 0 && y > lastCriticalPoint) {
                lastCriticalPoint = y;
            }
            else if (direction < 0 && y < lastCriticalPoint) {
                lastCriticalPoint = y;
            }
        }
    }

    double getRise(double scale, double fallTolerance) {
        // calculate rise from absolute minimum, allowing for some falls
        int n = filteredData.size();
        int minPos = -1;
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 0 ; i < n; i++) {
            double y = filteredData.get(i).value * scale;
            if (y < minimum) {
                minPos = i;
                minimum = y;
            }
        }
        Log.v("GiantBarometer", "minPos "+minPos+" minimum "+minimum);
        int direction = minPos <= n/2 ? 1 : -1;
        int end = direction == -1 ? 0 : n - 1;
        int pos = minPos;
//        int peakCandidatePos = pos;
        double peakCandidateHeight = minimum;
        double biggestSoFar = minimum;
        double biggestFall = 0;
        while (pos != end) {
            double y = filteredData.get(pos).value * scale;
            if (y > biggestSoFar) {
                biggestSoFar = y;
                if ((y-minimum) > biggestFall * fallTolerance) {
                    peakCandidateHeight = y;
//                    peakCandidatePos = pos;
                }
            }
            else if (biggestSoFar-y > biggestFall)
                biggestFall = biggestSoFar-y;

            pos += direction;
        }
        return peakCandidateHeight - minimum;
    }

    private List<TimedDatum<Double>> medianFilter(List<TimedDatum<Double>> data, double windowSize) {
        LinkedList<TimedDatum<Double>> window = new LinkedList<>();
        ArrayList<TimedDatum<Double>> out = new ArrayList<>();
        for (TimedDatum<Double> datum : data) {
            double cutoff = datum.time - windowSize;
            ListIterator<TimedDatum<Double>> iterator = window.listIterator();
            boolean added = false;
            while (iterator.hasNext()) {
                TimedDatum<Double> node = iterator.next();
                if (node.time < cutoff)
                    iterator.remove();
                else if (!added && datum.value <= node.value) {
                    iterator.previous();
                    iterator.add(datum);
                    added = true;
                    break;
                }
            }
            if (!added)
                iterator.add(datum);
            int n = window.size();
            if (n % 2 != 0) {
                out.add(new TimedDatum<Double>(datum.time, window.get(n/2).value));
            }
            else {
                out.add(new TimedDatum<Double>(datum.time, (window.get(n/2-1).value+window.get(n/2).value)/2));
            }
        }
        return out;
    }

    private List<TimedDatum<Double>> spacingFilter(List<TimedDatum<Double>> data, double spacing) {
        ArrayList<TimedDatum<Double>> out = new ArrayList<>();
        double prevTime = Double.NEGATIVE_INFINITY;
        int count = data.size();
        for (TimedDatum<Double> d : data) {
            if (d.time - spacing >= prevTime || count == 1)
                out.add(d);
            count--;
        }
        return out;
    }

    static final class TimedDatum<T> {
        long time;

        T value;

        public TimedDatum(long t, T y) {
            time = t;
            value = y;
        }

    }

    static final class RecentData {
        LinkedList<TimedDatum<Double>> recent = new LinkedList<>(); // kept sorted by value for medians
        long timeToKeep = 100*1000;

        RecentData(long keep) {
            timeToKeep = keep;
        }

        void clear() {
            recent.clear();
        }

        TimedDatum latest() {
            if (recent.isEmpty())
                return null;

            long latest = Long.MIN_VALUE;
            TimedDatum<Double> best = null;
            ListIterator<TimedDatum<Double>> iterator = recent.listIterator();
            while (iterator.hasNext()) {
                TimedDatum<Double> node = iterator.next();
                if (node.time > latest) {
                    best = node;
                    latest = node.time;
                }
            }
            return best;
        }

        void add(TimedDatum<Double> datum) {
            long now = datum.time;
            ListIterator<TimedDatum<Double>> iterator = recent.listIterator();
            long cutoff = now - timeToKeep;
            boolean added = false;
            while (iterator.hasNext()) {
                TimedDatum<Double> node = iterator.next();
                if (node.time < cutoff)
                    iterator.remove();
                else if (!added && datum.value <= node.value) {
                    iterator.previous();
                    iterator.add(datum);
                    added = true;
                    break;
                }
            }
            if (!added)
                iterator.add(datum);
        }

        public double smooth(String smoothing) {
            TimedDatum<Double> latest = latest();
            if (latest == null)
                return Double.NaN;
            if (smoothing.startsWith("med")) {
                long cutoff = latest.time - Long.parseLong(smoothing.substring(3));
                ArrayList<Double> selected = new ArrayList<>();
                for (TimedDatum<Double> datum : recent) {
                    if (datum.time >= cutoff)
                        selected.add(datum.value);
                }
                int n = selected.size();
                if (n % 2 != 0) {
                    return selected.get(n/2);
                }
                else {
                    return (selected.get(n/2-1) + selected.get(n/2)) / 2.;
                }
            }
            else if (smoothing.startsWith("avg")) {
                long cutoff = latest.time - Long.parseLong(smoothing.substring(3));
                double sum = 0;
                long count = 0;
                ListIterator<TimedDatum<Double>> iterator = recent.listIterator();
                while (iterator.hasNext()) {
                    TimedDatum<Double> node = iterator.next();
                    if (node.time >= cutoff) {
                        sum += node.value;
                        count ++;
                    }
                }
                return sum/count;
            }
            else {
                return latest.value;
            }
        }
    }
}
