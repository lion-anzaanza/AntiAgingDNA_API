package cloud.anzaanza.antiagingdna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "diary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Diary {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "sleep_started_time")
    private LocalTime sleepStartedTime;

    @Column(name = "sleep_ended_time")
    private LocalTime sleepEndedTime;

    @Column(name = "sleep_waiting_time")
    private Integer sleepWaitingTime;

    @Column(name = "sleep_satisfaction")
    private Integer sleepSatisfaction;
}
