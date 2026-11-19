package com.outputhub.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** REQ-outputhub-022: 수락·검토완료 시점에 원본 파일명을 규칙에 맞게 바꾸는 명명 규칙. */
public final class VersionNaming {

    /** 수락 시점 파일명 날짜 형식(내보내기 명명 규칙 REQ-outputhub-018과 같은 YYYYMMDD). */
    private static final DateTimeFormatter ACCEPTED_ON_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 저장 불가 문자(StorageService 원본 저장과 같은 규칙). */
    private static final String ILLEGAL_FILE_CHARACTERS = "[\\\\/:*?\"<>|]";

    private VersionNaming() {
    }

    /**
     * 버전 번호(백분위 정수)를 표기 문자열로 바꾼다. 10→"0.1", 11→"0.11", 100→"1.0", 101→"1.01", 110→"1.1".
     * 수락 버전은 v0.1부터 0.01씩 올라가고(REQ-outputhub-022), 검토완료 표기는 v1.0이다.
     */
    public static String format(int versionNo) {
        int whole = versionNo / 100;
        int hundredths = versionNo % 100;
        if (hundredths % 10 == 0) {
            return whole + "." + (hundredths / 10);
        }
        return whole + "." + String.format("%02d", hundredths);
    }

    /** 수락 시점 파일명: {산출물 항목}_v{버전}_{YYYYMMDD}.{확장자} */
    public static String versionedFileName(String itemName, int versionNo, String originalFileName,
                                           LocalDate acceptedOn) {
        String composed = sanitize(itemName) + "_v" + format(versionNo) + "_"
                + acceptedOn.format(ACCEPTED_ON_FORMAT) + extensionOf(originalFileName);
        return sanitize(composed);
    }

    /** 검토완료 시점 파일명: {산출물 항목}_v1.0.{확장자} */
    public static String confirmedFileName(String itemName, String originalFileName) {
        return sanitize(sanitize(itemName) + "_v1.0" + extensionOf(originalFileName));
    }

    /** 확장자는 원본 파일명의 마지막 마침표 뒷부분을 그대로 쓴다(없으면 빈 문자열). */
    public static String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }
        int dot = originalFileName.lastIndexOf('.');
        return dot < 0 ? "" : originalFileName.substring(dot);
    }

    private static String sanitize(String name) {
        String cleaned = name == null ? "unnamed" : name;
        return cleaned.replaceAll(ILLEGAL_FILE_CHARACTERS, "_");
    }
}
