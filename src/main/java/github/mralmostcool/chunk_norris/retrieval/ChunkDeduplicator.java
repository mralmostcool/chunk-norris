package github.mralmostcool.chunk_norris.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ChunkDeduplicator {

    public static final double DEFAULT_OVERLAP_THRESHOLD = 0.70;

    private final double defaultOverlapThreshold;

    public ChunkDeduplicator() {
        this(DEFAULT_OVERLAP_THRESHOLD);
    }

    public ChunkDeduplicator(double defaultOverlapThreshold) {
        this.defaultOverlapThreshold = defaultOverlapThreshold;
    }

    public List<RetrievedChunk> deduplicate(List<RetrievedChunk> chunks) {
        return deduplicate(chunks, this.defaultOverlapThreshold);
    }

    public List<RetrievedChunk> deduplicate(List<RetrievedChunk> chunks, double overlapThreshold) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        if (chunks.size() == 1) {
            return new ArrayList<>(chunks);
        }

        // Sort descending by score to ensure highest-scoring chunks are preserved
        List<RetrievedChunk> sorted = new ArrayList<>(chunks);
        sorted.sort(Comparator.comparing(
                RetrievedChunk::score,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        List<RetrievedChunk> accepted = new ArrayList<>();
        List<Set<String>> acceptedWordSets = new ArrayList<>();
        List<String> acceptedNormalizedTexts = new ArrayList<>();

        for (RetrievedChunk candidate : sorted) {
            String candText = candidate.text() != null ? candidate.text() : "";
            String candNorm = normalize(candText);
            Set<String> candWords = tokenize(candText);

            boolean isDuplicate = false;
            for (int i = 0; i < accepted.size(); i++) {
                String existingNorm = acceptedNormalizedTexts.get(i);
                if (!candNorm.isEmpty() && candNorm.equals(existingNorm)) {
                    isDuplicate = true;
                    break;
                }

                Set<String> existingWords = acceptedWordSets.get(i);
                double overlap = calculateOverlapRatio(candWords, existingWords);
                if (overlap >= overlapThreshold) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                accepted.add(candidate);
                acceptedWordSets.add(candWords);
                acceptedNormalizedTexts.add(candNorm);
            }
        }

        return accepted;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String[] tokens = cleaned.trim().split("\\s+");
        Set<String> set = new HashSet<>();
        for (String t : tokens) {
            if (!t.isBlank()) {
                set.add(t);
            }
        }
        return set;
    }

    private double calculateOverlapRatio(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0;
        }
        long intersection = set1.stream().filter(set2::contains).count();
        if (intersection == 0) {
            return 0.0;
        }
        double jaccard = (double) intersection / (set1.size() + set2.size() - intersection);
        double containment = (double) intersection / Math.min(set1.size(), set2.size());
        return Math.max(jaccard, containment);
    }
}
