package cloud.anzaanza.antiagingdna.exception;

import java.time.LocalDate;

/** 해당 날짜의 일지가 없음 — 404 Not Found */
public class DiaryNotFoundException extends RuntimeException {

    public DiaryNotFoundException(LocalDate date) {
        super("해당 날짜의 일지가 없습니다: " + date);
    }
}
