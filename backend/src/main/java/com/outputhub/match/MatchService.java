package com.outputhub.match;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * REQ-outputhub-004·014: 하이브리드 매칭. 키워드 점수(파일명·문서 내용)와 파일명 벡터 유사도
 * (2-gram TF-IDF 코사인)를 합산해 후보 점수를 계산하고 근거(어느 키워드·위치)를 만든다.
 * 규칙·가중치·기준값은 outputhub-rules.yml 에서 읽는다.
 */
@Service
public class MatchService {

    public static final String EVIDENCE_FILENAME = "파일명 키워드";
    public static final String EVIDENCE_CONTENT = "문서 내용 키워드";
    public static final String EVIDENCE_SIMILARITY = "벡터 유사도";

    private final List<Rule> rules = new ArrayList<>();
    private int filenameScore = 3;
    private int contentScore = 2;
    private int vectorWeight = 4;
    private double vectorThreshold = 0.3;

    public MatchService() {
        loadRules();
    }

    @SuppressWarnings("unchecked")
    private void loadRules() {
        try (InputStream in = new ClassPathResource("outputhub-rules.yml").getInputStream()) {
            Yaml yaml = new Yaml();
            Object root = yaml.load(in);
            java.util.Map<String, Object> rootMap = (java.util.Map<String, Object>) root;
            for (Object entry : (List<Object>) rootMap.get("rules")) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) entry;
                rules.add(new Rule((String) map.get("name"), (List<String>) map.get("keywords")));
            }
            java.util.Map<String, Object> matching = (java.util.Map<String, Object>) rootMap.get("matching");
            if (matching != null) {
                filenameScore = ((Number) matching.getOrDefault("filename-score", 3)).intValue();
                contentScore = ((Number) matching.getOrDefault("content-score", 2)).intValue();
                vectorWeight = ((Number) matching.getOrDefault("vector-weight", 4)).intValue();
                vectorThreshold = ((Number) matching.getOrDefault("vector-threshold", 0.3)).doubleValue();
            }
        } catch (IOException e) {
            throw new IllegalStateException("매칭 규칙 파일을 읽을 수 없습니다", e);
        }
    }

    /** 대장 행 후보를 하이브리드 점수순으로 계산한다. 근거는 "위치 키워드" 형식이다. */
    public List<Candidate> rank(String fileName, Path contentFile, List<ItemRef> items) {
        String lowerName = stripExtension(fileName.toLowerCase(Locale.ROOT));
        String content = extractContent(contentFile);
        List<ItemText> corpus = buildCorpus(items);
        Map<String, Double> idf = inverseDocumentFrequency(corpus);
        Map<String, Double> queryVector = termVector(bigrams(normalize(lowerName)), idf);
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Candidate candidate = candidateFor(items.get(i), corpus.get(i), lowerName, content, idf, queryVector);
            if (candidate != null) {
                result.add(candidate);
            }
        }
        result.sort((a, b) -> b.score() - a.score());
        return result;
    }

    /** 한 대장 행의 키워드 점수와 벡터 유사도 점수를 합산해 후보를 만든다(0점이면 후보 아님). */
    private Candidate candidateFor(ItemRef item, ItemText itemText, String lowerName, String content,
                                   Map<String, Double> idf, Map<String, Double> queryVector) {
        List<String> evidences = new ArrayList<>();
        int score = keywordScore(itemText.keywords(), lowerName, content, evidences);
        double similarity = cosine(queryVector, termVector(itemText.grams(), idf));
        int vectorPoints = vectorPoints(similarity);
        if (vectorPoints > 0) {
            score += vectorPoints;
            evidences.add(EVIDENCE_SIMILARITY + " " + String.format(Locale.ROOT, "%.2f", similarity));
        }
        if (score <= 0) {
            return null;
        }
        return new Candidate(item.id(), item.name(), score, String.join(", ", evidences));
    }

    /** 규칙 키워드가 파일명·문서 내용에 그대로 있는 만큼 점수를 더하고 근거를 남긴다. */
    private int keywordScore(List<String> keywords, String lowerName, String content, List<String> evidences) {
        int score = 0;
        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            if (lowerName.contains(lowerKeyword)) {
                score += filenameScore;
                evidences.add(EVIDENCE_FILENAME + " \"" + keyword + "\"");
            }
            if (!content.isEmpty() && content.toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                score += contentScore;
                evidences.add(EVIDENCE_CONTENT + " \"" + keyword + "\"");
            }
        }
        return score;
    }

    /** 항목 이름·키워드로 이루어진 벡터 검색 문서집합을 만든다. */
    private List<ItemText> buildCorpus(List<ItemRef> items) {
        List<ItemText> corpus = new ArrayList<>(items.size());
        for (ItemRef item : items) {
            List<String> keywords = keywordsOf(item.name());
            List<String> grams = new ArrayList<>(bigrams(normalize(item.name().toLowerCase(Locale.ROOT))));
            for (String keyword : keywords) {
                grams.addAll(bigrams(normalize(keyword)));
            }
            corpus.add(new ItemText(grams, keywords));
        }
        return corpus;
    }

    /** 규칙 name 이 항목 이름과 정확히 같을 때만 그 키워드를 쓴다(REQ-outputhub-014). */
    private List<String> keywordsOf(String itemName) {
        List<String> keywords = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.matchesItem(itemName)) {
                keywords.addAll(rule.keywords());
            }
        }
        return keywords;
    }

    /** 문서 빈도 역수 가중치. 자주 겹치는 어절(예: "보고")의 영향을 줄인다. */
    private Map<String, Double> inverseDocumentFrequency(List<ItemText> corpus) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (ItemText text : corpus) {
            for (String gram : new HashSet<>(text.grams())) {
                documentFrequency.merge(gram, 1, Integer::sum);
            }
        }
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            idf.put(entry.getKey(), Math.log(1.0 + (double) corpus.size() / entry.getValue()));
        }
        return idf;
    }

    /** 2-gram 목록을 TF-IDF 가중 벡터로 바꾼다. 문서집합에 없는 gram 은 무시한다. */
    private Map<String, Double> termVector(List<String> grams, Map<String, Double> idf) {
        Map<String, Double> vector = new HashMap<>();
        for (String gram : grams) {
            double weight = idf.getOrDefault(gram, 0.0);
            if (weight > 0.0) {
                vector.merge(gram, weight, Double::sum);
            }
        }
        return vector;
    }

    /** 두 가중 벡터의 코사인 유사도(0~1). 빈 벡터는 0.0 이다. */
    private double cosine(Map<String, Double> query, Map<String, Double> document) {
        if (query.isEmpty() || document.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        for (Map.Entry<String, Double> entry : query.entrySet()) {
            dot += entry.getValue() * document.getOrDefault(entry.getKey(), 0.0);
        }
        if (dot == 0.0) {
            return 0.0;
        }
        return dot / (norm(query) * norm(document));
    }

    private double norm(Map<String, Double> vector) {
        double sum = 0.0;
        for (double weight : vector.values()) {
            sum += weight * weight;
        }
        return Math.sqrt(sum);
    }

    /** 최소 기준 이상일 때만 점수로 환산한다(코사인 1.0 = vectorWeight 점). */
    private int vectorPoints(double similarity) {
        if (similarity < vectorThreshold) {
            return 0;
        }
        return (int) Math.round(similarity * vectorWeight);
    }

    /** 글자·숫자만 남겨 공백·밑줄·기호 차이를 무시한다. */
    private String normalize(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private List<String> bigrams(String value) {
        List<String> grams = new ArrayList<>();
        for (int i = 0; i < value.length() - 1; i++) {
            grams.add(value.substring(i, i + 2));
        }
        return grams;
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 시트 헤더·텍스트 파일 첫 줄에서 키워드 판단용 텍스트를 뽑는다(pdf·hwp는 파일명만 사용). */
    String extractContent(Path file) {
        if (file == null) {
            return "";
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xlsm")) {
                return readFirstSheetHeaders(file);
            }
            if (name.endsWith(".txt") || name.endsWith(".csv")) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                return String.join(" ", lines.subList(0, Math.min(5, lines.size())));
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    private String readFirstSheetHeaders(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                var cell = header.getCell(i);
                if (cell != null) {
                    sb.append(cell.toString()).append(' ');
                }
            }
            return sb.toString();
        }
    }

    public record Rule(String name, List<String> keywords) {
        boolean matchesItem(String itemName) {
            return name.equals(itemName);
        }
    }

    public record ItemRef(Long id, String name) {
    }

    public record Candidate(Long itemId, String itemName, int score, String evidence) {
    }

    /** 벡터 검색 문서 한 개: 항목 이름·키워드의 2-gram 목록과 규칙 키워드 목록. */
    private record ItemText(List<String> grams, List<String> keywords) {
    }
}
